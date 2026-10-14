package com.idanck.nexplorer.data.net

import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMBApiException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/** שגיאה של smbj (או של הרשת מתחתיו) → IOException עם הסבר בעברית. */
internal object SmbErrors {

    fun map(e: Throwable, connection: NetConnection): IOException {
        if (e is SmbFs.NotFound) return e
        val chain = generateSequence(e) { it.cause }.toList()
        val server = connection.subtitle.substringAfter('@')
        chain.firstNotNullOfOrNull { it as? SMBApiException }?.let { api ->
            val message = when (api.status) {
                NtStatus.STATUS_LOGON_FAILURE -> "שם המשתמש או הסיסמה שגויים"
                NtStatus.STATUS_ACCOUNT_DISABLED, NtStatus.STATUS_PASSWORD_EXPIRED, NtStatus.STATUS_LOGON_TYPE_NOT_GRANTED,
                -> "השרת לא מאפשר להתחבר עם המשתמש הזה"
                NtStatus.STATUS_ACCESS_DENIED, NtStatus.STATUS_CANNOT_DELETE -> "אין הרשאה לפעולה הזו בשרת"
                NtStatus.STATUS_BAD_NETWORK_NAME -> return SmbFs.NotFound("השיתוף לא נמצא בשרת")
                NtStatus.STATUS_OBJECT_NAME_NOT_FOUND, NtStatus.STATUS_OBJECT_PATH_NOT_FOUND,
                NtStatus.STATUS_NO_SUCH_FILE, NtStatus.STATUS_DELETE_PENDING,
                -> return SmbFs.NotFound("הפריט לא נמצא בשרת")
                NtStatus.STATUS_OBJECT_NAME_COLLISION -> "כבר קיים פריט בשם הזה"
                NtStatus.STATUS_DIRECTORY_NOT_EMPTY -> "התיקייה לא ריקה"
                NtStatus.STATUS_OBJECT_NAME_INVALID -> "השרת לא מקבל את השם הזה"
                NtStatus.STATUS_DISK_FULL -> "אין מקום פנוי בשרת"
                NtStatus.STATUS_SHARING_VIOLATION -> "הקובץ פתוח כרגע בתוכנה אחרת"
                NtStatus.STATUS_NOT_A_DIRECTORY -> "זו לא תיקייה"
                NtStatus.STATUS_FILE_IS_A_DIRECTORY -> "זו תיקייה, לא קובץ"
                NtStatus.STATUS_BAD_NETWORK_PATH -> "השרת $server לא נמצא"
                else -> "שגיאת SMB (${api.status.name})"
            }
            return IOException(message, e)
        }
        NetErrors.network(e, connection)?.let { return it }
        return e as? IOException ?: IOException(e.message ?: "שגיאת רשת", e)
    }
}

/** שגיאות רשת שמשותפות לכל הפרוטוקולים: שרת שלא נמצא, שלא עונה, או שלא מקבל חיבורים. */
internal object NetErrors {

    /** null — זו לא שגיאת רשת. */
    fun network(e: Throwable, connection: NetConnection): IOException? {
        val server = connection.subtitle.substringAfter('@')
        val chain = generateSequence(e) { it.cause }.toList()
        val message = when (chain.firstOrNull { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException || it is SocketTimeoutException || it is TimeoutException }) {
            is UnknownHostException -> "השרת $server לא נמצא — בדוק את הכתובת"
            is ConnectException -> "השרת $server לא מקבל חיבורים (פורט ${connection.effectivePort})"
            is NoRouteToHostException -> "אין דרך להגיע ל-$server — בדוק שאתה באותה רשת"
            is SocketTimeoutException, is TimeoutException -> "השרת $server לא עונה"
            else -> null
        }
        return message?.let { IOException(it, e) }
    }
}
