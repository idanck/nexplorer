package com.idanck.nexplorer.qa

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.idanck.nexplorer.MainActivity
import com.idanck.nexplorer.qa.Qa.waitFor
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SearchAndSettingsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        Qa.grantStorage()
        QaData.seed()
        scenario = Qa.launch(QaData.root.absolutePath)
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

    private fun openSettings() {
        compose.onNodeWithContentDescription("עוד פעולות").performClick()
        compose.waitFor(hasText("אפשרויות תצוגה"))
        clickDisplayed(hasText("הגדרות"))
        compose.waitFor(hasText("ערכת צבע"))
    }

    @Test
    fun t01_searchSyntax() {
        compose.onNodeWithContentDescription("חיפוש").performClick()
        compose.waitFor(hasSetTextAction())
        Qa.screenshot(compose, "40-search-empty")
        compose.onNode(hasSetTextAction()).performTextInput("*.txt")
        compose.waitFor(hasText("notes.txt", substring = true), 15_000)
        compose.waitFor(hasText("דוח שנתי 2026.txt", substring = true))
        Qa.screenshot(compose, "41-search-glob")

        compose.onNode(hasSetTextAction()).performTextReplacement("notes | readme")
        compose.waitFor(hasText("readme.md", substring = true), 15_000)
        compose.waitFor(hasText("notes.txt", substring = true))
        Qa.screenshot(compose, "42-search-or")

        compose.onNode(hasSetTextAction()).performTextReplacement("zzz-nothing")
        compose.waitFor(hasText("לא נמצא כלום."), 15_000)
        compose.onNodeWithContentDescription("חזרה").performClick()
        compose.waitFor(hasText("readme.md"))
    }

    @Test
    fun t02_settingsAndDarkTheme() {
        openSettings()
        Qa.screenshot(compose, "43-settings")
        compose.onNodeWithText("אודות").performScrollTo()
        Qa.screenshot(compose, "44-settings-bottom")

        compose.onNodeWithText("ערכת צבע").performScrollTo()
        compose.onNodeWithText("כהה").performClick()
        Qa.screenshot(compose, "45-settings-dark")
        compose.onNodeWithContentDescription("חזרה").performClick()
        compose.waitFor(hasText("readme.md"))
        Qa.screenshot(compose, "46-browser-dark")
        compose.onNodeWithContentDescription("פתח מגירת מקומות").performClick()
        Qa.screenshot(compose, "47-drawer-dark")
        Qa.device.pressBack()
        Thread.sleep(400)

        openSettings()
        compose.onNodeWithText("מערכת").performClick()
        compose.onNodeWithContentDescription("חזרה").performClick()
        compose.waitFor(hasContentDescription("חיפוש"))
    }
}
