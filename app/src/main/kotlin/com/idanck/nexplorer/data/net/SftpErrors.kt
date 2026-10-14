package com.idanck.nexplorer.data.net

import com.hierynomus.sshj.common.KeyDecryptionFailedException
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.common.SSHException
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.userauth.UserAuthException
import java.io.IOException

/** שגיאה של sshj (או של הרשת מתחתיו) → IOException עם הסבר בעברית. */
internal object SftpErrors {

    fun map(e: Throwable, connection: NetConnection): IOException {
        if (e is HostKeyUntrusted || e is RemoteCrossDevice || e is Explained) return e as IOException
        val chain = generateSequence(e) { it.cause }.toList()
        chain.firstNotNullOfOrNull { it as? KeyDecryptionFailedException }?.let {
            return IOException("סיסמת המפתח שגויה", e)
        }
        chain.firstNotNullOfOrNull { it as? SFTPException }?.let { sftp ->
            val message = when (sftp.statusCode) {
                Response.StatusCode.NO_SUCH_FILE, Response.StatusCode.NO_SUCH_PATH -> "הפריט לא נמצא בשרת"
                Response.StatusCode.PERMISSION_DENIED, Response.StatusCode.WRITE_PROTECT, Response.StatusCode.CANNOT_DELETE -> "אין הרשאה לפעולה הזו בשרת"
                Response.StatusCode.FILE_ALREADY_EXISTS -> "כבר קיים פריט בשם הזה"
                Response.StatusCode.NO_SPACE_ON_FILESYSTEM, Response.StatusCode.QUOTA_EXCEEDED -> "אין מקום פנוי בשרת"
                Response.StatusCode.DIR_NOT_EMPTY -> "התיקייה לא ריקה"
                Response.StatusCode.NOT_A_DIRECTORY -> "זו לא תיקייה"
                Response.StatusCode.FILE_IS_A_DIRECTORY -> "זו תיקייה, לא קובץ"
                Response.StatusCode.INVALID_FILENAME -> "השרת לא מקבל את השם הזה"
                Response.StatusCode.OP_UNSUPPORTED -> "השרת לא תומך בפעולה הזו"
                Response.StatusCode.NO_CONNECTION, Response.StatusCode.CONNECITON_LOST -> "החיבור לשרת נותק"
                else -> "השרת סירב לפעולה (${sftp.statusCode.name})"
            }
            return IOException(message, e)
        }
        chain.firstNotNullOfOrNull { it as? UserAuthException }?.let { auth ->
            // מפתח שלא נקרא: sshj זורק אותו מתוך ההתחברות.
            val unreadableKey = generateSequence(auth.cause) { it.cause }.any { it is IOException && it !is SSHException }
            return IOException(
                when {
                    unreadableKey -> "המפתח הפרטי לא נקרא — קובץ לא תקין, או שצריך את הסיסמה שלו"
                    connection.keySecret != null -> "השרת לא קיבל את המפתח עבור ${connection.user}"
                    else -> "שם המשתמש או הסיסמה שגויים"
                },
                e,
            )
        }
        NetErrors.network(e, connection)?.let { return it }
        chain.firstNotNullOfOrNull { it as? SSHException }?.let { ssh ->
            val message = when (ssh.disconnectReason) {
                DisconnectReason.KEY_EXCHANGE_FAILED -> "אין שיטת הצפנה משותפת עם השרת"
                DisconnectReason.PROTOCOL_VERSION_NOT_SUPPORTED, DisconnectReason.PROTOCOL_ERROR -> "זה לא שרת SSH (או גרסה שלא נתמכת)"
                DisconnectReason.TOO_MANY_CONNECTIONS -> "יותר מדי חיבורים לשרת — נסה שוב עוד רגע"
                DisconnectReason.HOST_NOT_ALLOWED_TO_CONNECT -> "השרת לא מאפשר חיבור מהמכשיר הזה"
                DisconnectReason.CONNECTION_LOST, DisconnectReason.BY_APPLICATION -> "החיבור לשרת נותק"
                DisconnectReason.NO_MORE_AUTH_METHODS_AVAILABLE -> "השרת לא מאפשר להתחבר בסיסמה או במפתח"
                else -> null
            }
            if (message != null) return IOException(message, e)
        }
        return e as? IOException ?: IOException(e.message ?: "שגיאת SFTP", e)
    }
}
