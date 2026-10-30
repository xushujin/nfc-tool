package com.nfctool.app.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nfctool.app.BuildConfig
import com.nfctool.app.core.media.MediaKind
import com.nfctool.app.core.media.MediaPlan
import com.nfctool.app.core.media.MediaPayloads
import com.nfctool.app.core.mifare.MifareDump
import com.nfctool.app.core.mifare.MifareDumpTextCodec
import com.nfctool.app.core.model.TagSnapshot
import com.nfctool.app.core.ndef.NdefCodec
import com.nfctool.app.core.ndef.NdefFormatException
import com.nfctool.app.core.ndef.NdefMessage
import com.nfctool.app.core.ndef.NdefRecords
import com.nfctool.app.core.update.AppVersion
import com.nfctool.app.core.update.ReleaseAsset
import com.nfctool.app.core.update.ReleaseInfo
import com.nfctool.app.core.update.UpdateCheck
import com.nfctool.app.core.wifi.CurrentWifi
import com.nfctool.app.core.wifi.CurrentWifiResult
import com.nfctool.app.core.wsc.WifiCredential
import com.nfctool.app.core.wsc.WscCodec
import com.nfctool.app.core.write.PackageNameValidator
import com.nfctool.app.data.AppContainer
import com.nfctool.app.data.AppSettings
import com.nfctool.app.data.HistoryRepository
import com.nfctool.app.data.SettingsRepository
import com.nfctool.app.data.db.HistoryEntry
import com.nfctool.app.data.update.DownloadResult
import com.nfctool.app.data.update.InstallResult
import com.nfctool.app.data.update.UpdateRepository
import com.nfctool.app.nfc.CurrentWifiReader
import com.nfctool.app.nfc.NfcTagReader
import com.nfctool.app.nfc.NfcTagWriter
import com.nfctool.app.nfc.WriteOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 提示级别。与界面组件的级别解耦，由界面负责映射。 */
enum class StatusLevel { INFO, SUCCESS, WARNING, ERROR }

/** 一条待展示的提示。 */
data class StatusMessage(val text: String, val level: StatusLevel)

/**
 * 「读取当前连接的 WiFi」这一步的界面状态。
 *
 * 放在 ViewModel 而不是界面的 `remember` 里：授权弹窗在某些 ROM 上会触发 Activity 重建，
 * 写在 `remember` 里的状态和**挂在界面作用域上的协程**会一起被丢弃，界面就表现为
 * "点了一下、布局跳了一下，然后什么都没有"。ViewModel 跨重建存活，异步结果才送得到。
 */
sealed interface WifiReadState {

    /** 还没发起过读取。 */
    data object Idle : WifiReadState

    /** 正在读取。 */
    data object Reading : WifiReadState

    /** 过程性提示（例如「请在系统弹窗中允许…」），给用户即时反馈。 */
    class Notice(val text: String) : WifiReadState

    /** 读取结果。措辞交给界面，因为「是否永久拒绝」需要 Activity 才能判定。 */
    class Result(val result: CurrentWifiResult) : WifiReadState
}

/**
 * 在线更新的界面状态。
 *
 * 「检查失败」「已是最新」这类一次性结果不在这里，而是走全局提示弹窗；
 * 这里只保留需要持续渲染的状态（检查中 / 发现新版本 / 下载中）。
 */
sealed interface UpdateUiState {

    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    class Available(val info: ReleaseInfo, val asset: ReleaseAsset) : UpdateUiState

    /**
     * @param info 下载过程中仍要展示版本与更新说明，因此随状态一起携带，
     *   否则进度弹窗会丢失标题与说明。
     */
    class Downloading(
        val info: ReleaseInfo,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateUiState {

        val fraction: Float
            get() = if (totalBytes <= 0L) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }
}

/** 等待标签靠近才能执行的操作。 */
sealed interface PendingOperation {

    val prompt: String

    class WriteNdef(val message: NdefMessage, override val prompt: String) : PendingOperation

    class RestoreMifare(val dump: MifareDump, override val prompt: String) : PendingOperation

