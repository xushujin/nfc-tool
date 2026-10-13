package com.nfctool.app.data.db

import android.content.ContentValues
import android.database.Cursor

/**
 * 一条读卡历史（列表用的摘要）。
 *
 * 原文（NDEF 二进制 / MIFARE 转储）另存于同表的 `ndef_message` / `mifare_dump` 列，
 * 由 [HistoryDao.content] 按需读取 —— 列表查询不加载它们，避免一次拉出大量字节。
 * [hasContent] 表示这条记录是否保存了可再次写入的原文。
 */
data class HistoryEntry(
    val id: Long = 0L,
    val uidHex: String,
    val technologies: String,
    val recordKinds: String,
    val recordCount: Int,
    val ndefBytes: Int,
    val createdAtMillis: Long,
    val hasContent: Boolean = false,
)

/** 历史记录里保存的标签原文。两者都可为 null（该记录没有对应内容）。 */
class HistoryContent(
    val ndefMessage: ByteArray?,
    val mifareDump: String?,
) {
    val isEmpty: Boolean get() = ndefMessage == null && mifareDump == null
}

/** 读卡历史表的访问对象。 */
class HistoryDao(private val helper: AppDatabase) {

    fun insert(
        entry: HistoryEntry,
        ndefMessage: ByteArray? = null,
        mifareDump: String? = null,
    ): Long {
        val values = ContentValues().apply {
            put(Column.UID_HEX, entry.uidHex)
            put(Column.TECHNOLOGIES, entry.technologies)
            put(Column.RECORD_KINDS, entry.recordKinds)
            put(Column.RECORD_COUNT, entry.recordCount)
            put(Column.NDEF_BYTES, entry.ndefBytes)
            put(Column.CREATED_AT, entry.createdAtMillis)
            if (ndefMessage != null) put(Column.NDEF_MESSAGE, ndefMessage) else putNull(Column.NDEF_MESSAGE)
            if (mifareDump != null) put(Column.MIFARE_DUMP, mifareDump) else putNull(Column.MIFARE_DUMP)
        }
        return helper.writableDatabase.insertOrThrow(Schema.TABLE_HISTORY, null, values)
    }

    fun recent(limit: Int): List<HistoryEntry> {
        require(limit > 0) { "limit 必须为正数，实际 $limit" }
        // 显式列出摘要列（外加一个"有没有原文"的标记），不把原文 BLOB 拉进内存
        val sql = "SELECT ${Column.ID}, ${Column.UID_HEX}, ${Column.TECHNOLOGIES}, " +
            "${Column.RECORD_KINDS}, ${Column.RECORD_COUNT}, ${Column.NDEF_BYTES}, " +
            "${Column.CREATED_AT}, " +
            "CASE WHEN ${Column.NDEF_MESSAGE} IS NOT NULL OR ${Column.MIFARE_DUMP} IS NOT NULL " +
            "THEN 1 ELSE 0 END AS ${Column.HAS_CONTENT} " +
            "FROM ${Schema.TABLE_HISTORY} " +
            "ORDER BY ${Column.CREATED_AT} DESC, ${Column.ID} DESC LIMIT ?"
        return helper.readableDatabase.rawQuery(sql, arrayOf(limit.toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toEntry())
            }
        }
    }

    /** 读取某条历史保存的原文；记录不存在时返回 null。 */
    fun content(id: Long): HistoryContent? =
        helper.readableDatabase.rawQuery(
            "SELECT ${Column.NDEF_MESSAGE}, ${Column.MIFARE_DUMP} " +
                "FROM ${Schema.TABLE_HISTORY} WHERE ${Column.ID} = ?",
            arrayOf(id.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            HistoryContent(
                ndefMessage = if (cursor.isNull(0)) null else cursor.getBlob(0),
                mifareDump = if (cursor.isNull(1)) null else cursor.getString(1),
            )
        }

    fun count(): Int =
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${Schema.TABLE_HISTORY}",
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    fun delete(id: Long): Int =
        helper.writableDatabase.delete(Schema.TABLE_HISTORY, "${Column.ID} = ?", arrayOf(id.toString()))

    fun clear(): Int =
        helper.writableDatabase.delete(Schema.TABLE_HISTORY, null, null)

    private fun Cursor.toEntry(): HistoryEntry = HistoryEntry(
        id = getLong(getColumnIndexOrThrow(Column.ID)),
        uidHex = getString(getColumnIndexOrThrow(Column.UID_HEX)),
        technologies = getString(getColumnIndexOrThrow(Column.TECHNOLOGIES)),
        recordKinds = getString(getColumnIndexOrThrow(Column.RECORD_KINDS)),
        recordCount = getInt(getColumnIndexOrThrow(Column.RECORD_COUNT)),
        ndefBytes = getInt(getColumnIndexOrThrow(Column.NDEF_BYTES)),
        createdAtMillis = getLong(getColumnIndexOrThrow(Column.CREATED_AT)),
        hasContent = getInt(getColumnIndexOrThrow(Column.HAS_CONTENT)) != 0,
    )

    object Column {
        const val ID = "id"
        const val UID_HEX = "uid_hex"
        const val TECHNOLOGIES = "technologies"
        const val RECORD_KINDS = "record_kinds"
        const val RECORD_COUNT = "record_count"
        const val NDEF_BYTES = "ndef_bytes"
        const val CREATED_AT = "created_at"

        /** 标签原文：NDEF 消息的原始编码。 */
        const val NDEF_MESSAGE = "ndef_message"

        /** 标签原文：MIFARE 转储（自有文本格式）。 */
        const val MIFARE_DUMP = "mifare_dump"

        /** 查询别名，非真实列。 */
        const val HAS_CONTENT = "has_content"
    }
}
