package com.idanck.nexplorer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {
    private val sha = "a".repeat(64)

    private fun manifest(
        url: String = "https://nexplorer-updates.idanck.com/apk/nexplorer-0.1.0.7.apk",
        size: Long = 9_000_000,
        hash: String = sha,
        code: Int = 7,
    ) = """
        {"versionCode": $code, "versionName": "0.1.0.7", "apkUrl": "$url", "sha256": "$hash",
         "sizeBytes": $size, "minSdk": 26, "notes": {"he": "תיקון", "en": "fix"}}
    """.trimIndent()

    @Test
    fun `parses what CI publishes`() {
        val m = UpdateManifest.parse(manifest()).getOrThrow()
        assertEquals(7, m.versionCode)
        assertEquals("תיקון", m.notes)
        assertTrue(m.isNewerThan(6))
        assertTrue(!m.isNewerThan(7))
    }

    @Test
    fun `rejects anything that could swap the binary`() {
        assertTrue(UpdateManifest.parse(manifest(url = "http://evil/x.apk")).isFailure)
        assertTrue(UpdateManifest.parse(manifest(hash = "XYZ")).isFailure)
        assertTrue(UpdateManifest.parse(manifest(size = 0)).isFailure)
        assertTrue(UpdateManifest.parse(manifest(size = UpdateManifest.MAX_SIZE_BYTES + 1)).isFailure)
        assertTrue(UpdateManifest.parse(manifest(code = 0)).isFailure)
        assertTrue(UpdateManifest.parse("<html>").isFailure)
    }
}