    class MakeReadOnly(override val prompt: String) : PendingOperation
}

/** 界面状态。 */
data class NfcToolUiState(
    val nfcSupported: Boolean = true,
    val nfcEnabled: Boolean = true,
    val settings: AppSettings = AppSettings.DEFAULT,
    val snapshot: TagSnapshot? = null,
    val pending: PendingOperation? = null,
    val status: StatusMessage? = null,
    val busy: Boolean = false,
    val history: List<HistoryEntry> = emptyList(),
    val readMifareDump: Boolean = false,
    val wifiRead: WifiReadState = WifiReadState.Idle,
    val update: UpdateUiState = UpdateUiState.Idle,
) {

    /**
     * 读卡完成后更新状态。
     *
     * @param clearStatus 是否清空全局提示条。用户主动读卡时传 true；
     *   写卡之后的自动回读校验必须传 false —— 否则刚生成的「写入成功 / 失败」
     *   提示会被这次回读抹掉，用户什么结果都看不到（这正是复制功能"没反应"的根因）。
     */
    fun afterRead(snapshot: TagSnapshot?, clearStatus: Boolean): NfcToolUiState =
        if (clearStatus) {
            copy(snapshot = snapshot, busy = false, status = null)
        } else {
            copy(snapshot = snapshot, busy = false)
        }
}

/**
 * 应用唯一的 ViewModel。
 *
 * 读卡与写卡共用同一条"标签到达"入口：没有待执行操作时按读卡处理，
 * 有待执行操作时按写卡处理。这样用户的心智模型只有一句
 * 「贴上去」，不需要区分"现在是读模式还是写模式"。
 */
class NfcToolViewModel(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val updateRepository: UpdateRepository,
    /** 应用级 Context，供读取当前 WiFi 使用（不能用 Activity，否则随重建泄漏）。 */
    private val appContext: Context,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(NfcToolUiState())
    val uiState: StateFlow<NfcToolUiState> = mutableUiState.asStateFlow()

    /** 「等待用户贴卡」的看门狗任务，见 [startPendingTimeout]。 */
    private var pendingTimeoutJob: Job? = null

    /** 写入是否正在进行。用于丢弃感应区里对同一标签的重复分发。 */
    @Volatile
    private var writeInFlight = false

    /** 提示条自动消失的定时任务，见 [scheduleStatusDismiss]。 */
    private var statusDismissJob: Job? = null

    /**
     * 已排过消失计划的提示条实例。
     *
     * 用引用相等（`!==`）而不是值相等：连续两次操作可能产生**内容完全相同**的提示
     * （例如重复写入同一张卡），按值比较会认为"没变化"而不重新计时，
     * 结果第二条提示沿用上一条的倒计时提前消失。
     */
    private var scheduledStatus: StatusMessage? = null

    init {
        viewModelScope.launch {
            mutableUiState.collect { state ->
                val status = state.status
                if (status !== scheduledStatus) {
                    scheduledStatus = status
                    scheduleStatusDismiss(status)
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                mutableUiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            historyRepository.entries.collect { entries ->
                mutableUiState.update { it.copy(history = entries) }
            }
        }
        viewModelScope.launch { historyRepository.refresh() }
    }

    // ------------------------------------------------------------ NFC 可用性

    fun updateNfcAvailability(supported: Boolean, enabled: Boolean) {
        mutableUiState.update { it.copy(nfcSupported = supported, nfcEnabled = enabled) }
    }

    fun setReadMifareDump(enabled: Boolean) {
        mutableUiState.update { it.copy(readMifareDump = enabled) }
    }

    /** 手动关闭提示条（界面点击时调用）。 */
    fun dismissStatus() {
        mutableUiState.update { it.copy(status = null) }
    }

    /**
     * 提示条自动消失。
     *
     * 只靠手动关闭的话，提示会一直挂在屏幕上挡住内容 —— 读卡写卡是高频操作，
     * 每次都要点一下很烦。按级别给不同时长：结果类提示短，需要用户读完的
     * 警告与错误长一些。手动点击仍然可以立刻关掉。
     *
     * 注意这只管浮层提示条；「等待贴卡 / 正在写入」是 [NeuPendingCard] 的持久状态，
     * 不受这里的定时影响，该等多久还是等多久。
     */
    private fun scheduleStatusDismiss(status: StatusMessage?) {
        statusDismissJob?.cancel()
        statusDismissJob = null
        if (status == null) return

        val delayMillis = when (status.level) {
            StatusLevel.SUCCESS -> STATUS_DISMISS_SUCCESS_MILLIS
            StatusLevel.INFO -> STATUS_DISMISS_INFO_MILLIS
            StatusLevel.WARNING -> STATUS_DISMISS_WARNING_MILLIS
            StatusLevel.ERROR -> STATUS_DISMISS_ERROR_MILLIS
        }
        statusDismissJob = viewModelScope.launch {
            delay(delayMillis)
            // 只清掉当初排计划的那一条，避免误关掉期间新出现的提示
            mutableUiState.update { state ->
                if (state.status === status) state.copy(status = null) else state
            }
        }
    }

    // ------------------------------------------------------------ 标签到达

    /**
     * 由前台读卡回调驱动。写操作优先于读操作。
     *
     * 标签在感应区里停留时，系统可能连续多次分发同一个 Tag；写入期间必须丢弃
     * 重复分发，否则第二次会被当成"读卡"清掉刚写完的提示。
     */
    fun onTagDiscovered(tag: android.nfc.Tag) {
        if (writeInFlight) {
            Log.d(TAG, "写入进行中，忽略重复的标签分发")
            return
        }
        val pending = mutableUiState.value.pending
        if (pending != null) {
            executePending(tag, pending)
        } else {
            readTag(tag)
        }
    }

    /**
     * @param clearStatus 是否清空全局提示条。
     *   用户主动读卡时清空；写卡后自动回读校验时必须为 false ——
     *   否则刚设置好的「写入成功」提示会被这次回读抹掉，用户什么也看不到。
     */
    private fun readTag(tag: android.nfc.Tag, clearStatus: Boolean = true) {
        val readDump = mutableUiState.value.readMifareDump
        val snapshot = NfcTagReader.snapshot(tag, readMifareDump = readDump)
        mutableUiState.update { state ->
            val next = state.afterRead(snapshot, clearStatus)
            // 用户主动读卡时给一条结果摘要（统一弹窗展示）；
            // 写卡后的自动回读必须保留刚写入的结果，不能被这次摘要覆盖。
            if (clearStatus) next.copy(status = readSummary(snapshot)) else next
        }
        viewModelScope.launch {
            historyRepository.record(snapshot)
        }
    }

    /** 把一次读卡结果压成一条可弹窗展示的摘要。 */
    private fun readSummary(snapshot: TagSnapshot): StatusMessage {
        val uid = snapshot.uidHex.ifEmpty { "无 UID" }
        val tech = snapshot.technologies.joinToString(" / ").ifEmpty { "未知技术" }
        val records = snapshot.ndef?.message?.records?.size ?: 0
        val mifare = snapshot.mifareClassic
            ?.let { "，MIFARE ${it.size.label.substringBefore("（")}" }
            .orEmpty()
        val warning = if (snapshot.warnings.isEmpty()) "" else "，另有 ${snapshot.warnings.size} 条提示"
        return StatusMessage(
            text = "已读到标签\nUID：$uid\n技术：$tech\nNDEF 记录：$records 条$mifare$warning",
            level = if (snapshot.warnings.isEmpty()) StatusLevel.SUCCESS else StatusLevel.WARNING,
        )
    }

    /**
     * 执行待写操作。
     *
     * NFC 读写是阻塞 IO，放在 [Dispatchers.IO] 上做：
     * 一是界面能真的把 "正在写入" 状态渲染出来（同步跑在主线程时中间态根本来不及绘制），
     * 二是避免主线程被 IO 卡住导致平台判定应用无响应、进而断开标签连接。
     */
    private fun executePending(tag: android.nfc.Tag, pending: PendingOperation) {
        writeInFlight = true
        mutableUiState.update { it.copy(busy = true) }
        Log.d(TAG, "开始执行待写操作：$pending")

        viewModelScope.launch(Dispatchers.IO) {
            // 兜底捕获：写入链路上任何未预期的异常（例如编码阶段抛出的
            // NdefFormatException 是 IllegalArgumentException 子类，不被 NfcTagWriter
            // 的 IOException 捕获）都必须把 busy/pending 复位，否则界面会永远停在
            // 「复制中」，用户既看不到成功也看不到失败。
            val outcome = try {
                when (pending) {
                    is PendingOperation.WriteNdef -> NfcTagWriter.writeNdef(tag, pending.message)
                    is PendingOperation.RestoreMifare -> NfcTagWriter.restoreMifare(tag, pending.dump)
                    is PendingOperation.MakeReadOnly -> NfcTagWriter.makeReadOnly(tag)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "执行待写操作时抛出未预期异常", e)
                WriteOutcome.Failure(
                    "写入过程异常中断：${e.javaClass.simpleName} — ${e.message ?: "无详细信息"}。请重试。",
                )
            } finally {
                cancelPendingTimeout()
            }

            Log.d(TAG, "写入结果：$outcome")
            mutableUiState.update {
                it.copy(pending = null, busy = false, status = outcome.toStatusMessage())
            }
            // 写完后顺手读一次，让界面立刻反映卡片当前内容。
            // 这里必须保留刚写入的提示，否则成功/失败提示会被回读清空。
            try {
                if (outcome is WriteOutcome.Success || outcome is WriteOutcome.Partial) {
                    readTag(tag, clearStatus = false)
                }
            } finally {
                // 必须放在 finally：回读本身也可能抛异常，
                // 一旦漏掉这里的复位，后续所有标签分发都会被"写入进行中"挡掉，变成新的卡死。
                writeInFlight = false
            }
        }
    }

    private fun WriteOutcome.toStatusMessage(): StatusMessage = when (this) {
        is WriteOutcome.Success -> StatusMessage(detail, StatusLevel.SUCCESS)
        is WriteOutcome.Partial -> StatusMessage(
            text = detail + if (failures.isEmpty()) "" else "；未完成：" + failures.joinToString("；"),
            level = StatusLevel.WARNING,
        )

        is WriteOutcome.Failure -> StatusMessage(reason, StatusLevel.ERROR)
    }

    // ------------------------------------------------------------ 等待标签超时

    /**
     * 用户点了写入但迟迟不贴卡时主动收尾。
     * 没有这个保护的话，界面会无限期停在"等待标签"，用户无法区分
     * 是应用没响应还是自己在等一个物理动作。
     */
    private fun startPendingTimeout() {
        cancelPendingTimeout()
        pendingTimeoutJob = viewModelScope.launch {
            delay(PENDING_TIMEOUT_MILLIS)
            val stillPending = mutableUiState.value.pending != null
            if (stillPending) {
                mutableUiState.update {
                    it.copy(
                        pending = null,
                        busy = false,
                        status = StatusMessage(
                            "等待标签超时，本次写入已取消。请重新点击写入按钮后再贴卡。",
                            StatusLevel.WARNING,
                        ),
                    )
                }
            }
        }
    }

    private fun cancelPendingTimeout() {
        pendingTimeoutJob?.cancel()
        pendingTimeoutJob = null
    }

    private fun beginPending(operation: PendingOperation, info: String) {
        cancelPendingTimeout()
        mutableUiState.update {
            it.copy(
                pending = operation,
                busy = false,
                status = StatusMessage("已准备写入：$info。请将目标标签贴近手机背面。", StatusLevel.INFO),
            )
        }
        startPendingTimeout()
    }

    // ------------------------------------------------------------ 写操作入口

    fun requestWriteNdef(message: NdefMessage, description: String) {
        beginPending(
            operation = PendingOperation.WriteNdef(message, "请将目标标签贴近手机背面"),
            info = description,
        )
    }

    /** 按 WSC 规范把 WiFi 凭据编码成一条 MIME 记录并准备写入。 */
    fun requestWifiWrite(credential: WifiCredential) {
        val record = try {
            WscCodec.buildRecord(credential)
        } catch (e: NdefFormatException) {
            mutableUiState.update {
                it.copy(status = StatusMessage(e.message ?: "WiFi 凭据不合法", StatusLevel.ERROR))
            }
            return
        }
        requestWriteNdef(
            message = NdefMessage(listOf(record)),
            description = "WiFi「${credential.ssid}」（${credential.authType.label} / " +
                "${credential.encryptionType.label}）",
        )
    }

    /** 把当前快照的 NDEF 内容复制到另一张卡。 */
    fun requestCopyCurrentNdef() {
        val snapshot = mutableUiState.value.snapshot
        val message = snapshot?.ndef?.message
        if (message == null) {
            mutableUiState.update {
                it.copy(status = StatusMessage("当前没有可复制的 NDEF 内容，请先读一张卡片。", StatusLevel.WARNING))
            }
            return
        }
        requestWriteNdef(message, "复制 ${message.records.size} 条 NDEF 记录")
    }

    /** 把当前快照的 MIFARE 整卡转储回写到另一张卡。 */
    fun requestCopyCurrentMifare() {
        val info = mutableUiState.value.snapshot?.mifareClassic
        val dump = info?.dump
        if (dump == null) {
            val reason = when {
                info == null -> "当前快照不包含 MIFARE Classic 数据，请先在读卡页开启「读取 MIFARE 转储」。"
                else -> "转储不完整（扇区 ${info.unreadableSectors.joinToString("、")} 密钥未知），无法整卡复制。"
            }
            mutableUiState.update { it.copy(status = StatusMessage(reason, StatusLevel.WARNING)) }
            return
        }
        beginPending(
            operation = PendingOperation.RestoreMifare(dump, "请将目标卡片贴近手机背面"),
            info = "回写 ${dump.size.label} 转储（${dump.restorableBlocks().size} 个数据块）。" +
                "扇区尾块与厂商块不会写入",
        )
    }

    fun requestMakeReadOnly() {
        beginPending(
            operation = PendingOperation.MakeReadOnly("请将标签贴近手机背面以锁定"),
            info = "将标签永久锁定为只读（不可撤销）",
        )
    }

    fun cancelPending() {
        cancelPendingTimeout()
        val had = mutableUiState.value.pending != null
        mutableUiState.update {
            it.copy(
                pending = null,
                busy = false,
                status = if (had) StatusMessage("已取消待执行的写入操作。", StatusLevel.INFO) else it.status,
            )
        }
    }

    // ------------------------------------------------------------ 媒体写入

    /** 写入纯文本（NDEF Text 记录）。 */
    fun requestWriteText(text: String, language: String) {
        val record = try {
            NdefRecords.text(text, language)
        } catch (e: IllegalArgumentException) {
            mutableUiState.update {
                it.copy(status = StatusMessage(e.message ?: "文本内容不合法", StatusLevel.ERROR))
            }
            return
        }
        requestWriteNdef(
            message = NdefMessage(listOf(record)),
            description = "文本 ${text.length} 字符（$language）",
        )
    }

    /** 写入图片（MIME 记录内嵌文件字节）。 */
    fun requestWriteImage(kind: MediaKind, fileName: String, mimeType: String, bytes: ByteArray) {
        requestMediaWrite(
            MediaPayloads.embeddedMedia(
                kind = kind,
                fileName = fileName,
                mimeType = mimeType,
                bytes = bytes,
                capacityBytes = null,
            ),
        )
    }

    /**
     * 写入 Android 应用记录（AAR）：读取该标签时手机会尝试唤起指定应用。
     *
     * 注意 AAR 只在「目标应用已安装」时能拉起；未安装时能否跳应用市场
     * 取决于对方机型的处理，本应用无法保证。
     */
    fun requestWriteApp(packageName: String) {
        val error = PackageNameValidator.problem(packageName)
        if (error != null) {
            mutableUiState.update { it.copy(status = StatusMessage(error, StatusLevel.ERROR)) }
            return
        }
        requestWriteNdef(
            message = NdefMessage(listOf(NdefRecords.androidApplication(packageName))),
            description = "唤起应用 $packageName",
        )
    }

    fun requestMediaWrite(plan: MediaPlan) {
        when (plan) {
            is MediaPlan.Ready -> requestWriteNdef(plan.message, plan.description)
            is MediaPlan.TooLarge -> mutableUiState.update {
                it.copy(
                    status = StatusMessage(
                        "内容需要 ${plan.requiredBytes} 字节，标签仅能容纳 ${plan.capacityBytes} 字节。",
                        StatusLevel.ERROR,
                    ),
                )
            }

            is MediaPlan.Invalid -> mutableUiState.update {
                it.copy(status = StatusMessage(plan.reason, StatusLevel.ERROR))
            }
        }
    }

    // ------------------------------------------------------------ 读取当前 WiFi

    /**
     * 读取当前连接的 WiFi，结果写回 [NfcToolUiState.wifiRead]。
     *
     * 协程挂在 [viewModelScope] 上，而不是界面作用域：授权弹窗触发 Activity 重建时，
     * 界面作用域的协程会被取消，结果就永远到不了界面（"点了没反应"）。
     * viewModelScope 跨重建存活，读到的结果一定送得到。
     */
    fun startWifiRead() {
        mutableUiState.update { it.copy(wifiRead = WifiReadState.Reading) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                CurrentWifiReader.read(appContext)
            } catch (e: Throwable) {
                // read() 内部已兜异常，这里再兜一层：绝不让异常冒到协程外顶掉应用。
                CurrentWifiResult.Failure(
                    "${e.javaClass.simpleName}" + (e.message?.let { " — $it" } ?: ""),
                )
            }
            withContext(Dispatchers.Main) {
                mutableUiState.update { it.copy(wifiRead = WifiReadState.Result(result)) }
            }
        }
    }

    /** 设置读取前的过程性提示（授权弹窗阶段），保证点一下就立刻有可见反馈。 */
    fun setWifiReadNotice(text: String) {
        mutableUiState.update { it.copy(wifiRead = WifiReadState.Notice(text)) }
    }

    // ------------------------------------------------------------ 在线更新

    /**
     * 检查更新。
     *
     * 只在用户主动点击时触发，不做启动自动检查：本应用的定位是离线工具，
     * 不该在用户没要求的时候联网。检查结果里「已是最新 / 失败 / 已跳过」
     * 走全局提示弹窗，只有「发现新版本」会停留在 [UpdateUiState.Available] 上渲染更新弹窗。
     */
    fun checkForUpdate() {
        if (mutableUiState.value.update is UpdateUiState.Checking) return
        mutableUiState.update { it.copy(update = UpdateUiState.Checking) }
        viewModelScope.launch {
            val current = AppVersion.parse(BuildConfig.VERSION_NAME)
            if (current == null) {
                finishUpdateWithStatus(
                    "无法解析当前版本号「${BuildConfig.VERSION_NAME}」，已取消检查。",
                    StatusLevel.ERROR,
                )
                return@launch
            }
            when (val result = updateRepository.check(current)) {
                is UpdateCheck.Available -> mutableUiState.update {
                    // 同时清掉可能残留的结果提示，避免两个弹窗叠在一起
                    it.copy(update = UpdateUiState.Available(result.info, result.asset), status = null)
                }

                is UpdateCheck.UpToDate -> finishUpdateWithStatus(
                    if (result.skipped) {
                        "已跳过 ${result.latest}，如需更新请在设置里取消跳过。"
                    } else {
                        "已是最新版本（$current）。"
                    },
                    StatusLevel.SUCCESS,
                )

                is UpdateCheck.Failed -> finishUpdateWithStatus(result.reason, StatusLevel.ERROR)
            }
        }
    }

    /** 下载当前发现的新版本，下载完成后直接拉起系统安装器。 */
    fun downloadUpdate() {
        val available = mutableUiState.value.update as? UpdateUiState.Available ?: return
        mutableUiState.update {
            it.copy(update = UpdateUiState.Downloading(available.info, 0L, available.asset.sizeBytes))
        }
        viewModelScope.launch {
            val result = updateRepository.download(available.asset) { downloaded, total ->
                mutableUiState.update { state ->
                    if (state.update is UpdateUiState.Downloading) {
                        state.copy(update = UpdateUiState.Downloading(available.info, downloaded, total))
                    } else {
                        state
                    }
                }
            }
            when (result) {
                is DownloadResult.Success -> {
                    mutableUiState.update { it.copy(update = UpdateUiState.Idle) }
                    when (val install = updateRepository.install(result.file)) {
                        is InstallResult.Launched -> showStatus(
                            "安装包已下载，请在系统安装界面确认安装。",
                            StatusLevel.SUCCESS,
                        )

                        is InstallResult.NeedsPermission -> showStatus(
                            "需要先允许本应用安装未知来源的应用。已在系统设置中打开开关，" +
                                "开启后请重新点「检查更新」。",
                            StatusLevel.WARNING,
                        )

                        is InstallResult.Failed -> showStatus(install.reason, StatusLevel.ERROR)
                    }
                }

                is DownloadResult.Failed -> finishUpdateWithStatus(result.reason, StatusLevel.ERROR)
            }
        }
    }

    /** 跳过当前提示的版本，之后检查到它不再提示。 */
    fun skipUpdate() {
        val available = mutableUiState.value.update as? UpdateUiState.Available ?: return
        viewModelScope.launch {
            updateRepository.skip(available.info.version)
            mutableUiState.update { it.copy(update = UpdateUiState.Idle) }
            showStatus(
                "已跳过 ${available.info.version}，之后检查到该版本不再提示。可在设置里取消跳过。",
                StatusLevel.INFO,
            )
        }
    }

    /** 取消跳过，让下一次检查重新提示该版本。 */
    fun clearSkippedUpdate() {
        viewModelScope.launch {
            updateRepository.clearSkipped()
            showStatus("已取消跳过，下次检查会重新提示新版本。", StatusLevel.INFO)
        }
    }

    fun dismissUpdate() {
        mutableUiState.update { it.copy(update = UpdateUiState.Idle) }
    }

    private fun finishUpdateWithStatus(text: String, level: StatusLevel) {
        mutableUiState.update { it.copy(update = UpdateUiState.Idle, status = StatusMessage(text, level)) }
    }

    private fun showStatus(text: String, level: StatusLevel) {
        mutableUiState.update { it.copy(status = StatusMessage(text, level)) }
    }

    // ------------------------------------------------------------ 设置与历史

    fun setThemeId(themeId: String) {
        viewModelScope.launch { settingsRepository.setThemeId(themeId) }
    }

    fun setVibrateOnScan(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVibrateOnScan(enabled) }
    }

    fun setAutoSaveHistory(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoSaveHistory(enabled) }
    }

