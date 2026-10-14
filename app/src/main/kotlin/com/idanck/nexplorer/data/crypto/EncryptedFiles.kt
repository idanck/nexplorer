package com.idanck.nexplorer.data.crypto

import com.idanck.nexplorer.core.FileNames
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * "הצפן" / "פענח": פורמט ‎.enc‎ של nexplorer. המפרט ב-docs/plan/04-phase4.md ("פורמט ההצפנה"),
 * ו-tools/nxdecrypt.py פותח אותו גם בלי האפליקציה. נבדק ב-JVM.
 *
 * AES-256-GCM בחלקים של 64KB: כל חלק מאומת לבד, כך שקובץ גדול לא נטען לזיכרון וחלק פגום לא נכתב לדיסק.
 * המפתח נגזר מהסיסמה ב-PBKDF2-HMAC-SHA256, ולכל קובץ מפתח משלו (HKDF עם מלח של הקובץ) — ולכן ה-nonce
 * יכול להיות מונה. החלק האחרון מסומן ב-nonce, וכל הכותרת היא AAD: קיצוץ או שינוי נתפסים בפענוח.
 */
object EncryptedFiles {

    const val EXTENSION = "enc"

    /** ההמלצה של OWASP ל-PBKDF2-HMAC-SHA256. נשמר בכותרת — אפשר להעלות בעתיד בלי לשבור קבצים קיימים. */
    const val ITERATIONS = 600_000

    private const val VERSION: Byte = 1
    private val MAGIC = byteArrayOf(0x4E, 0x58, 0x45, 0x4E, 0x43, VERSION) // "NXENC" + גרסה
    internal const val SALT = 16
    private const val TAG = 16
    private const val CHUNK_LOG2 = 16
    private const val CHUNK = 1 shl CHUNK_LOG2

    /** magic (6) · חזרות PBKDF2 (4, BE) · log2 של גודל החלק (1) · מלח הסיסמה (16) · מלח הקובץ (16). */
    const val HEADER_SIZE = 6 + 4 + 1 + SALT + SALT

    /** קובץ עם ערכים מחוץ לטווח לא יתקע את האפליקציה בגזירה של שעה ולא ידרוש חלק של ג'יגה. */
    private val ITERATIONS_RANGE = 10_000..10_000_000
    private val CHUNK_LOG2_RANGE = 10..20

    private const val HMAC = "HmacSHA256"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private val INFO = "nexplorer/enc/v1".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()

    /** החלק הראשון לא נפתח: סיסמה שגויה (או, בלי דרך לדעת, חלק ראשון פגום). */
    class WrongPassword : IOException("סיסמה שגויה")

    /** הקובץ לא שלם, שונה, או שהוא לא קובץ מוצפן בכלל. */
    class Damaged(message: String) : IOException(message)

    fun hasEncryptedName(name: String): Boolean =
        name.length > EXTENSION.length + 1 && name.endsWith(".$EXTENSION", ignoreCase = true)

    /** "photo.jpg.enc" → "photo.jpg". קובץ שהשם שלו שונה ואין לו את הסיומת — נשאר כמו שהוא. */
    fun plainName(name: String): String = if (hasEncryptedName(name)) name.dropLast(EXTENSION.length + 1) else name

    /**
     * "photo.jpg" → "photo.jpg.enc", ואם תפוס — "photo (1).jpg.enc" (ולא "photo.jpg (1).enc"),
     * כדי שהפענוח יחזיר "photo (1).jpg" עם הסיומת המקורית.
     */
    fun encryptedNameIn(dir: File, plain: String): String =
        FileNames.unique(plain) { File(dir, "$it.$EXTENSION").exists() } + ".$EXTENSION"

    /** לפי התוכן, לא לפי השם. */
    fun isEncrypted(file: File): Boolean = try {
        file.inputStream().use { input ->
            val head = ByteArray(MAGIC.size)
            readFully(input, head, 0) == MAGIC.size && head.contentEquals(MAGIC)
        }
    } catch (e: IOException) {
        false
    }

