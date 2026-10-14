package com.idanck.nexplorer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApkStatusTest {

    @Test
    fun `install state against what is installed`() {
        assertEquals(ApkInstallState.NotInstalled, ApkStatus.of(5, null, null))
        assertEquals(ApkInstallState.Upgrade, ApkStatus.of(6, 5, true))
        assertEquals(ApkInstallState.Same, ApkStatus.of(5, 5, true))
        assertEquals(ApkInstallState.Downgrade, ApkStatus.of(4, 5, true))
        // מפתח אחר קודם לגרסה: גם עדכון "חדש יותר" ייכשל.
        assertEquals(ApkInstallState.OtherSigner, ApkStatus.of(6, 5, false))
        // חתימה שלא נקראה לא חוסמת — המתקין יחליט.
        assertEquals(ApkInstallState.Upgrade, ApkStatus.of(6, 5, null))
    }

    @Test
    fun `only states that can install offer an action`() {
        assertEquals("עדכן", ApkInstallState.Upgrade.action)
        assertNull(ApkInstallState.Downgrade.action)
        assertNull(ApkInstallState.OtherSigner.action)
    }

    @Test
    fun `sdk labels`() {
        assertEquals("API 26 · Android 8.0", ApkStatus.sdkLabel(26))
        assertEquals("API 35 · Android 15", ApkStatus.sdkLabel(35))
        assertEquals("API 99", ApkStatus.sdkLabel(99))
    }
}
