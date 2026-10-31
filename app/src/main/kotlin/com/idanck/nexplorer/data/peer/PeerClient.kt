package com.idanck.nexplorer.data.peer

import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException

/** מה שנשמר אחרי חיבור מוצלח: המכשיר השני ומפתח החיבור. */
class PairedPeer(val id: String, val name: String, val key: ByteArray)

/**
 * הצד שמקליד את הקוד (בדרך כלל הטלפון). [start] — מתחבר, מתחייב למפתח ומקבל את המפתח של השרת; השרת מציג עכשיו קוד.
 * [confirm] — הקוד שהמשתמש הקליד: שונה מהקוד שחושב כאן (טעות הקלדה, או מישהו באמצע) — [WrongCode], ואפשר לנסות
 * שוב; נכון — אישור הדדי, ו-[PairedPeer] לשמירה.
 */
class PairingClient(private val address: InetSocketAddress, private val me: PeerIdentity) : Closeable {

    class WrongCode : IOException("הקוד לא תואם — בדוק את הקוד שבמסך של המכשיר השני")

    private var channel: PeerChannel? = null
    private var expected: String? = null
    private var key: ByteArray? = null
    var serverId: String = ""
        private set
    var serverName: String = ""
        private set

    fun start() {
        val channel = PeerChannel(PeerConnect.open(address)).also { this.channel = it }
        channel.timeout(HELLO_TIMEOUT)
        val keys = PeerCrypto.keyPair()
        val clientPublic = PeerCrypto.encode(keys.public)
        val clientNonce = PeerCrypto.random(PeerCrypto.NONCE_BYTES)
        channel.writeLine(
            JSONObject()
                .put("v", PeerServer.VERSION)
                .put("type", "pair")
                .put("id", me.id)
                .put("name", me.name)
                .put("commit", PeerChannel.b64(PeerCrypto.commit(clientPublic, clientNonce))),
        )
        val reply = channel.readLine()
        reply.optString("error").takeIf { it.isNotEmpty() }?.let { throw IOException(it) }
        serverId = reply.optString("id").takeIf { it.isNotBlank() } ?: throw IOException("תשובה לא תקינה מהמכשיר השני")
        serverName = reply.optString("name").ifBlank { "מכשיר" }
        val serverPublic = PeerChannel.unb64(reply.optString("pub"))
        val serverNonce = PeerChannel.unb64(reply.optString("nonce"))
        val shared = PeerCrypto.agree(keys.private, PeerCrypto.decodePublic(serverPublic))
        key = PeerCrypto.pairKey(shared, clientNonce, serverNonce)
        expected = PeerCrypto.code(clientPublic, serverPublic, clientNonce, serverNonce)
        channel.writeLine(JSONObject().put("pub", PeerChannel.b64(clientPublic)).put("nonce", PeerChannel.b64(clientNonce)))
        channel.timeout(PeerServer.PAIR_TIMEOUT)
    }

    fun confirm(typed: String): PairedPeer {
        val channel = channel ?: throw IOException("החיבור לא התחיל")
        val expected = expected ?: throw IOException("החיבור לא התחיל")
        val key = key ?: throw IOException("החיבור לא התחיל")
        if (!PeerCrypto.sameCode(typed.filter(Char::isDigit), expected)) throw WrongCode()
        channel.writeLine(JSONObject().put("confirm", PeerChannel.b64(PeerCrypto.clientConfirm(key))))
        val answer = channel.readLine()
        if (!answer.optBoolean("ok") || !PeerCrypto.same(PeerChannel.unb64(answer.optString("confirm")), PeerCrypto.serverConfirm(key))) {
            throw IOException("המכשיר השני לא אישר את החיבור")
        }
        close()
        return PairedPeer(serverId, serverName, key)
    }

    override fun close() {
        channel?.close()
        channel = null
    }

    private companion object {
        const val HELLO_TIMEOUT = 15_000
    }
}

/** פתיחת חיבור, עם הודעה בעברית כשהמכשיר לא עונה. */
object PeerConnect {
    const val NOT_VISIBLE = "המכשיר לא נראה ברשת — nexplorer צריך להיות פתוח בו"

    fun open(address: InetSocketAddress, timeout: Int = 5_000): Socket {
        val socket = Socket()
        try {
            socket.connect(address, timeout)
            socket.tcpNoDelay = true
            return socket
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw when (e) {
                is ConnectException, is NoRouteToHostException, is SocketTimeoutException -> IOException(NOT_VISIBLE, e)
                else -> e
            }
        }
    }
}

/**
 * שיחה מוצפנת עם מכשיר מחובר: פתיחה גלויה (מזהה + nonce), ומכאן מסגרות במפתחות השיחה. מפתח שגוי (החיבור נמחק בצד
 * השני) — המסגרת הראשונה לא נפתחת, ושני הצדדים סוגרים.
 */
class PeerSession private constructor(val channel: PeerChannel) : Closeable {

    /** בקשה ותשובה. `{"error": …}` — [IOException] עם ההודעה, והשיחה ממשיכה. */
    fun call(request: JSONObject): JSONObject {
        channel.sendJson(request)
        return channel.receiveJson().also { reply ->
            reply.optString("error").takeIf { it.isNotEmpty() }?.let { throw PeerError(it) }
        }
    }

    fun timeout(millis: Int) = channel.timeout(millis)

    override fun close() = channel.close()

    /** שגיאה של פעולה (לא של החיבור) — השיחה עדיין תקינה. */
    class PeerError(message: String) : IOException(message)

    companion object {
        fun open(address: InetSocketAddress, me: PeerIdentity, key: ByteArray): PeerSession {
            val channel = PeerChannel(PeerConnect.open(address))
            try {
                channel.timeout(30_000)
                val clientNonce = PeerCrypto.random(PeerCrypto.NONCE_BYTES)
                channel.writeLine(JSONObject().put("v", PeerServer.VERSION).put("type", "session").put("id", me.id).put("nonce", PeerChannel.b64(clientNonce)))
                val reply = channel.readLine()
                when (val error = reply.optString("error")) {
                    "" -> Unit
                    PeerServer.UNPAIRED -> throw IOException("המכשיר השני כבר לא מחובר לכאן — חבר אותו מחדש")
                    else -> throw IOException(error)
                }
                val serverNonce = PeerChannel.unb64(reply.optString("nonce"))
                if (serverNonce.size != PeerCrypto.NONCE_BYTES) throw IOException("תשובה לא תקינה מהמכשיר השני")
                val (c2s, s2c) = PeerCrypto.sessionKeys(key, clientNonce, serverNonce)
                channel.secure(send = c2s, receive = s2c)
                return PeerSession(channel)
            } catch (e: Exception) {
                channel.close()
                throw e
            }
        }
    }
}