    /**
     * מצפין את [source] לקובץ חדש ב-[dir] ומחזיר אותו. נכתב לשם זמני, נקרא שוב ומאומת, ורק אז מקבל את
     * השם הסופי — כך שכשהמקור נמחק אחר כך, הגרסה המוצפנת בוודאות שלמה. המקור לא נמחק כאן.
     * [onBytes] סופר כל בית פעמיים: בכתיבה ובאימות.
     */
    suspend fun encryptFile(
        source: File,
        dir: File,
        keys: KeyRing,
        checkpoint: suspend () -> Unit,
        onBytes: (Long) -> Unit,
    ): File {
        val part = File(dir, ".${encryptedNameIn(dir, source.name)}.nexplorer-part")
        try {
            source.inputStream().use { input ->
                FileOutputStream(part).use { output ->
                    encrypt(input, output, keys, checkpoint, onBytes)
                    output.flush()
                    output.fd.sync()
                }
            }
            try {
                part.inputStream().use { decrypt(it, null, keys, checkpoint, onBytes) }
            } catch (e: IOException) {
                throw IOException("ההצפנה של \"${source.name}\" לא נקראה בחזרה כמו שנכתבה — המקור לא נמחק", e)
            }
            // שם שנתפס בינתיים מקבל שם פנוי — rename דורס בשקט.
            val final = File(dir, encryptedNameIn(dir, source.name))
            if (!part.renameTo(final)) throw IOException("לא ניתן לשמור את \"${final.name}\"")
            final.setLastModified(source.lastModified())
            return final
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
    }

    /**
     * מפענח את [source] לקובץ חדש ב-[dir] ("photo.jpg.enc" → "photo.jpg", או שם פנוי) ומחזיר אותו.
     * כל חלק מאומת לפני שהוא נכתב; בכשל לא נשאר כלום. הקובץ המוצפן לא נמחק כאן.
     */
    suspend fun decryptFile(
        source: File,
        dir: File,
        keys: KeyRing,
        checkpoint: suspend () -> Unit,
        onBytes: (Long) -> Unit,
    ): File {
        val name = plainName(source.name)
        val part = File(dir, ".${FileNames.unique(name) { File(dir, it).exists() }}.nexplorer-part")
        try {
            source.inputStream().use { input ->
                FileOutputStream(part).use { output ->
                    decrypt(input, output, keys, checkpoint, onBytes)
                    output.flush()
                    output.fd.sync()
                }
            }
            val final = File(dir, FileNames.unique(name) { File(dir, it).exists() })
            if (!part.renameTo(final)) throw IOException("לא ניתן לשמור את \"${final.name}\"")
            final.setLastModified(source.lastModified())
            return final
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
    }

    suspend fun encrypt(
        input: InputStream,
        output: OutputStream,
        keys: KeyRing,
        checkpoint: suspend () -> Unit = {},
        onBytes: (Long) -> Unit = {},
    ) {
        val sealing = keys.sealing()
        val fileSalt = randomBytes(SALT)
        val header = ByteBuffer.allocate(HEADER_SIZE)
            .put(MAGIC)
            .putInt(sealing.iterations)
            .put(CHUNK_LOG2.toByte())
            .put(sealing.salt)
            .put(fileSalt)
            .array()
        output.write(header)
        val key = fileKey(sealing.key, fileSalt)
        val cipher = Cipher.getInstance(AES_GCM)
        val chunk = ByteArray(CHUNK)
        val sealed = ByteArray(CHUNK + TAG)
        var index = 0L
        var filled = readFully(input, chunk, 0)
        while (true) {
            checkpoint()
            // חלק מלא ואחריו סוף הקובץ הוא עדיין האחרון — מציצים בית אחד קדימה.
            val next = if (filled == CHUNK) input.read() else -1
            val last = next < 0
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG * 8, nonce(index, last)))
            cipher.updateAAD(header)
            val written = cipher.doFinal(chunk, 0, filled, sealed, 0)
            output.write(sealed, 0, written)
            onBytes(filled.toLong())
            if (last) return
            index++
            chunk[0] = next.toByte()
            filled = 1 + readFully(input, chunk, 1)
        }
    }

    /** [output] null — רק אימות (אחרי הצפנה). [onBytes] סופר בתים של התוכן המקורי, כמו בהצפנה. */
    suspend fun decrypt(
        input: InputStream,
        output: OutputStream?,
        keys: KeyRing,
        checkpoint: suspend () -> Unit = {},
        onBytes: (Long) -> Unit = {},
    ) {
        val header = ByteArray(HEADER_SIZE)
        if (readFully(input, header, 0) < HEADER_SIZE || !header.copyOf(MAGIC.size).contentEquals(MAGIC)) {
            throw Damaged("זה לא קובץ שהוצפן ב-nexplorer")
        }
        val fields = ByteBuffer.wrap(header, MAGIC.size, HEADER_SIZE - MAGIC.size)
        val iterations = fields.int
        val chunkLog2 = fields.get().toInt()
        if (iterations !in ITERATIONS_RANGE || chunkLog2 !in CHUNK_LOG2_RANGE) throw Damaged("הכותרת של הקובץ פגומה")
        val passwordSalt = ByteArray(SALT).also { fields.get(it) }
        val fileSalt = ByteArray(SALT).also { fields.get(it) }
        val key = fileKey(keys.keyFor(passwordSalt, iterations), fileSalt)
        val cipher = Cipher.getInstance(AES_GCM)
        val sealedSize = (1 shl chunkLog2) + TAG
        val sealed = ByteArray(sealedSize)
        val plain = ByteArray(sealedSize)
        var index = 0L
        var filled = readFully(input, sealed, 0)
        while (true) {
            checkpoint()
            val next = if (filled == sealedSize) input.read() else -1
            val last = next < 0
            if (filled < TAG) throw Damaged("הקובץ קטוע")
            val opened = open(cipher, key, header, sealed, filled, plain, index, last)
                ?: throw when {
                    index > 0 -> Damaged("הקובץ פגום — חלק ${index + 1} לא מאומת")
                    // החלק הראשון נפתח כשהוא לא האחרון: הסיסמה נכונה, וחסר סוף הקובץ.
                    last && open(cipher, key, header, sealed, filled, plain, 0, last = false) != null -> Damaged("הקובץ קטוע")
                    else -> WrongPassword()
                }
            output?.write(plain, 0, opened)
            onBytes(opened.toLong())
            if (last) return
            index++
            sealed[0] = next.toByte()
            filled = 1 + readFully(input, sealed, 1)
        }
    }

