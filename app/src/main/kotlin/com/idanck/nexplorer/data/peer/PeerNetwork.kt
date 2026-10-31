package com.idanck.nexplorer.data.peer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.idanck.nexplorer.core.NetProtocol
import com.idanck.nexplorer.data.net.ConnectionSecrets
import com.idanck.nexplorer.data.net.NetConnection
import com.idanck.nexplorer.data.net.RemoteFiles
import com.idanck.nexplorer.data.net.RemoteFs
import com.idanck.nexplorer.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** מכשיר עם nexplorer פתוח באותה רשת (NSD). */
data class NearbyDevice(val id: String, val name: String, val host: String, val port: Int, val serviceName: String)

/** הצעת קבצים שמחכה לתשובה במסך (T5). [answer] — פעם אחת. */
class IncomingOffer(val peerId: String, val peerName: String, val files: List<OfferedFile>, val total: Long, private val reply: CompletableFuture<Boolean>) {
    fun answer(accept: Boolean) {
        reply.complete(accept)
    }
}

/**
 * "העברה בין מכשירים" באפליקציה (1.16): השרת ([PeerServer]) והפרסום ב-NSD — רק כשהאפליקציה פתוחה ([start] מ-onStart,
 * [stop] מ-onStop; ההחלטה של עידן), והגילוי של מכשירים אחרים. מכשיר מחובר נשמר כ-[NetConnection] מסוג
 * [NetProtocol.Peer]: [NetConnection.host] — המזהה שלו, והסוד — מפתח החיבור (Base64) בצופן של ה-Keystore.
 */
