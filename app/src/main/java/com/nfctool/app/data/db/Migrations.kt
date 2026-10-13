package com.nfctool.app.data.db

import android.database.sqlite.SQLiteDatabase

/** 一次「版本 N → N+1」的显式迁移。 */
interface Migration {
    val fromVersion: Int
    val toVersion: Int

    /** 执行迁移语句。实现中不得包含删表 / 删数据语句，该约束由单测守卫。 */
    fun migrate(db: SQLiteDatabase)
}

/** 由 SQL 语句列表构成的迁移。 */
class SqlMigration(
    override val fromVersion: Int,
    override val toVersion: Int,
    private val statements: List<String>,
) : Migration {

    init {
        require(toVersion == fromVersion + 1) {
            "迁移必须逐版本推进：from=$fromVersion to=$toVersion"
        }
        require(statements.isNotEmpty()) { "迁移语句不能为空" }
    }

    override fun migrate(db: SQLiteDatabase) {
        for (statement in statements) {
            db.execSQL(statement)
        }
    }

    /** 供单测检查语句内容。 */
    fun statements(): List<String> = statements
}

/**
 * 迁移注册表。
 *
 * 当前数据库版本为 2。每次改 schema 的做法是：
 *   1. 把 [AppDatabase.VERSION] 加 1；
 *   2. 在 [ALL] 中新增一条 `SqlMigration(旧版本, 旧版本 + 1, listOf(...))`；
 *   3. 补一条「旧库 → 新库」的迁移单测。
 * 若缺少对应迁移，[MigrationRunner] 会直接抛错而不是静默重建表。
 */
object Migrations {

    val ALL: Map<Int, Migration> = mapOf(
        1 to SqlMigration(
            fromVersion = 1,
            toVersion = 2,
            statements = listOf(
                // 存标签原文，让历史记录可以"再次写入新卡"。
                // 此前只存摘要，历史只能看不能用；代价是 WiFi 密码一类内容会随之落库，
                // 已在「关于」页与 README 的隐私说明中如实标注。
                "ALTER TABLE ${Schema.TABLE_HISTORY} ADD COLUMN ndef_message BLOB",
                "ALTER TABLE ${Schema.TABLE_HISTORY} ADD COLUMN mifare_dump TEXT",
            ),
        ),
    )

    fun forStep(fromVersion: Int): Migration? = ALL[fromVersion]
}

/** 迁移执行器，抽出来是为了能在单测里注入自造的迁移表验证执行路径。 */
object MigrationRunner {

    /**
     * 从 [fromVersion] 逐级升到 [toVersion]。
     *
     * @throws IllegalStateException 缺少某一级的迁移脚本，或出现降级。
     */
    fun run(
        db: SQLiteDatabase,
        fromVersion: Int,
        toVersion: Int,
        registry: Map<Int, Migration> = Migrations.ALL,
    ) {
        if (fromVersion == toVersion) return
        if (fromVersion > toVersion) {
            throw IllegalStateException("不支持数据库降级：当前 $fromVersion，目标 $toVersion")
        }
        var version = fromVersion
        while (version < toVersion) {
            val migration = registry[version]
                ?: throw IllegalStateException(
                    "缺少 v$version → v${version + 1} 的迁移脚本；" +
                        "schema 变更必须补显式迁移，不允许删表重建",
                )
            migration.migrate(db)
            version++
        }
    }
}