    fun setNdefLanguage(language: String) {
        viewModelScope.launch { settingsRepository.setNdefLanguage(language) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clear()
            mutableUiState.update {
                it.copy(status = StatusMessage("读卡历史已清空。", StatusLevel.SUCCESS))
            }
        }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch { historyRepository.delete(id) }
    }

    /**
     * 用某条历史记录「再次写入新卡」。
     *
     * 优先用保存的 NDEF 原文（NTAG 等标准标签）；没有 NDEF 时退回 MIFARE 转储 ——
     * 门禁卡一类通常没有 NDEF，只能走整卡回写。
     */
    fun useHistoryEntry(id: Long) {
        viewModelScope.launch {
            val content = historyRepository.loadContent(id)
            if (content == null || content.isEmpty) {
                mutableUiState.update {
                    it.copy(status = StatusMessage("这条记录没有保存可写回的内容。", StatusLevel.WARNING))
                }
                return@launch
            }

            val ndefBytes = content.ndefMessage
            if (ndefBytes != null) {
                val message = try {
                    NdefCodec.decode(ndefBytes)
                } catch (e: NdefFormatException) {
                    mutableUiState.update {
                        it.copy(
                            status = StatusMessage(
                                "历史内容已损坏，无法解析：${e.message ?: "格式非法"}",
                                StatusLevel.ERROR,
                            ),
                        )
                    }
                    return@launch
                }
                requestWriteNdef(message, "历史记录（${message.records.size} 条 NDEF 记录）")
                return@launch
            }

            val dumpText = content.mifareDump
            if (dumpText != null) {
                val dump = try {
                    MifareDumpTextCodec.decode(dumpText)
                } catch (e: IllegalArgumentException) {
                    mutableUiState.update {
                        it.copy(
                            status = StatusMessage(
                                "历史转储已损坏，无法解析：${e.message ?: "格式非法"}",
                                StatusLevel.ERROR,
                            ),
                        )
                    }
                    return@launch
                }
                beginPending(
                    operation = PendingOperation.RestoreMifare(dump, "请将目标卡片贴近手机背面"),
                    info = "历史记录里的 MIFARE 转储（${dump.restorableBlocks().size} 个数据块）",
                )
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NfcToolViewModel::class.java)) {
                "本工厂只能创建 NfcToolViewModel，收到 $modelClass"
            }
            return NfcToolViewModel(
                settingsRepository = container.settingsRepository,
                historyRepository = container.historyRepository,
                updateRepository = container.updateRepository,
                appContext = container.appContext,
            ) as T
        }
    }

    private companion object {
        const val TAG = "NfcToolViewModel"

        /** 等待用户贴卡的最长时间：超时后自动取消并提示，避免界面无限期停在写入态。 */
        const val PENDING_TIMEOUT_MILLIS = 60_000L

        // 提示条自动消失时长。中文一行二三十字读完约需 3~4 秒，据此定档：
        /** 操作结果，看一眼即可。 */
        const val STATUS_DISMISS_SUCCESS_MILLIS = 3_000L

        /** 过程性说明，读完就够。 */
        const val STATUS_DISMISS_INFO_MILLIS = 3_000L

        /** 需要用户留意（部分成功、超时取消），留足阅读时间。 */
        const val STATUS_DISMISS_WARNING_MILLIS = 6_000L

        /** 失败原因里有具体字节数与处理建议，需要时间读。 */
        const val STATUS_DISMISS_ERROR_MILLIS = 8_000L
    }
}
