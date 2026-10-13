package com.nfctool.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 应用数据库。
 *
 * 存储方案：直接使用 Android 平台自带的 SQLite（[SQLiteOpenHelper]），不引入 ORM。
 * 取舍：
 * - 代价：SQL 只能在运行期校验，没有编译期 schema 检查；
 * - 收益：零注解处理器依赖（与 AGP 9 内置 Kotlin 无版本耦合），
 *   迁移逻辑完全显式可控，且可以用 Robolectric 在 JVM 上跑真实 SQLite 验证。
 * 详见 README「存储方案」章节。
 */
class AppDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        for (statement in Schema.CREATE_V1) {
            db.execSQL(statement)
        }
        // 全新库也要把迁移跑一遍，保证「新建」与「从旧版升级」得到完全一致的表结构。
        // 否则新建的库版本号是新版、列却是旧版的，运行期才会报"没有该列"。
        MigrationRunner.run(db, fromVersion = 1, toVersion = VERSION)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 显式逐级迁移。缺脚本即抛错，绝不回退到"删表重建"。
        MigrationRunner.run(db, oldVersion, newVersion)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException(
            "不支持数据库降级：当前版本 $oldVersion，目标版本 $newVersion。" +
                "如需回退请先导出数据并清除应用数据。",
        )
    }

    companion object {
        const val DATABASE_NAME = "nfc_tool.db"

        /** 当前 schema 版本。改动 schema 时必须同步新增迁移，见 [Migrations]。 */
        const val VERSION = 2
    }
}
