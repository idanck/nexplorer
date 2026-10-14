package com.idanck.nexplorer.core

/** מה תעשה התקנה של ה-APK, מול מה שמותקן. */
enum class ApkInstallState(val label: String, val action: String?) {
    NotInstalled("לא מותקן", "התקן"),
    Upgrade("גרסה חדשה יותר מהמותקנת", "עדכן"),
    Same("אותה גרסה מותקנת", "התקן מחדש"),

    /** אנדרואיד לא מוריד גרסה בלי להסיר קודם (והנתונים נמחקים). */
    Downgrade("גרסה ישנה יותר מהמותקנת — צריך להסיר את המותקנת קודם", null),

    /** אנדרואיד מחליף חבילה רק באותה תעודה. */
    OtherSigner("חתום במפתח אחר מהמותקן — ההתקנה תיכשל בלי להסיר את המותקן", null),
}

object ApkStatus {

    /** [sameSigner] — null כשאי אפשר לדעת (אין חתימה שנקראה); אז לא חוסמים, והמתקין יחליט. */
    fun of(apkVersion: Long, installedVersion: Long?, sameSigner: Boolean?): ApkInstallState = when {
        installedVersion == null -> ApkInstallState.NotInstalled
        sameSigner == false -> ApkInstallState.OtherSigner
        apkVersion > installedVersion -> ApkInstallState.Upgrade
        apkVersion == installedVersion -> ApkInstallState.Same
        else -> ApkInstallState.Downgrade
    }

    /** "API 26 · Android 8.0". */
    fun sdkLabel(api: Int): String {
        val name = ANDROID_VERSIONS[api]
        return if (name != null) "API $api · Android $name" else "API $api"
    }

    private val ANDROID_VERSIONS = mapOf(
        21 to "5.0", 22 to "5.1", 23 to "6", 24 to "7.0", 25 to "7.1", 26 to "8.0", 27 to "8.1", 28 to "9",
        29 to "10", 30 to "11", 31 to "12", 32 to "12L", 33 to "13", 34 to "14", 35 to "15", 36 to "16",
    )
}
