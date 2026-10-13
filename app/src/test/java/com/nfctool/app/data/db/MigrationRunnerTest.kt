package com.nfctool.app.data.db

import android.database.sqlite.SQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 迁移机制测试。
 *
 * 重点验证三件事：
 * 1. 缺迁移脚本时必须报错，而不是静默重建表（即禁止 destructive migration）；
 * 2. 迁移按版本逐级、按顺序执行；
 * 3. 已注册的迁移里不允许出现删表 / 删数据语句。
 */
@RunWith(RobolectricTestRunner::class)
class MigrationRunnerTest {

    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        db = SQLiteDatabase.create(null)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `版本相同则不做任何事`() {
        // 注册表里放一条会建表的迁移；版本相同时它不应被执行
        val registry = mapOf<Int, Migration>(
            1 to SqlMigration(1, 2, listOf("CREATE TABLE should_not_exist (id INTEGER)")),
        )
        MigrationRunner.run(db, fromVersion = 1, toVersion = 1, registry = registry)
        assertThat(tableExists("should_not_exist")).isFalse()
    }

    @Test
    fun `缺少中间迁移脚本时报错并说明原因`() {
        val registry = mapOf<Int, Migration>(
            1 to SqlMigration(1, 2, listOf("CREATE TABLE a (id INTEGER)")),
        )
        val error = assertThrows(IllegalStateException::class.java) {
            MigrationRunner.run(db, fromVersion = 1, toVersion = 3, registry = registry)
        }
        assertThat(error.message).contains("缺少 v2 → v3 的迁移脚本")
        assertThat(error.message).contains("不允许删表重建")
    }

    @Test
    fun `完全没有注册迁移时直接报错`() {
        val error = assertThrows(IllegalStateException::class.java) {
            MigrationRunner.run(db, fromVersion = 1, toVersion = 2, registry = emptyMap())
        }
        assertThat(error.message).contains("缺少 v1 → v2")
    }

    @Test
    fun `降级时报错`() {
        val error = assertThrows(IllegalStateException::class.java) {
            MigrationRunner.run(db, fromVersion = 3, toVersion = 2)
        }
        assertThat(error.message).contains("不支持数据库降级")
    }

    @Test
    fun `迁移按版本顺序执行`() {
        val registry = mapOf<Int, Migration>(
            1 to SqlMigration(
                fromVersion = 1,
                toVersion = 2,
                statements = listOf("CREATE TABLE stage (id INTEGER PRIMARY KEY, name TEXT)"),
            ),
            2 to SqlMigration(
                fromVersion = 2,
                toVersion = 3,
                statements = listOf("ALTER TABLE stage ADD COLUMN note TEXT NOT NULL DEFAULT ''"),
            ),
        )

        MigrationRunner.run(db, fromVersion = 1, toVersion = 3, registry = registry)

        assertThat(tableExists("stage")).isTrue()
        assertThat(columnExists("stage", "note")).isTrue()

        db.execSQL("INSERT INTO stage (id, name, note) VALUES (1, 'n', 'memo')")
        db.rawQuery("SELECT note FROM stage WHERE id = 1", null).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("memo")
        }
    }

    @Test
    fun `迁移必须逐版本推进`() {
        assertThrows(IllegalArgumentException::class.java) {
            SqlMigration(fromVersion = 1, toVersion = 3, statements = listOf("SELECT 1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SqlMigration(fromVersion = 1, toVersion = 2, statements = emptyList())
        }
    }

    @Test
    fun `注册表覆盖当前版本之前的每一步`() {
        for (version in 1 until AppDatabase.VERSION) {
            assertWithMessage("v$version → v${version + 1} 的迁移")
                .that(Migrations.forStep(version))
                .isNotNull()
        }
    }

    @Test
    fun `已注册的迁移不含破坏性语句`() {
        val forbidden = Schema.FORBIDDEN_MIGRATION_KEYWORDS
        for ((fromVersion, migration) in Migrations.ALL) {
            assertThat(migration.fromVersion).isEqualTo(fromVersion)
            assertThat(migration.toVersion).isEqualTo(fromVersion + 1)

            val statements = (migration as? SqlMigration)?.statements()
                ?: error("迁移 v$fromVersion 不是 SqlMigration，请补充对应的破坏性语句检查")
            for (statement in statements) {
                val upper = statement.uppercase()
                for (keyword in forbidden) {
                    assertWithMessage("迁移 v$fromVersion 的语句「$statement」")
                        .that(upper)
                        .doesNotContain(keyword)
                }
            }
        }
    }

    private fun tableExists(name: String): Boolean =
        db.rawQuery(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(name),
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }

    private fun columnExists(table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) return true
            }
            false
        }
}
