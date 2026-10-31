package com.idanck.nexplorer.data.peer

import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * חיבור אחד בין שני מכשירים. בהתחלה שורות JSON גלויות (פתיחה וחיבור), ואחרי [secure] — מסגרות מוצפנות:
 * אורך (4 בתים) ואחריו AES-256-GCM, עם nonce שהוא מונה של 12 בתים לכל כיוון (מפתח נפרד לכל כיוון, ולכן מונה
 * שמתחיל מאפס לא חוזר על עצמו). מסגרת שלא נפתחת (מפתח שגוי, שינוי בדרך) — [IOException], והחיבור נסגר.
 */
class PeerChannel(private val socket: Socket) : Closeable {

    private val input = DataInputStream(BufferedInputStream(socket.getInputStream(), BUFFER))
    private val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), BUFFER))

    private var sendKey: SecretKeySpec? = null
    private var receiveKey: SecretKeySpec? = null
    private var sendCounter = 0L
    private var receiveCounter = 0L

    val remoteAddress: String? get() = socket.inetAddress?.hostAddress

    fun timeout(millis: Int) {
        socket.soTimeout = millis
    }

    // --- השלב הגלוי ---

    fun writeLine(json: JSONObject) {
        output.write((json.toString() + "\n").toByteArray(Charsets.UTF_8))
        output.flush()
    }

    fun readLine(): JSONObject {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("החיבור נסגר")
            if (b == '\n'.code) break
            if (bytes.size() >= MAX_LINE) throw IOException("הודעה ארוכה מדי")
            bytes.write(b)
        }
        return try {
            JSONObject(bytes.toString(Charsets.UTF_8.name()))
        } catch (e: JSONException) {
            throw IOException("הודעה לא תקינה מהמכשיר השני", e)
        }
    }

    // --- השלב המוצפן ---

    fun secure(send: ByteArray, receive: ByteArray) {
        sendKey = SecretKeySpec(send, "AES")
        receiveKey = SecretKeySpec(receive, "AES")
    }

    fun send(plain: ByteArray, offset: Int = 0, length: Int = plain.size) {
        require(length <= MAX_FRAME) { "frame too large" }
        val key = sendKey ?: throw IllegalStateException("not secured")
        val cipher = Cipher.getInstance(AES).apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(sendCounter++))) }
        val sealed = cipher.doFinal(plain, offset, length)
        output.writeInt(sealed.size)
        output.write(sealed)
    }

    fun flush() = output.flush()

    fun receive(): ByteArray {
        val key = receiveKey ?: throw IllegalStateException("not secured")
        val size = input.readInt()
        if (size < TAG_BITS / 8 || size > MAX_FRAME + TAG_BITS / 8) throw IOException("מסגרת לא תקינה מהמכשיר השני")
        val sealed = ByteArray(size)
        input.readFully(sealed)
        return try {
            Cipher.getInstance(AES).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(receiveCounter++))) }.doFinal(sealed)
        } catch (e: Exception) {
            throw IOException("המכשיר השני לא מוכר — חבר אותו מחדש", e)
        }
    }

    fun sendJson(json: JSONObject) {
        send(json.toString().toByteArray(Charsets.UTF_8))
        flush()
    }

    fun receiveJson(): JSONObject = try {
        JSONObject(String(receive(), Charsets.UTF_8))
    } catch (e: JSONException) {
        throw IOException("הודעה לא תקינה מהמכשיר השני", e)
    }

    override fun close() {
        runCatching { socket.close() }
    }

    private fun nonce(counter: Long): ByteArray = ByteBuffer.allocate(12).putInt(0).putLong(counter).array()

    companion object {
        const val MAX_FRAME = 64 * 1024
        private const val MAX_LINE = 16 * 1024
        private const val BUFFER = 128 * 1024
        private const val TAG_BITS = 128
        private const val AES = "AES/GCM/NoPadding"

        fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

        fun unb64(text: String?): ByteArray = try {
            Base64.getDecoder().decode(text ?: throw IOException("הודעה לא תקינה מהמכשיר השני"))
        } catch (e: IllegalArgumentException) {
            throw IOException("הודעה לא תקינה מהמכשיר השני", e)
        }
    }
}
