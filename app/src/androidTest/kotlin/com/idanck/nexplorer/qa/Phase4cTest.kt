package com.idanck.nexplorer.qa

import android.content.Context
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.idanck.nexplorer.MainActivity
import com.idanck.nexplorer.NexplorerApp
import com.idanck.nexplorer.data.crypto.EncryptedFiles
import com.idanck.nexplorer.data.crypto.Vaults
import com.idanck.nexplorer.data.transfer.TransferKind
import com.idanck.nexplorer.qa.Qa.waitFor
import com.idanck.nexplorer.qa.Qa.waitForDisk
import com.idanck.nexplorer.qa.Qa.waitGone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/** שלב 4ג: "תיקייה מוצפנת" מ"חדש", מה שנכנס אליה מוצפן, פתיחה בלי סיסמה, נעילה, וכניסה בסיסמה. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Phase4cTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val root get() = QaData.root
    private val container get() = (ApplicationProvider.getApplicationContext<Context>() as NexplorerApp).container
    private val vault get() = File(root, "Vault")
    private val password = "כספת qa"

    @Before
    fun setUp() {
        Qa.grantStorage()
        QaData.seed()
        container.vaults.lockAll()
        runBlocking { container.settings.update { it.copy(favorites = emptyList(), hiddenFolders = emptySet()) } }
        scenario = Qa.launch(root.absolutePath)
        compose.waitFor(hasText("readme.md"), 15_000)
    }

    @After
    fun tearDown() = scenario.close()

    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction())

    @Test
    fun t01_createVaultThenWhatEntersIsEncrypted() {
        compose.onNodeWithContentDescription("חדש").performClick()
        compose.waitFor(hasText("תיקייה מוצפנת"))
        Qa.screenshot(compose, "102-new-menu")
        compose.onNodeWithText("תיקייה מוצפנת").performClick()
        compose.waitFor(hasText("שם התיקייה"))
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Vault")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(password)
        compose.onAllNodes(hasSetTextAction())[2].performTextInput(password)
        Qa.screenshot(compose, "103-new-vault-dialog")
        button("צור").performClick()
        compose.waitForDisk(30_000) { Vaults.isVault(vault) }
        compose.waitFor(hasText("Vault"))
        assertTrue("נוצרה פתוחה", container.vaults.isUnlocked(vault))

        // העתקה והעברה לתוכה — דרך תור ההעברות, כמו "הדבק" או גרירה.
        val shot = File(root, "Photos/shot-0.png")
        val readme = File(root, "readme.md")
        container.transfers.enqueue(TransferKind.Copy, listOf(shot.absolutePath), vault.absolutePath)
        container.transfers.enqueue(TransferKind.Move, listOf(readme.absolutePath), vault.absolutePath)
        compose.waitForDisk(60_000) { File(vault, "shot-0.png.enc").isFile && File(vault, "readme.md.enc").isFile && !readme.exists() }
        assertTrue(EncryptedFiles.isEncrypted(File(vault, "shot-0.png.enc")))
        assertTrue(EncryptedFiles.isEncrypted(File(vault, "readme.md.enc")))
        assertTrue("בהעתקה המקור נשאר", shot.isFile)
        assertFalse("שום דבר לא נכנס לא מוצפן", File(vault, "shot-0.png").exists() || File(vault, "readme.md").exists())

        // פתוחה: נכנסים ופותחים בלי סיסמה.
        compose.onNodeWithText("Vault").performClick()
        compose.waitFor(hasText("shot-0.png.enc"), 15_000)
        Qa.screenshot(compose, "104-vault-inside")
        compose.onNodeWithText("shot-0.png.enc").performClick()
        compose.waitFor(hasText("1 / 1"), 30_000)
        Qa.screenshot(compose, "105-vault-file-opened")
        Qa.device.pressBack()
        compose.waitFor(hasText("readme.md.enc"))

        // "נעל" — יוצאים לתיקייה שמעליה, והמפתח נשכח.
        compose.onNodeWithContentDescription("עוד פעולות").performClick()
        compose.waitFor(hasText("נעל תיקייה מוצפנת"))
        compose.onNodeWithText("נעל תיקייה מוצפנת").performClick()
        compose.waitFor(hasText("big.bin"))
        compose.waitGone(hasText("readme.md.enc"))
        assertFalse(container.vaults.isUnlocked(vault))
    }

    @Test
    fun t02_lockedVaultAsksForThePasswordAndLocksInTheBackground() {
        runBlocking {
            val (dir, keys) = Vaults.create(root, "Vault", password.toCharArray())
            File(root, "inside.txt").writeText("סוד\n")
            EncryptedFiles.encryptFile(File(root, "inside.txt"), dir, keys, {}, {})
            File(root, "inside.txt").delete()
            keys.close()
        }
        scenario.close()
        scenario = Qa.launch(root.absolutePath)
        compose.waitFor(hasText("Vault"), 15_000)

        compose.onNodeWithText("Vault").performClick()
        compose.waitFor(hasText("תיקייה מוצפנת"))
        compose.onNode(hasSetTextAction()).performTextInput("wrong")
        button("פתח").performClick()
        compose.waitFor(hasText("סיסמה שגויה"), 30_000)
        Qa.screenshot(compose, "106-unlock-vault-wrong")
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput(password)
        button("פתח").performClick()
        compose.waitFor(hasText("inside.txt.enc"), 30_000)
        assertTrue(container.vaults.isUnlocked(vault))

        // יציאה מהאפליקציה נועלת, וחזרה מוצאת את הרשימה שמעל התיקייה.
        scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitForDisk(10_000) { !container.vaults.isUnlocked(vault) }
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitFor(hasText("big.bin"), 15_000)
        compose.waitGone(hasText("inside.txt.enc"))
    }

    @Test
    fun t03_extractIntoVaultIsRefused() {
        // פתוחה — ובכל זאת: חילוץ היה כותב בה קבצים לא מוצפנים.
        val (dir, keys) = runBlocking { Vaults.create(root, "Vault", password.toCharArray()) }
        container.vaults.put(dir, keys)
        container.transfers.enqueue(TransferKind.Extract, listOf(File(root, "archive.zip").absolutePath), vault.absolutePath)
        compose.waitFor(hasText("חילוץ לתוך תיקייה מוצפנת", substring = true), 15_000)
        assertEquals(listOf(Vaults.MARKER), vault.list()!!.toList())
    }
}
