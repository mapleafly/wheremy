package com.wheremy.app.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.backup.AutoExporter
import com.wheremy.app.data.backup.BackupRepository
import com.wheremy.app.data.backup.ImportMode
import com.wheremy.app.data.db.DB_SCHEMA_VERSION
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.settings.testSettings
import com.wheremy.app.domain.ReminderEngine
import com.wheremy.app.reminder.ReminderRunner
import com.wheremy.app.ui.theme.WheremyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 备份加密的界面与流程（`FR-BAK-09` / `ADR-27` / `UI.md` §7.10）。
 *
 * 钉三件事：
 * 1. **导出前先问口令**（不加这一步，用户根本不知道包是不是明文的）；
 * 2. **没给口令的加密包要弹"请输入口令"，而不是报"导入失败"**；
 * 3. 口令不对时说 🗣「口令不对」，**对话框留着**让人重试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsBackupEncryptionUiTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var store: PhotoStore
    private lateinit var backup: BackupRepository
    private lateinit var vm: SettingsViewModel

    @Before
    fun setUp() {
        // 与其它 Compose 测试同一套：viewModelScope 跑在 Main 上，不设 Unconfined 就得靠泵主循环，
        // 断言会抢在协程之前跑（这一版就踩了：三条导入用例等不到 needImportPassword）
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PhotoStore(File(tmp.root, "photos"))
        backup = BackupRepository(db, store, "0.1.0-test", File(store.rootDir, ".incoming"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun render() {
        val settings = testSettings()
        vm = SettingsViewModel(
            settings = settings,
            runner = ReminderRunner(settings, ReminderEngine(db.itemDao())),
            backup = backup,
            exporter = AutoExporter(backup, { null }, settings),
        )
        compose.setContent {
            WheremyTheme(darkTheme = false) {
                SettingsScreen(
                    onOpenDesignGallery = {},
                    onOpenDataSelfCheck = {},
                    onOpenDevSeed = {},
                    vm = vm,
                )
            }
        }
        compose.waitUntil(timeoutMillis = 20_000) { !vm.state.value.loading }
    }

    @Test
    fun `点导出先问口令，两个选项都是真按钮`() {
        render()

        compose.onNodeWithText("导出到文件").performScrollTo().performClick()

        compose.onNodeWithText("给这个备份加个口令？").assertIsDisplayed()
        compose.onNodeWithText("加密导出").assertIsDisplayed()
        compose.onNodeWithText("不加密导出").assertIsDisplayed()
    }

    @Test
    fun `选了加密才出现口令输入，两次不一样就停在框里`() {
        render()

        compose.onNodeWithText("导出到文件").performScrollTo().performClick()
        compose.onNodeWithText("加密导出").performClick()

        compose.onNodeWithTag("export_password").performTextInput("hunter2hunter2")
        compose.onNodeWithTag("export_password_again").performTextInput("hunter2hunter3")
        compose.onNodeWithText("加密导出").performClick()

        compose.onNodeWithText("两次输入的口令不一样").assertIsDisplayed()
    }

    @Test
    fun `没设口令时状态行说清导的是明文包`() {
        render()

        compose.onNodeWithTag("settings_backup_password_line").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("自动导出导的是明文包（没设口令）。").assertIsDisplayed()
    }

    // —— 导入侧的三种收场（VM 层看状态，不必真点系统选择器）——

    private fun encryptedPack(password: String): ByteArray {
        val out = ByteArrayOutputStream()
        runBlocking { backup.exportTo(out, at = 42L, password = password) }
        return out.toByteArray()
    }

    @Test
    fun `导入加密包没给口令：要口令而不是报导入失败`() {
        render()
        val pack = encryptedPack("correct horse battery")

        vm.importBackup(ImportMode.MERGE, { ByteArrayInputStream(pack) }, password = null)

        compose.waitUntil(timeoutMillis = 10_000) { vm.state.value.needImportPassword }
        assertNull("不能顺手报一句「导入失败」", vm.state.value.backupResult)
        compose.onNodeWithText("这个备份包加密了").assertIsDisplayed()
    }

    @Test
    fun `口令不对：说口令不对，对话框留着`() {
        render()
        val pack = encryptedPack("correct horse battery")

        vm.importBackup(ImportMode.MERGE, { ByteArrayInputStream(pack) }, password = "wrong")

        compose.waitUntil(timeoutMillis = 10_000) { vm.state.value.importPasswordError != null }
        assertEquals("口令不对", vm.state.value.importPasswordError)
        assertTrue("对话框要留着让人重试", vm.state.value.needImportPassword)
        assertNull(vm.state.value.backupResult)
        compose.onNodeWithText("口令不对").assertIsDisplayed()
    }

    @Test
    fun `取消之后两个状态都清掉，库没被动过`() {
        render()
        val pack = encryptedPack("correct horse battery")
        vm.importBackup(ImportMode.MERGE, { ByteArrayInputStream(pack) }, password = null)
        compose.waitUntil(timeoutMillis = 10_000) { vm.state.value.needImportPassword }

        vm.cancelImportPassword()

        assertFalse(vm.state.value.needImportPassword)
        assertNull(vm.state.value.importPasswordError)
        assertEquals(0, runBlocking { db.itemDao().all() }.size)
        assertEquals("schema 版本没被写过东西", DB_SCHEMA_VERSION, DB_SCHEMA_VERSION)
    }
}
