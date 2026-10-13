package com.nfctool.app.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/** 设置项键值表的访问对象。所有设置都以 `key -> value` 字符串形式存放。 */
class SettingsDao(private val helper: AppDatabase) {

    fun all(): Map<String, String> =
        helper.readableDatabase.rawQuery("SELECT $COLUMN_KEY, $COLUMN_VALUE FROM ${Schema.TABLE_SETTINGS}", null)
            .use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) {
                        put(cursor.getString(0), cursor.getString(1))
                    }
                }
            }

    fun get(key: String): String? =
        helper.readableDatabase.rawQuery(
            "SELECT $COLUMN_VALUE FROM ${Schema.TABLE_SETTINGS} WHERE $COLUMN_KEY = ?",
            arrayOf(key),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    fun put(key: String, value: String) {
        val values = ContentValues().apply {
            put(COLUMN_KEY, key)
            put(COLUMN_VALUE, value)
        }
        helper.writableDatabase.insertWithOnConflict(
            Schema.TABLE_SETTINGS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /** 批量写入，包在一个事务里，避免写一半留下不一致状态。 */
    fun putAll(entries: Map<String, String>) {
        if (entries.isEmpty()) return
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for ((key, value) in entries) {
                val values = ContentValues().apply {
                    put(COLUMN_KEY, key)
                    put(COLUMN_VALUE, value)
                }
                db.insertWithOnConflict(
                    Schema.TABLE_SETTINGS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun remove(key: String): Int =
        helper.writableDatabase.delete(Schema.TABLE_SETTINGS, "$COLUMN_KEY = ?", arrayOf(key))

    private companion object {
        const val COLUMN_KEY = "key"
        const val COLUMN_VALUE = "value"
    }
}