    /** null — האימות נכשל. */
    private fun open(
        cipher: Cipher,
        key: SecretKeySpec,
        header: ByteArray,
        sealed: ByteArray,
        length: Int,
        plain: ByteArray,
        index: Long,
        last: Boolean,
    ): Int? = try {
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG * 8, nonce(index, last)))
        cipher.updateAAD(header)
        cipher.doFinal(sealed, 0, length, plain, 0)
    } catch (e: BadPaddingException) {
        // AEADBadTagException יורש ממנו.
        null
    }

    /** 11 בתים של מספר החלק (BE) ובית אחרון: 1 בחלק האחרון, אחרת 0. */
    private fun nonce(index: Long, last: Boolean): ByteArray =
        ByteBuffer.allocate(12).putLong(3, index).put(11, (if (last) 1 else 0).toByte()).array()

    /** HKDF-SHA256 (RFC 5869): מפתח לקובץ אחד מהמפתח של הסיסמה ומהמלח של הקובץ. */
    private fun fileKey(master: ByteArray, salt: ByteArray): SecretKeySpec {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(salt, HMAC))
        val prk = mac.doFinal(master)
        mac.init(SecretKeySpec(prk, HMAC))
        mac.update(INFO)
        mac.update(1)
        val okm = mac.doFinal()
        prk.fill(0)
        // SecretKeySpec שומר עותק משלו.
        return SecretKeySpec(okm, "AES").also { okm.fill(0) }
    }

    /**
     * PBKDF2-HMAC-SHA256 לבלוק אחד (32 בתים). מימוש משלנו ולא SecretKeyFactory: הקידוד של הסיסמה
     * (UTF-8 אחרי NFC) צריך להיות זהה בכל מכשיר ובכלי הפענוח — ספקים ישנים באנדרואיד קודדו תווים ל-8 ביט.
     */
    internal fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(password, HMAC))
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        val u = mac.doFinal()
        val result = u.copyOf()
        repeat(iterations - 1) {
            mac.update(u)
            mac.doFinal(u, 0)
            for (i in result.indices) result[i] = (result[i].toInt() xor u[i].toInt()).toByte()
        }
        u.fill(0)
        return result
    }

    /** NFC: אותה סיסמה בעברית מכל מקלדת נותנת אותם בתים. */
    internal fun passwordBytes(password: CharArray): ByteArray =
        Normalizer.normalize(String(password), Normalizer.Form.NFC).toByteArray(Charsets.UTF_8)

    internal fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    /** קורא עד ש-[buffer] מלא מ-[offset] או שהזרם נגמר; מחזיר כמה בתים נקראו. */
    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int): Int {
        var total = 0
        while (offset + total < buffer.size) {
            val read = input.read(buffer, offset + total, buffer.size - offset - total)
            if (read < 0) break
            total += read
        }
        return total
    }
}

/**
 * הסיסמה של פעולה אחת והמפתחות שנגזרו ממנה. PBKDF2 הוא החלק האיטי (שנייה בערך בטלפון), ולכן כל הקבצים
 * שמוצפנים באותה פעולה חולקים מלח אחד, ובפענוח כל מלח נגזר פעם אחת. [close] מוחק הכל מהזיכרון.
 * לא בטוח לשימוש מכמה תהליכונים במקביל — כל פעולה מחזיקה אחד משלה.
 */
class KeyRing(password: CharArray, private val iterations: Int = EncryptedFiles.ITERATIONS) : AutoCloseable {

    init {
        require(password.isNotEmpty()) { "צריך סיסמה" }
    }

    private val secret = EncryptedFiles.passwordBytes(password)
    private val derived = HashMap<String, ByteArray>()
    private var sealingSalt: ByteArray? = null

    internal class Sealing(val salt: ByteArray, val iterations: Int, val key: ByteArray)

    /** המפתח להצפנה. נגזר בקריאה הראשונה — אפשר לקרוא מראש, בזמן "מכין". */
    internal fun sealing(): Sealing {
        val salt = sealingSalt ?: EncryptedFiles.randomBytes(EncryptedFiles.SALT).also { sealingSalt = it }
        return Sealing(salt, iterations, keyFor(salt, iterations))
    }

    internal fun keyFor(salt: ByteArray, iterations: Int): ByteArray =
        derived.getOrPut(Base64.getEncoder().encodeToString(salt) + ":" + iterations) {
            EncryptedFiles.pbkdf2(secret, salt, iterations)
        }

    /** גוזר מראש את מפתח ההצפנה (החלק האיטי), כדי שההתקדמות תתחיל רק כשבאמת כותבים. */
    fun prepareSealing() {
        sealing()
    }

    override fun close() {
        secret.fill(0)
        derived.values.forEach { it.fill(0) }
        derived.clear()
    }
}
