package com.nfctool.app.data

import com.nfctool.app.data.db.SettingsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** 设置项的键名。新增设置项时只在这里加常量，并同步 [AppSettings] 与 [AppSettings.DEFAULT]。 */
object SettingsKeys {
    const val THEME_ID = "theme_id"
    const val VIBRATE_ON_SCAN = "vibrate_on_scan"
    const val AUTO_SAVE_HISTORY = "auto_save_history"
    const val NDEF_LANGUAGE = "ndef_language"

    /** 用户主动跳过的更新版本（空串表示没有跳过任何版本）。 */
    const val SKIPPED_UPDATE_VERSION = "skipped_update_version"
}

/** 全部应用设置的不可变快照。 */
data class AppSettings(
    val themeId: String,
    val vibrateOnScan: Boolean,
    val autoSaveHistory: Boolean,
    val ndefLanguage: String,
    /** 已跳过的更新版本号；空串表示未跳过。 */
    val skippedUpdateVersion: String = "",
) {
    fun toMap(): Map<String, String> = mapOf(
        SettingsKeys.THEME_ID to themeId,
        SettingsKeys.VIBRATE_ON_SCAN to vibrateOnScan.toString(),
        SettingsKeys.AUTO_SAVE_HISTORY to autoSaveHistory.toString(),
        SettingsKeys.NDEF_LANGUAGE to ndefLanguage,
        SettingsKeys.SKIPPED_UPDATE_VERSION to skippedUpdateVersion,
    )

    companion object {
        const val DEFAULT_THEME_ID = "aurora"

        val DEFAULT = AppSettings(
            themeId = DEFAULT_THEME_ID,
            vibrateOnScan = true,
            autoSaveHistory = true,
            ndefLanguage = "zh",
        )

        fun fromMap(values: Map<String, String>): AppSettings = AppSettings(
            themeId = values[SettingsKeys.THEME_ID]?.takeIf { it.isNotBlank() } ?: DEFAULT.themeId,
            vibrateOnScan = values[SettingsKeys.VIBRATE_ON_SCAN]?.toBooleanStrictOrNull()
                ?: DEFAULT.vibrateOnScan,
            autoSaveHistory = values[SettingsKeys.AUTO_SAVE_HISTORY]?.toBooleanStrictOrNull()
                ?: DEFAULT.autoSaveHistory,
            ndefLanguage = values[SettingsKeys.NDEF_LANGUAGE]?.takeIf { it.isNotBlank() }
                ?: DEFAULT.ndefLanguage,
            skippedUpdateVersion = values[SettingsKeys.SKIPPED_UPDATE_VERSION].orEmpty(),
        )
    }
}

/**
 * 应用设置的**唯一**读写入口。
 *
 * 约定：任何设置项都只能经由本类读写，界面不允许直接碰 [SettingsDao]，
 * 也不允许自己缓存一份设置副本。这样「同一个设置只有一套实现」，
 * 主题切换等改动能通过 [settings] 这个 StateFlow 全局生效。
 */
class SettingsRepository(private val dao: SettingsDao) {

    private val mutableSettings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()

    /** 首次读取；解析失败的字段回退到默认值。 */
    fun load(): AppSettings = AppSettings.fromMap(dao.all())

    /** 更新设置：先落库再改内存状态，避免界面显示已生效但实际没存下。 */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = mutableSettings.value
        val next = transform(current)
        if (next == current) return
        withContext(Dispatchers.IO) {
            dao.putAll(next.toMap())
        }
        mutableSettings.value = next
    }

    suspend fun setThemeId(themeId: String) = update { it.copy(themeId = themeId) }

    suspend fun setVibrateOnScan(enabled: Boolean) = update { it.copy(vibrateOnScan = enabled) }

    suspend fun setAutoSaveHistory(enabled: Boolean) = update { it.copy(autoSaveHistory = enabled) }

    suspend fun setNdefLanguage(language: String) = update { it.copy(ndefLanguage = language) }

    suspend fun setSkippedUpdateVersion(version: String) =
        update { it.copy(skippedUpdateVersion = version.trim()) }
}
