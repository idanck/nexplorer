package com.idanck.nexplorer.data.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.idanck.nexplorer.core.NetProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address

/** שרת שפרסם את עצמו ברשת המקומית (mDNS / Bonjour) — "נמצאו ברשת" בלוח "חיבור חדש". */
data class FoundServer(val name: String, val host: String, val port: Int, val protocol: NetProtocol)

/**
 * גילוי שרתים ב-LAN דרך NsdManager: `_smb._tcp` (Synology, macOS ו-Samba עם Avahi מפרסמים אותו).
 * כל שירות שנמצא מתורגם לכתובת אחד אחרי השני — לפני Android 14 אפשר רק תרגום אחד בכל רגע.
 * שרת שלא מפרסם את עצמו (Windows בלי Bonjour) לא יופיע — הכתובת מוזנת ידנית.
 */
class NetDiscovery(context: Context) {

    private val nsd = context.applicationContext.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val _found = MutableStateFlow<List<FoundServer>>(emptyList())
    val found: StateFlow<List<FoundServer>> = _found

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (nsd == null || listener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                main.post { _searching.value = false }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                main.post {
                    pending.addLast(info)
                    resolveNext()
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { _found.update { list -> list.filterNot { it.name == info.serviceName } } }
            }
        }
        listener = l
        _searching.value = true
        runCatching { nsd.discoverServices(SMB_SERVICE, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure {
            listener = null
            _searching.value = false
        }
        // אין "סיום" לגילוי: אחרי כמה שניות בלי תשובה — "לא נמצאו".
        main.postDelayed({ _searching.value = false }, SEARCH_WINDOW_MS)
    }

    fun stop() {
        listener?.let { l -> runCatching { nsd?.stopServiceDiscovery(l) } }
        listener = null
        pending.clear()
        main.removeCallbacksAndMessages(null)
        _searching.value = false
    }

    @Suppress("DEPRECATION") // registerServiceInfoCallback רק מ-Android 14; resolveService עדיין עובד.
    private fun resolveNext() {
        if (resolving || listener == null) return
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
                    main.post {
                        resolving = false
                        val host = address?.hostAddress
                        if (host != null && listener != null) {
                            val server = FoundServer(info.serviceName, host, info.port, NetProtocol.Smb)
                            _found.update { list -> (list.filterNot { it.name == server.name } + server).sortedBy { it.name.lowercase() } }
                        }
                        resolveNext()
                    }
                }
            },
        ) ?: run { resolving = false }
    }

    private companion object {
        const val SMB_SERVICE = "_smb._tcp"
        const val SEARCH_WINDOW_MS = 6_000L
    }
}
