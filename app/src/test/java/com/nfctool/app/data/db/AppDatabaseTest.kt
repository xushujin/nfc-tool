package com.nfctool.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.nfctool.app.core.bytes.Hex
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 数据库与访问对象的真实 SQL 测试。
 *
 * 用 Robolectric 在 JVM 上跑真实的 SQLite，因此 SQL 写错会在这里暴露，
 * 而不是等到运行时才发现 —— 这也是本项目选择手写 SQL 而不引 ORM 之后
 * 必须补上的验证环节。
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var historyDao: HistoryDao
    private lateinit var settingsDao: SettingsDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        database = AppDatabase(context)
        historyDao = HistoryDao(database)
        settingsDao = SettingsDao(database)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
    }

    @Test
    fun `首次打开即建好两张表且版本为当前版本`() {
        assertThat(database.writableDatabase.version).isEqualTo(AppDatabase.VERSION)
        assertThat(tableNames()).containsAtLeast(Schema.TABLE_HISTORY, Schema.TABLE_SETTINGS)
    }

    @Test
    fun `历史表可插入并按时间倒序读取`() {
        historyDao.insert(entry(uid = "AAAA", createdAt = 1000))
        historyDao.insert(entry(uid = "BBBB", createdAt = 3000))
        historyDao.insert(entry(uid = "CCCC", createdAt = 2000))

        val recent = historyDao.recent(10)
        assertThat(recent.map { it.uidHex }).containsExactly("BBBB", "CCCC", "AAAA").inOrder()
        assertThat(historyDao.count()).isEqualTo(3)
    }

    @Test
    fun `历史表按 limit 截断`() {
        for (index in 1..5) {
            historyDao.insert(entry(uid = "UID$index", createdAt = index * 100L))
        }
        val recent = historyDao.recent(2)
        assertThat(recent).hasSize(2)
        assertThat(recent.map { it.uidHex }).containsExactly("UID5", "UID4").inOrder()
    }

    @Test
    fun `历史记录字段完整往返`() {
        val id = historyDao.insert(
            HistoryEntry(
                uidHex = "4A1B2C3D",
                technologies = "NfcA / Ndef",
                recordKinds = "文本 + 网页链接",
                recordCount = 2,
                ndefBytes = 40,
                createdAtMillis = 1_700_000_000_000,
            ),
        )
        val loaded = historyDao.recent(1).single()
        assertThat(loaded.id).isEqualTo(id)
        assertThat(loaded.uidHex).isEqualTo("4A1B2C3D")
        assertThat(loaded.technologies).isEqualTo("NfcA / Ndef")
        assertThat(loaded.recordKinds).isEqualTo("文本 + 网页链接")
        assertThat(loaded.recordCount).isEqualTo(2)
        assertThat(loaded.ndefBytes).isEqualTo(40)
        assertThat(loaded.createdAtMillis).isEqualTo(1_700_000_000_000)
    }

    @Test
    fun `历史可单条删除与整体清空`() {
        val first = historyDao.insert(entry(uid = "AAAA", createdAt = 1000))
        historyDao.insert(entry(uid = "BBBB", createdAt = 2000))

        assertThat(historyDao.delete(first)).isEqualTo(1)
        assertThat(historyDao.count()).isEqualTo(1)

        assertThat(historyDao.clear()).isEqualTo(1)
        assertThat(historyDao.count()).isEqualTo(0)
        assertThat(historyDao.recent(10)).isEmpty()
    }

    @Test
    fun `设置表可写入与覆盖`() {
        assertThat(settingsDao.get("theme")).isNull()

        settingsDao.put("theme", "aurora")
        assertThat(settingsDao.get("theme")).isEqualTo("aurora")

        settingsDao.put("theme", "graphite")
        assertThat(settingsDao.get("theme")).isEqualTo("graphite")
        assertThat(settingsDao.all()).containsExactly("theme", "graphite")
    }

    @Test
    fun `设置表批量写入在一个事务内完成`() {
        settingsDao.putAll(mapOf("a" to "1", "b" to "2", "c" to "3"))
        assertThat(settingsDao.all()).containsExactly("a", "1", "b", "2", "c", "3")

        settingsDao.putAll(mapOf("b" to "22", "d" to "4"))
        assertThat(settingsDao.get("b")).isEqualTo("22")
        assertThat(settingsDao.get("d")).isEqualTo("4")
    }

    @Test
    fun `设置可删除`() {
        settingsDao.put("temp", "x")
        assertThat(settingsDao.remove("temp")).isEqualTo(1)
        assertThat(settingsDao.get("temp")).isNull()
        assertThat(settingsDao.remove("temp")).isEqualTo(0)
    }

    @Test
    fun `空 map 批量写入不报错`() {
        settingsDao.putAll(emptyMap())
        assertThat(settingsDao.all()).isEmpty()
    }

    @Test
    fun `关闭再打开时数据仍在且不触发迁移`() {
        settingsDao.put("theme", "mint")
        historyDao.insert(entry(uid = "AAAA", createdAt = 1000))
        database.close()

        val reopened = AppDatabase(context)
        try {
            assertThat(SettingsDao(reopened).get("theme")).isEqualTo("mint")
            assertThat(HistoryDao(reopened).count()).isEqualTo(1)
            assertThat(reopened.writableDatabase.version).isEqualTo(AppDatabase.VERSION)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun `limit 必须为正数`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { historyDao.recent(0) }
    }

    @Test
    fun `历史原文可按需存取`() {
        val ndef = byteArrayOf(0xD1.toByte(), 0x01, 0x0A, 0x55, 0x04, 0x68, 0x74, 0x74, 0x70, 0x3A)
        val dump = "NFC-TOOL-MIFARE-DUMP\nversion=1\nsize=ONE_K\n"
        val withContent = historyDao.insert(entry(uid = "AAAA", createdAt = 1000), ndef, dump)
        val without = historyDao.insert(entry(uid = "BBBB", createdAt = 2000))

        // 列表只带"有没有原文"的标记，不把 BLOB 拉出来
        val list = historyDao.recent(10)
        assertThat(list.first { it.id == withContent }.hasContent).isTrue()
        assertThat(list.first { it.id == without }.hasContent).isFalse()

        val loaded = historyDao.content(withContent)
        assertThat(loaded).isNotNull()
        assertThat(Hex.encode(loaded!!.ndefMessage!!)).isEqualTo(Hex.encode(ndef))
        assertThat(loaded.mifareDump).isEqualTo(dump)

        assertThat(historyDao.content(without)?.isEmpty).isTrue()
        assertThat(historyDao.content(9999)).isNull()
    }

    @Test
    fun `从 v1 升级到 v2 保留数据并补齐原文列`() {
        // 手工造一个 v1 库，再交给 AppDatabase 打开，触发 v1→v2 迁移
        database.close()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        val path = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
            legacy.version = 1
            for (statement in Schema.CREATE_V1) legacy.execSQL(statement)
            legacy.execSQL(
                "INSERT INTO ${Schema.TABLE_HISTORY} " +
                    "(uid_hex, technologies, record_kinds, record_count, ndef_bytes, created_at) " +
                    "VALUES ('AAAA', 'NfcA', '文本', 1, 16, 1000)",
            )
        }

        AppDatabase(context).use { upgraded ->
            assertThat(upgraded.writableDatabase.version).isEqualTo(AppDatabase.VERSION)
            val rows = HistoryDao(upgraded).recent(10)
            assertThat(rows).hasSize(1)
            assertThat(rows.single().uidHex).isEqualTo("AAAA")
            assertThat(rows.single().hasContent).isFalse()
        }
    }

    @Test
    fun `全新库的结构与迁移后的库一致`() {
        // 新装走 onCreate：必须也包含 v2 新增的原文列，否则运行期才会报"没有该列"
        assertThat(columnNames(Schema.TABLE_HISTORY)).containsAtLeast(
            HistoryDao.Column.NDEF_MESSAGE,
            HistoryDao.Column.MIFARE_DUMP,
        )
    }

    private fun columnNames(table: String): List<String> =
        database.readableDatabase.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private fun entry(uid: String, createdAt: Long) = HistoryEntry(
        uidHex = uid,
        technologies = "NfcA",
        recordKinds = "文本",
        recordCount = 1,
        ndefBytes = 16,
        createdAtMillis = createdAt,
    )

    private fun tableNames(): List<String> =
        database.readableDatabase.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
}
