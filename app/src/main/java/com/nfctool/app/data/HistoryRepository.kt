package com.nfctool.app.data

import com.nfctool.app.core.mifare.MifareDumpTextCodec
import com.nfctool.app.core.model.RecordSummaries
import com.nfctool.app.core.model.TagSnapshot
import com.nfctool.app.core.ndef.NdefCodec
import com.nfctool.app.core.ndef.NdefFormatException
import com.nfctool.app.data.db.HistoryContent
import com.nfctool.app.data.db.HistoryDao
import com.nfctool.app.data.db.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 读卡历史。
 *
 * 摘要（UID / 技术类型 / 记录类别 / 记录条数 / NDEF 字节数 / 时间）用于列表展示；
 * 同时保存标签原文（NDEF 编码 / MIFARE 转储），使历史记录可以「再次写入新卡」。
 * 代价是 WiFi 密码一类内容会落到本地库，隐私说明已同步更新。
 */
class HistoryRepository(
    private val dao: HistoryDao,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) {

    private val mutableEntries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = mutableEntries.asStateFlow()

    suspend fun refresh(limit: Int = DEFAULT_LIMIT) {
        val rows = withContext(Dispatchers.IO) { dao.recent(limit) }
        mutableEntries.value = rows
    }

    /**
     * 记录一次读卡。
     *
     * @return 实际写入时返回新记录 id；因「自动记录」开关关闭而跳过时返回 null。
     */
    suspend fun record(snapshot: TagSnapshot): Long? {
        if (!settingsRepository.settings.value.autoSaveHistory) return null
        val summaries = snapshot.ndef?.message?.let { RecordSummaries.of(it) }.orEmpty()
        val entry = HistoryEntry(
            uidHex = snapshot.uidHex,
            technologies = snapshot.technologies.joinToString(" / "),
            recordKinds = if (summaries.isEmpty()) "无 NDEF 数据" else RecordSummaries.kindsLabel(summaries),
            recordCount = summaries.size,
            ndefBytes = snapshot.ndef?.message?.encodedSize ?: 0,
            createdAtMillis = timeProvider(),
        )
        // 原文按需保存：NDEF 存原始编码，MIFARE 存自有文本格式。
        // 编码失败（极少数非规范记录）只是不能「再次写入」，不影响摘要记录本身。
        val ndefMessage = snapshot.ndef?.message?.let { message ->
            try {
                NdefCodec.encode(message)
            } catch (_: NdefFormatException) {
                null
            }
        }
        val mifareDump = snapshot.mifareClassic?.dump?.let(MifareDumpTextCodec::encodeNative)
        val id = withContext(Dispatchers.IO) { dao.insert(entry, ndefMessage, mifareDump) }
        refresh()
        return id
    }

    /** 读取某条历史保存的原文，用于「再次写入新卡」。 */
    suspend fun loadContent(id: Long): HistoryContent? =
        withContext(Dispatchers.IO) { dao.content(id) }

    suspend fun delete(id: Long) {
        withContext(Dispatchers.IO) { dao.delete(id) }
        refresh()
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) { dao.clear() }
        refresh()
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) { dao.count() }

    companion object {
        const val DEFAULT_LIMIT = 200
    }
}
