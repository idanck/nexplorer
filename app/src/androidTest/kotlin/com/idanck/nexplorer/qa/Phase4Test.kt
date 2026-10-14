package com.idanck.nexplorer.qa

import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.idanck.nexplorer.MainActivity
import com.idanck.nexplorer.NexplorerApp
import com.idanck.nexplorer.core.FolderSizes
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.qa.Qa.waitFor
import com.idanck.nexplorer.qa.Qa.waitGone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/** שלב 4א: הסתר תיקייה והחזרה מההגדרות, גודל תיקיות, וסידור המועדפים. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Phase4Test {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val root get() = QaData.root
    private val settings get() = (ApplicationProvider.getApplicationContext<Context>() as NexplorerApp).container.settings

    @Before
    fun setUp() {
        Qa.grantStorage()
        QaData.seed()
        // ההעדפות נשמרות בין בדיקות: כל בדיקה מתחילה בלי מוסתרות, בלי מועדפים ובלי "גודל תיקיות".
        runBlocking {
            settings.update {
                it.copy(
                    hiddenFolders = emptySet(),
                    favorites = emptyList(),
                    defaultView = it.defaultView.copy(showFolderSizes = false),
                    folderViews = emptyMap(),
                )
            }
        }
        scenario = Qa.launch(root.absolutePath)
        compose.waitFor(hasText("readme.md"), 15_000)
    }

    @After
    fun tearDown() = scenario.close()

    private fun clickDisplayed(matcher: SemanticsMatcher) {
        val nodes = compose.onAllNodes(matcher)
        val count = nodes.fetchSemanticsNodes().size
        for (i in 0 until count) {
            if (runCatching { nodes[i].assertIsDisplayed() }.isSuccess) {
                nodes[i].performClick()
                return
            }
        }
        throw AssertionError("אין צומת מוצג שמתאים ל-$matcher")
    }

    /** ⋮ ← אפשרויות תיקייה ← [item]. */
    private fun folderOption(item: String) {
        compose.onNodeWithContentDescription("עוד פעולות").performClick()
        compose.waitFor(hasText("אפשרויות תיקייה"))
        compose.onNodeWithText("אפשרויות תיקייה").performClick()
        compose.waitFor(hasText(item))
        Qa.screenshot(compose, "91-folder-options")
        compose.onNodeWithText(item).performClick()
    }

    @Test
    fun t01_hideFolderThenShowItFromSettings() {
        val documents = File(root, "Documents").absolutePath
        compose.onNodeWithText("Documents").performClick()
        compose.waitFor(hasText("notes.txt"))
        folderOption("הסתר תיקייה")

        // חזרה לתיקייה שמעל, בלי התיקייה שהוסתרה; במכשיר היא נשארת.
        compose.waitFor(hasText("התיקייה הוסתרה", substring = true))
        compose.waitFor(hasText("readme.md"))
        compose.waitGone(hasText("Documents"))
        assertEquals(setOf(documents), settings.state.value.hiddenFolders)
        assertTrue(File(documents, "notes.txt").isFile)

        compose.onNodeWithContentDescription("עוד פעולות").performClick()
        compose.waitFor(hasText("אפשרויות תצוגה"))
        clickDisplayed(hasText("הגדרות"))
        compose.waitFor(hasText("תיקיות מוסתרות"))
        compose.onNodeWithText("תיקיות מוסתרות").performScrollTo().performClick()
        compose.waitFor(hasText(documents))
        Qa.screenshot(compose, "92-hidden-folders")
        compose.onNodeWithText("הצג").performClick()
        compose.waitGone(hasText(documents))
        assertTrue(settings.state.value.hiddenFolders.isEmpty())

        compose.onNodeWithContentDescription("חזרה").performClick()
        compose.waitFor(hasText("Documents"))
    }

    @Test
    fun t02_folderSizes() {
        val documents = FolderSizes.of(File(root, "Documents"))!!
        val photos = FolderSizes.of(File(root, "Photos"))!!
        folderOption("גודל תיקיות")
        // הגודל נכנס לשורת הפרטים של כל תיקייה ("3 פריטים · 49.2 KB · …").
        compose.waitFor(hasText(SizeFormat.short(documents), substring = true), 15_000)
        compose.waitFor(hasText(SizeFormat.short(photos), substring = true), 15_000)
        Qa.screenshot(compose, "93-folder-sizes")
        assertTrue(settings.state.value.defaultView.showFolderSizes)

        folderOption("גודל תיקיות")
        compose.waitGone(hasText(SizeFormat.short(documents), substring = true))
    }

    @Test
    fun t03_reorderAndRemoveFavorites() {
        val documents = File(root, "Documents").absolutePath
        val photos = File(root, "Photos").absolutePath
        val music = File(root, "Music").absolutePath
        runBlocking { settings.update { it.copy(favorites = listOf(documents, photos, music)) } }

        compose.onNodeWithContentDescription("פתח מגירת מקומות").performClick()
        compose.waitFor(hasText("סדר"))
        compose.onNodeWithText("סדר").performClick()
        compose.waitFor(hasText(photos))
        // Photos למעלה (לפני Documents), Music מוסר.
        compose.onAllNodesWithContentDescription("הזז למעלה")[1].performClick()
        compose.onAllNodesWithContentDescription("הסר מהמועדפים")[2].performClick()
        compose.waitGone(hasText(music))
        Qa.screenshot(compose, "94-favorites-manager")
        compose.onNodeWithText("סיום").performClick()

        compose.waitUntil(5_000) { settings.state.value.favorites == listOf(photos, documents) }
        Qa.device.pressBack()
    }
}
