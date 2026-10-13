package com.nfctool.app.data.db

/**
 * 数据库 schema 定义。
 *
 * 约定：**任何 schema 变更都必须新增一个显式迁移**（见 [Migrations]），
 * 禁止用 drop / 重建表的方式"迁移"。理由是这个应用存的是用户卡片记录，
 * 删表即等于无声丢数据。
 */
object Schema {

    const val TABLE_HISTORY = "tag_history"
    const val TABLE_SETTINGS = "settings"

    /** v1 的建表语句。新增表 / 列请勿修改本列表，改为在 [Migrations] 中新增迁移。 */
    val CREATE_V1: List<String> = listOf(
        """
        CREATE TABLE $TABLE_HISTORY (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            uid_hex TEXT NOT NULL,
            technologies TEXT NOT NULL,
            record_kinds TEXT NOT NULL,
            record_count INTEGER NOT NULL,
            ndef_bytes INTEGER NOT NULL,
            created_at INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX idx_${TABLE_HISTORY}_created_at ON $TABLE_HISTORY(created_at DESC)",
        """
        CREATE TABLE $TABLE_SETTINGS (
            key TEXT PRIMARY KEY NOT NULL,
            value TEXT NOT NULL
        )
        """.trimIndent(),
    )

    /**
     * 迁移脚本中禁止出现的关键字。
     * 由单元测试逐条检查已注册的迁移，防止有人把"删表重建"当成迁移写进来。
     */
    val FORBIDDEN_MIGRATION_KEYWORDS: List<String> = listOf(
        "DROP TABLE",
        "DROP INDEX",
        "DELETE FROM",
        "TRUNCATE",
    )
}