class PeerNetwork(
    context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) : PeerHost {

    private val app = context.applicationContext
    private val nsd = app.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    lateinit var remote: RemoteFiles

    private val _nearby = MutableStateFlow<List<NearbyDevice>>(emptyList())
    /** מכשירים שנראים עכשיו (בלי המכשיר הזה). */
    val nearby: StateFlow<List<NearbyDevice>> = _nearby

    private val _pairing = MutableStateFlow<PairingRequest?>(null)
    /** חיבור שמחכה לקוד — הקוד מוצג (T6). */
    val pairing: StateFlow<PairingRequest?> = _pairing

    private val _offer = MutableStateFlow<IncomingOffer?>(null)
    val offer: StateFlow<IncomingOffer?> = _offer

    private val _paired = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** חיבור שהצליח בצד שמציג את הקוד — השם של המכשיר השני ("חובר ל-…"). */
    val pairedEvents: SharedFlow<String> = _paired

    /** המכשירים המחוברים (מההעדפות). */
    val devices: StateFlow<List<NetConnection>> = settings.state
        .map { s -> s.connections.filter { it.protocol == NetProtocol.Peer } }
        .stateIn(scope, SharingStarted.Eagerly, settings.state.value.connections.filter { it.protocol == NetProtocol.Peer })

    private var server: PeerServer? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var started = false

    @Volatile private var generatedId: String? = null

    /** שם המכשיר כפי שהאחרים רואים — מההגדרות, או שם המכשיר במערכת. */
    val defaultName: String
        get() = runCatching { Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()?.takeIf { it.isNotBlank() } ?: Build.MODEL

    override val identity: PeerIdentity
        get() {
            val s = settings.state.value
            val id = s.peerId.ifBlank {
                generatedId ?: UUID.randomUUID().toString().also { fresh ->
                    generatedId = fresh
                    scope.launch { settings.update { if (it.peerId.isBlank()) it.copy(peerId = fresh) else it } }
                }
            }
            return PeerIdentity(id, s.peerName.ifBlank { defaultName })
        }

    override val root: File get() = Environment.getExternalStorageDirectory()

    private fun paired(peerId: String): NetConnection? =
        settings.state.value.connections.firstOrNull { it.protocol == NetProtocol.Peer && it.host == peerId }

    override fun keyFor(peerId: String): ByteArray? {
        val secret = paired(peerId)?.secret ?: return null
        return runCatching { decodeKey(ConnectionSecrets.decrypt(secret)) }.getOrNull()
    }

    override fun autoAccept(peerId: String): Boolean = paired(peerId)?.autoAccept == true

    override fun onPairing(request: PairingRequest) {
        _pairing.value = request
    }

    override fun onPairingEnded(request: PairingRequest) {
        _pairing.update { if (it === request) null else it }
    }

    override fun onPaired(peerId: String, peerName: String, key: ByteArray) {
        runBlocking { save(peerId, peerName, key) }
        _paired.tryEmit(peerName)
    }

    override fun onOffer(peerId: String, files: List<OfferedFile>, total: Long, timeoutMillis: Long): Boolean {
        val name = paired(peerId)?.label ?: "מכשיר"
        val reply = CompletableFuture<Boolean>()
        val offer = IncomingOffer(peerId, name, files, total, reply)
        _offer.value = offer
        return try {
            reply.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            false
        } finally {
            _offer.update { if (it === offer) null else it }
        }
    }

    /** "קבל מהמכשיר הזה בלי לשאול". */
    suspend fun setAutoAccept(peerId: String, value: Boolean) {
        settings.update { s -> s.copy(connections = s.connections.map { if (it.protocol == NetProtocol.Peer && it.host == peerId) it.copy(autoAccept = value) else it }) }
    }

    private suspend fun save(peerId: String, peerName: String, key: ByteArray): NetConnection {
        val secret = ConnectionSecrets.encrypt(encodeKey(key))
        var saved: NetConnection? = null
        settings.update { s ->
            val existing = s.connections.firstOrNull { it.protocol == NetProtocol.Peer && it.host == peerId }
            val connection = (existing ?: NetConnection(NetConnection.newId(), NetProtocol.Peer, peerName, peerId, null, "", ""))
                .copy(name = peerName, secret = secret)
            saved = connection
            s.copy(connections = s.connections.filterNot { it.id == connection.id } + connection)
        }
        saved?.let { remote.forget(it.id) }
        return saved!!
    }

    /** "נתק": מוחק את החיבור כאן. הצד השני יגלה בשיחה הבאה ("חבר אותו מחדש"). */
    suspend fun unpair(connection: NetConnection) {
        remote.forget(connection.id)
        settings.update { s -> s.copy(connections = s.connections.filterNot { it.id == connection.id }) }
    }

    fun address(peerId: String): InetSocketAddress? =
        _nearby.value.firstOrNull { it.id == peerId }?.let { InetSocketAddress(it.host, it.port) }

    fun connectionFor(peerId: String): NetConnection? = paired(peerId)

    /** ל-[RemoteFiles]: מכשיר מחובר כמקום ברשת. */
    fun fs(connection: NetConnection, secret: CharArray): RemoteFs =
        PeerFs(connection, decodeKey(secret), { identity }, ::address)

    // --- חיבור מהצד שמקליד ---

    suspend fun beginPairing(device: NearbyDevice): PairingClient = withContext(Dispatchers.IO) {
        PairingClient(InetSocketAddress(device.host, device.port), identity).also {
            try {
                it.start()
            } catch (e: Exception) {
                it.close()
                throw e
            }
        }
    }

    /** הקוד שהמשתמש הקליד. [PairingClient.WrongCode] — אפשר לנסות שוב באותו חיבור. */
    suspend fun finishPairing(client: PairingClient, code: String): NetConnection = withContext(Dispatchers.IO) {
        val peer = client.confirm(code)
        save(peer.id, peer.name, peer.key)
    }

    /** "שלח למכשיר": התיקייה שאליה להעתיק, או null — דחה. */
    suspend fun offer(connection: NetConnection, files: List<OfferedFile>): String? = withContext(Dispatchers.IO) {
        val fs = remote.fsFor(connection.root) as? PeerFs ?: throw IOException("החיבור למכשיר הזה פגום — חבר אותו מחדש")
        fs.offer(files)
    }

    // --- מחזור החיים ---

    /** האפליקציה עברה לחזית. */
    fun start() {
        if (started) return
        started = true
        if (settings.state.value.peerVisible) publish()
        discover()
    }

    /** האפליקציה ברקע: לא גלויים, לא מחפשים. העברות שכבר באמצע ממשיכות בחיבורים שלהן. */
    fun stop() {
        if (!started) return
        started = false
        unpublish()
        discovery?.let { l -> runCatching { nsd?.stopServiceDiscovery(l) } }
        discovery = null
        pending.clear()
        resolving = false
        _nearby.value = emptyList()
    }

    /** "גלוי למכשירים ברשת" או שם חדש — מפרסם מחדש. */
    fun refresh() {
        if (!started) return
        unpublish()
        if (settings.state.value.peerVisible) publish()
    }

    private fun publish() {
        val nsd = nsd ?: return
        scope.launch(Dispatchers.IO) {
            val server = runCatching { PeerServer(this@PeerNetwork).start() }.getOrElse {
                Log.w("nexplorer", "peer server failed", it)
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (!started || this@PeerNetwork.server != null) {
                    server.close()
                    return@withContext
                }
                this@PeerNetwork.server = server
                val me = identity
                val info = NsdServiceInfo().apply {
                    serviceName = me.name.take(60)
                    serviceType = PeerServer.SERVICE_TYPE
                    port = server.port
                    setAttribute("id", me.id)
                    setAttribute("v", PeerServer.VERSION.toString())
                }
                val listener = object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(info: NsdServiceInfo) = Unit
                    override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
                    override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                }
                runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }.onSuccess { registration = listener }
            }
        }
    }

    private fun unpublish() {
        registration?.let { l -> runCatching { nsd?.unregisterService(l) } }
        registration = null
        server?.close()
        server = null
        _pairing.value?.cancel?.invoke()
    }

    private fun discover() {
        val nsd = nsd ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                main.post {
                    pending.addLast(info)
                    resolveNext()
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { _nearby.update { list -> list.filterNot { it.serviceName == info.serviceName } } }
            }
        }
        runCatching { nsd.discoverServices(PeerServer.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }.onSuccess { discovery = listener }
    }

    @Suppress("DEPRECATION") // registerServiceInfoCallback רק מ-Android 14; resolveService עדיין עובד.
    private fun resolveNext() {
        if (resolving || discovery == null) return
        val next = pending.removeFirstOrNull() ?: return
        resolving = true
        nsd?.resolveService(
            next,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    main.post {
                        resolving = false
                        resolveNext()
                    }
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        info.hostAddresses.firstOrNull { it is Inet4Address } ?: info.hostAddresses.firstOrNull()
                    } else {
                        info.host
                    }
                    val id = info.attributes["id"]?.let { String(it, Charsets.UTF_8) }
                    main.post {
                        resolving = false
                        val host = address?.hostAddress
                        if (host != null && id != null && id != identity.id && discovery != null) {
                            val device = NearbyDevice(id, info.serviceName, host, info.port, info.serviceName)
                            _nearby.update { list -> (list.filterNot { it.id == id || it.serviceName == device.serviceName } + device).sortedBy { it.name.lowercase() } }
                        }
                        resolveNext()
                    }
                }
            },
        ) ?: run { resolving = false }
    }

    private companion object {
        fun encodeKey(key: ByteArray): CharArray = Base64.encodeToString(key, Base64.NO_WRAP).toCharArray()

        fun decodeKey(secret: CharArray): ByteArray = try {
            Base64.decode(String(secret), Base64.NO_WRAP).also { secret.fill('\u0000') }
        } catch (e: IllegalArgumentException) {
            throw IOException("החיבור למכשיר הזה פגום — חבר אותו מחדש", e)
        }
    }
}
