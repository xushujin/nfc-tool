package com.nfctool.app.data

import android.content.Context
import com.nfctool.app.data.db.AppDatabase
import com.nfctool.app.data.db.HistoryDao
import com.nfctool.app.data.db.SettingsDao

/**
 * 手工依赖容器。
 *
 * 不引入 DI 框架：本应用只有两个仓库、一个数据库，
 * 手写装配比引入框架更直观，也少一层运行时反射。
 * 依赖关系集中在这里，便于一眼看清整体结构。
 */
class AppContainer(context: Context) {

    /** 应用级 Context。ViewModel 里读取当前 WiFi 需要它（不能用 Activity，否则会泄漏）。 */
    val appContext: Context = context.applicationContext

    private val database: AppDatabase by lazy { AppDatabase(appContext) }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(SettingsDao(database))
    }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(HistoryDao(database), settingsRepository)
    }
}
