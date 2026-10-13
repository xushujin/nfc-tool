package com.nfctool.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.nfctool.app.data.db.AppDatabase
import com.nfctool.app.data.db.SettingsDao
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 设置仓库测试。
 *
 * 设置项只有这一个读写入口，因此这里要覆盖两件事：
 * 落库与内存状态一致、损坏的设置值能安全回退到默认值。
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        database = AppDatabase(context)
        repository = SettingsRepository(SettingsDao(database))
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
    }

    @Test
    fun `首次读取返回默认设置`() {
        assertThat(repository.load()).isEqualTo(AppSettings.DEFAULT)
        assertThat(repository.settings.value).isEqualTo(AppSettings.DEFAULT)
    }

    @Test
    fun `修改设置后内存状态与落库结果一致`() = runTest {
        repository.setThemeId("graphite")
        repository.setVibrateOnScan(false)
        repository.setAutoSaveHistory(false)
        repository.setNdefLanguage("en")

        val expected = AppSettings(
            themeId = "graphite",
            vibrateOnScan = false,
            autoSaveHistory = false,
            ndefLanguage = "en",
        )
        assertThat(repository.settings.value).isEqualTo(expected)
        assertThat(repository.load()).isEqualTo(expected)
    }

    @Test
    fun `重复写入相同值不会改变状态`() = runTest {
        val before = repository.settings.value
        repository.setThemeId(before.themeId)
        assertThat(repository.settings.value).isSameInstanceAs(before)
    }

    @Test
    fun `损坏的设置值回退到默认值而不是崩溃`() {
        SettingsDao(database).putAll(
            mapOf(
                SettingsKeys.THEME_ID to "",
                SettingsKeys.VIBRATE_ON_SCAN to "不是布尔值",
                SettingsKeys.AUTO_SAVE_HISTORY to "yes",
                SettingsKeys.NDEF_LANGUAGE to "   ",
            ),
        )
        val loaded = repository.load()
        assertThat(loaded.themeId).isEqualTo(AppSettings.DEFAULT.themeId)
        assertThat(loaded.vibrateOnScan).isEqualTo(AppSettings.DEFAULT.vibrateOnScan)
        assertThat(loaded.autoSaveHistory).isEqualTo(AppSettings.DEFAULT.autoSaveHistory)
        assertThat(loaded.ndefLanguage).isEqualTo(AppSettings.DEFAULT.ndefLanguage)
    }

    @Test
    fun `未知主题 id 由配色表回退到默认配色`() {
        assertThat(com.nfctool.app.ui.theme.NeuPalettes.byId("不存在的主题"))
            .isEqualTo(com.nfctool.app.ui.theme.NeuPalettes.DEFAULT)
        assertThat(com.nfctool.app.ui.theme.NeuPalettes.contains("aurora")).isTrue()
        assertThat(com.nfctool.app.ui.theme.NeuPalettes.contains("不存在")).isFalse()
    }

    @Test
    fun `设置项序列化与反序列化互逆`() {
        val settings = AppSettings(
            themeId = "midnight",
            vibrateOnScan = false,
            autoSaveHistory = true,
            ndefLanguage = "ja",
        )
        assertThat(AppSettings.fromMap(settings.toMap())).isEqualTo(settings)
    }

    @Test
    fun `设置键名与序列化字段一一对应`() {
        val keys = AppSettings.DEFAULT.toMap().keys
        assertThat(keys).containsExactly(
            SettingsKeys.THEME_ID,
            SettingsKeys.VIBRATE_ON_SCAN,
            SettingsKeys.AUTO_SAVE_HISTORY,
            SettingsKeys.NDEF_LANGUAGE,
        )
    }
}
