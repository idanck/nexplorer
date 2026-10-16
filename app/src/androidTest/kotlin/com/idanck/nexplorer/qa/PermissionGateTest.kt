package com.idanck.nexplorer.qa

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.idanck.nexplorer.qa.Qa.exists
import com.idanck.nexplorer.qa.Qa.waitFor
import com.idanck.nexplorer.ui.permission.StorageAccess
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * רץ ראשון, על התקנה טרייה (`.github/qa/instrumented.sh`): בלי הרשאה — מסך הגישה;
 * "אפשר גישה" מוביל למסך המערכת הנכון; אחרי מתן ההרשאה וחזרה — חלון הקבצים.
 */
@RunWith(AndroidJUnit4::class)
class PermissionGateTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun gateLeadsToSystemSettingsAndOpensAfterGrant() {
        assumeFalse("ההרשאה כבר ניתנה — הבדיקה צריכה התקנה טרייה", StorageAccess.granted())
        val scenario = Qa.launch()

        compose.waitFor(hasText("גישה לקבצים"))
        compose.onNodeWithText("אפשר גישה").assertExists()
        Qa.screenshot(compose, "01-permission-gate")

        compose.onNodeWithText("אפשר גישה").performClick()
        val device = Qa.device
        // מסך "גישה לכל הקבצים" של האפליקציה הזו: מתג אחד.
        val toggle = device.wait(Until.findObject(By.checkable(true)), 8_000)
        Thread.sleep(500)
        Qa.screenshot(compose, "02-system-all-files-access")
        if (toggle != null) toggle.click() else Qa.grantStorage()
        device.waitForIdle()
        if (!StorageAccess.granted()) Qa.grantStorage()
        device.pressBack()

        // הבדיקה חוזרת ב-ON_RESUME; אם החזרה מהמערכת לא עברה דרכו — מכריחים.
        scenario.moveToState(Lifecycle.State.RESUMED)
        if (!compose.exists(hasContentDescription("חיפוש"))) {
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
        }
        compose.waitFor(hasContentDescription("חיפוש"), 15_000)
        Qa.screenshot(compose, "03-browser-after-grant")
        scenario.close()
    }
}
