package com.nfctool.app.ui.screen

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nfctool.app.core.media.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nfctool.app.core.media.MediaPayloads
import com.nfctool.app.core.media.MediaPlan
import com.nfctool.app.core.ndef.NdefRecords
import com.nfctool.app.core.wifi.CurrentWifi
import com.nfctool.app.core.wifi.CurrentWifiResult
import com.nfctool.app.core.wsc.WifiCredential
import com.nfctool.app.core.wsc.WifiCredentialValidator
import com.nfctool.app.core.wsc.WscAuthType
import com.nfctool.app.core.wsc.WscEncryptionType
import com.nfctool.app.core.write.PackageNameValidator
import com.nfctool.app.ui.InstalledApp
import com.nfctool.app.ui.InstalledAppsLoader
import com.nfctool.app.ui.MediaFileLoader
import com.nfctool.app.ui.NfcToolUiState
import com.nfctool.app.ui.WifiReadState
import com.nfctool.app.nfc.CurrentWifiReader
import com.nfctool.app.ui.component.NeuBanner
import com.nfctool.app.ui.component.NeuBannerLevel
import com.nfctool.app.ui.component.NeuButton
import com.nfctool.app.ui.component.NeuButtonContent
import com.nfctool.app.ui.component.NeuChipRow
import com.nfctool.app.ui.component.NeuIconKind
import com.nfctool.app.ui.component.NeuKeyValueRow
import com.nfctool.app.ui.component.NeuPendingCard
import com.nfctool.app.ui.component.NeuScreen
import com.nfctool.app.ui.component.NeuSectionTitle
import com.nfctool.app.ui.component.NeuSegmented
import com.nfctool.app.ui.component.NeuSurface
import com.nfctool.app.ui.component.NeuTextField
import com.nfctool.app.ui.theme.NeuDimens
import com.nfctool.app.ui.theme.NeuTheme

private val WRITE_TABS = listOf("文本", "图片", "WiFi", "网页", "音频", "视频", "应用")

/**
 * 写入页：文本、图片、WiFi、网页、音频、视频、拉起应用七类内容。
 *
 * 界面只负责收集输入与展示校验结果，实际编码与容量校验都在核心层完成。
 */
@Composable
fun WriteScreen(
    state: NfcToolUiState,
    onWriteWifi: (WifiCredential) -> Unit,
    onWriteText: (String, String) -> Unit,
    onWriteImage: (MediaKind, String, String, ByteArray) -> Unit,
    onWriteApp: (String) -> Unit,
    onWriteMedia: (MediaPlan) -> Unit,
    onStartWifiRead: () -> Unit,
    onWifiNotice: (String) -> Unit,
    onCancelPending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tabIndex by remember { mutableIntStateOf(0) }

    NeuScreen(title = "写入", modifier = modifier) {
        if (state.pending != null) {
            NeuPendingCard(
                prompt = state.pending.prompt,
                busy = state.busy,
                onCancel = onCancelPending,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(NeuDimens.gapLarge))
        }

        NeuChipRow(
            options = WRITE_TABS,
            selectedIndex = tabIndex,
            onSelect = { tabIndex = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(NeuDimens.gapMedium))

        when (tabIndex) {
            0 -> TextForm(language = state.settings.ndefLanguage, onWriteText = onWriteText)
            1 -> ImageForm(onWriteImage = onWriteImage)
            2 -> WifiForm(
                state = state,
                onWriteWifi = onWriteWifi,
                onStartWifiRead = onStartWifiRead,
                onWifiNotice = onWifiNotice,
            )
            3 -> MediaForm(kind = MediaKind.WEB, language = state.settings.ndefLanguage, onWriteMedia = onWriteMedia)
            4 -> MediaForm(kind = MediaKind.AUDIO, language = state.settings.ndefLanguage, onWriteMedia = onWriteMedia)
            5 -> MediaForm(kind = MediaKind.VIDEO, language = state.settings.ndefLanguage, onWriteMedia = onWriteMedia)
            else -> AppForm(onWriteApp = onWriteApp)
        }

        Spacer(modifier = Modifier.height(NeuDimens.gapLarge))
    }
}

// --------------------------------------------------------------------- WiFi

@Composable
private fun WifiForm(
    state: NfcToolUiState,
    onWriteWifi: (WifiCredential) -> Unit,
    onStartWifiRead: () -> Unit,
    onWifiNotice: (String) -> Unit,
) {
    val palette = NeuTheme.palette
    val context = LocalContext.current
    // 表单输入用 rememberSaveable：授权弹窗可能触发 Activity 重建，用户输入不能丢。
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var authIndex by rememberSaveable { mutableIntStateOf(1) }

    val authType = when (authIndex) {
        0 -> WscAuthType.OPEN
        1 -> WscAuthType.WPA2_PERSONAL
        else -> WscAuthType.WPA_PERSONAL
    }
    val encryptionType = when (authType) {
        WscAuthType.OPEN -> WscEncryptionType.NONE
        WscAuthType.WPA2_PERSONAL -> WscEncryptionType.AES
        else -> WscEncryptionType.TKIP
    }

    val problems = WifiCredentialValidator.validate(
        ssid = ssid,
        networkKey = if (authType == WscAuthType.OPEN) "" else password,
        authType = authType,
        encryptionType = encryptionType,
    )

    // 读取状态放在 ViewModel（跨 Activity 重建存活），这里只负责把它渲染成提示。
    // 「是否永久拒绝」需要 Activity 才能判定，所以在界面侧算好再传给纯函数。
    val read = state.wifiRead
    val presentation: WifiReadPresentation? = when (read) {
        WifiReadState.Idle -> null
        WifiReadState.Reading -> WifiReadPresentation("正在读取当前连接的 WiFi…", null)
        is WifiReadState.Notice -> WifiReadPresentation(read.text, null)
        is WifiReadState.Result -> {
            val permanentlyDenied = (read.result as? CurrentWifiResult.MissingPermission)
                ?.let { CurrentWifiReader.isPermanentlyDenied(context, it.permissions) }
                ?: false
            wifiReadPresentation(read.result, permanentlyDenied)
        }
    }

    // 读成功后回填 SSID 输入框
    LaunchedEffect(read) {
        val current = read
        if (current is WifiReadState.Result && current.result is CurrentWifiResult.Success) {
            ssid = current.result.ssid
        }
    }

    /** 权限申请回调：无论授予与否都读一次，让用户看到结果而不是没反应 */
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { onStartWifiRead() }

    /**
     * 点按钮后的入口：查权限 → 直接开读，或先申请权限。
     *
     * 真正的读取在 ViewModel 里跑（见 `NfcToolViewModel.startWifiRead`）：
     * 授权弹窗触发 Activity 重建时，挂在界面上的协程会被取消、结果送不到；
     * viewModelScope 跨重建存活，读到的结果一定回得来。这里整体包 try/catch 兜底。
     */
    fun requestCurrentWifi() {
        try {
            val permissions = CurrentWifi.requiredPermissions(Build.VERSION.SDK_INT)
            val granted = permissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
            }
            if (granted) {
                onStartWifiRead()
            } else {
                // 弹窗一出现就先落一条提示，保证任何一次点击都立刻有可见反馈。
                onWifiNotice(
                    "请在系统弹窗中允许" +
                        permissions.joinToString("、") { CurrentWifi.permissionLabel(it) } +
                        "权限，允许后会自动读取当前 WiFi。",
                )
                permissionLauncher.launch(permissions)
            }
        } catch (e: Throwable) {
            onWifiNotice(
                "启动 WiFi 读取失败：${e.javaClass.simpleName}" +
                    (e.message?.let { " — $it" } ?: "") + "。请手动输入 SSID。",
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(NeuDimens.gapMedium)) {
        NeuButton(onClick = { requestCurrentWifi() }, modifier = Modifier.fillMaxWidth()) {
            NeuButtonContent(icon = NeuIconKind.Wifi, text = "使用当前连接的 WiFi")
        }

        presentation?.let { (hint, remedy) ->
            NeuBanner(
                text = hint,
                level = NeuBannerLevel.INFO,
                modifier = Modifier.fillMaxWidth(),
            )
            remedy?.let { kind ->
                NeuButton(
                    onClick = {
                        when (kind) {
                            WifiRemedy.APP_SETTINGS -> {
                                if (!CurrentWifiReader.safeStartActivity(
                                        context,
                                        CurrentWifiReader.appSettingsIntent(context),
                                    )
                                ) {
                                    onWifiNotice(
                                        "无法打开系统设置页，请在「设置 → 应用 → NFC工具 → 权限」中手动开启。",
                                    )
                                }
                            }

                            WifiRemedy.LOCATION_SETTINGS -> {
                                if (!CurrentWifiReader.safeStartActivity(
                                        context,
                                        CurrentWifiReader.locationSettingsIntent(),
                                    )
                                ) {
                                    onWifiNotice("无法打开定位设置页，请在系统设置中手动开启定位服务后重试。")
                                }
                            }

                            WifiRemedy.LOCATION_PERMISSION -> {
                                // 补申请定位权限；无论授予与否，回调里都会再读一次
                                permissionLauncher.launch(CurrentWifi.locationPermissions())
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    NeuButtonContent(
                        icon = NeuIconKind.Settings,
                        text = when (kind) {
                            WifiRemedy.APP_SETTINGS -> "去系统设置授权"
                            WifiRemedy.LOCATION_SETTINGS -> "打开定位设置"
                            WifiRemedy.LOCATION_PERMISSION -> "授予位置信息权限"
                        },
                    )
                }
            }
        }

        NeuTextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = "WiFi 名称（SSID）",
            placeholder = "例如 Home-5G",
            supportingText = "最长 32 字节",
            modifier = Modifier.fillMaxWidth(),
        )

        NeuSegmented(
            options = listOf("开放", "WPA2", "WPA"),
            selectedIndex = authIndex,
            onSelect = { authIndex = it },
            modifier = Modifier.fillMaxWidth(),
        )

        if (authType != WscAuthType.OPEN) {
            NeuTextField(
                value = password,
                onValueChange = { password = it },
                label = "WiFi 密码",
                placeholder = "8~63 个字符",
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        NeuSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                NeuKeyValueRow("认证方式", authType.label)
                NeuKeyValueRow("加密方式", encryptionType.label)
                NeuKeyValueRow("写入记录", "MIME ${NdefRecords.MIME_WIFI_WSC}")
            }
        }

        if (problems.isNotEmpty()) {
            NeuBanner(
                text = problems.joinToString("；"),
                level = NeuBannerLevel.WARNING,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        NeuButton(
            onClick = {
                onWriteWifi(
                    WifiCredential(
                        ssid = ssid,
                        networkKey = if (authType == WscAuthType.OPEN) "" else password,
                        authType = authType,
                        encryptionType = encryptionType,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            primary = true,
            enabled = problems.isEmpty(),
        ) {
            NeuButtonContent(icon = NeuIconKind.Wifi, text = "写入 WiFi 标签")
        }

        Text(
            text = "写入后，支持 WSC 的 Android 手机会在贴卡时提示连接该 WiFi。"
                + "密码会以明文形式存进标签，请确认标签由你本人保管。",
            color = palette.onBackgroundMuted,
            fontSize = 12.sp,
        )
    }
}

// --------------------------------------------------------------- 网页 / 媒体

@Composable
private fun MediaForm(
    kind: MediaKind,
    language: String,
    onWriteMedia: (MediaPlan) -> Unit,
) {
    val palette = NeuTheme.palette
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var sourceIndex by remember { mutableIntStateOf(0) }
    var pickedName by remember { mutableStateOf<String?>(null) }
    var pickedMime by remember { mutableStateOf<String?>(null) }
    var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pickError by remember { mutableStateOf<String?>(null) }

    val urlOnly = kind == MediaKind.WEB
    val useLocalFile = !urlOnly && sourceIndex == 1

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val result = MediaFileLoader.load(context, uri)) {
            is MediaFileLoader.Result.Success -> {
                pickedName = result.file.displayName
                pickedMime = result.file.mimeType
                pickedBytes = result.file.bytes
                pickError = null
            }

            is MediaFileLoader.Result.Failure -> {
                pickError = result.reason
                pickedName = null
                pickedMime = null
                pickedBytes = null
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(NeuDimens.gapMedium)) {
        if (!urlOnly) {
            NeuSegmented(
                options = listOf("网络地址", "本地文件"),
                selectedIndex = sourceIndex,
                onSelect = {
                    sourceIndex = it
                    pickError = null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (urlOnly || !useLocalFile) {
            NeuTextField(
                value = url,
                onValueChange = { url = it },
                label = if (urlOnly) "网页地址" else "${kind.label}的网络地址",
                placeholder = "例如 https://example.com",
                keyboardType = KeyboardType.Uri,
                supportingText = "没有写协议头时会自动补 https://",
                modifier = Modifier.fillMaxWidth(),
            )
            NeuTextField(
                value = title,
                onValueChange = { title = it },
                label = "标题（可选）",
                placeholder = "会作为一条文本记录一并写入",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (useLocalFile) {
            NeuButton(
                onClick = { picker.launch(arrayOf("${kind.name.lowercase()}/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                NeuButtonContent(icon = NeuIconKind.Media, text = "选择${kind.label}文件")
            }
            pickError?.let {
                NeuBanner(text = it, level = NeuBannerLevel.ERROR, modifier = Modifier.fillMaxWidth())
            }
            val name = pickedName
            val mime = pickedMime
            val bytes = pickedBytes
            if (name != null && mime != null && bytes != null) {
                NeuSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        NeuKeyValueRow("文件名", name)
                        NeuKeyValueRow("MIME 类型", mime)
                        NeuKeyValueRow("文件大小", "${bytes.size} 字节")
                    }
                }
                NeuBanner(
                    text = "标准 NDEF 标签可用容量通常只有 144~888 字节，内嵌文件几乎一定写不进去。"
                        + "若目标标签容量不足，写入时会给出明确提示，此时请改用「网络地址」方式。",
                    level = NeuBannerLevel.WARNING,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        NeuButton(
            onClick = {
                val plan = when {
                    useLocalFile -> {
                        val name = pickedName
                        val mime = pickedMime
                        val bytes = pickedBytes
                        if (name == null || mime == null || bytes == null) {
                            MediaPlan.Invalid("请先选择一个${kind.label}文件")
                        } else {
                            MediaPayloads.embeddedMedia(
                                kind = kind,
                                fileName = name,
                                mimeType = mime,
                                bytes = bytes,
                                capacityBytes = null,
                                languageCode = language,
                            )
                        }
                    }

                    urlOnly -> MediaPayloads.webLink(url, title, language)
                    else -> MediaPayloads.remoteMedia(url, kind, title, language)
                }
                onWriteMedia(plan)
            },
            modifier = Modifier.fillMaxWidth(),
            primary = true,
        ) {
            NeuButtonContent(icon = NeuIconKind.Media, text = "写入${kind.label}标签")
        }

        NeuSectionTitle(text = "写入形式")
        NeuSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                NeuKeyValueRow(
                    "网页",
                    "URI 记录（TNF 1，类型 U），任何设备都能打开，可移植性最好。",
                )
                NeuKeyValueRow(
                    "${kind.label}网络地址",
                    "URI 记录 + 可选标题文本记录，指向网络上的媒体文件。",
                )
                NeuKeyValueRow(
                    "${kind.label}本地文件",
                    "MIME 记录内嵌文件字节，受标签容量限制，适合极小的片段。",
                )
            }
        }

        Text(
            text = "NDEF 标签容量上限通常在 1 KB 以内，无法承载完整的音视频文件。"
                + "本应用不会截断数据：放不下就直接报错并给出所需与可用字节数。",
            color = palette.onBackgroundMuted,
            fontSize = 12.sp,
        )
    }
}

// --------------------------------------------------------------------- 文本

/** 写入纯文本（NDEF Text 记录）。 */
@Composable
private fun TextForm(
    language: String,
    onWriteText: (String, String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var languageDraft by remember(language) { mutableStateOf(language) }

    val problems = buildList {
        if (text.isEmpty()) add("文本内容不能为空")
        if (text.toByteArray(Charsets.UTF_8).size > 1024) {
            add("文本转换为 UTF-8 后有 ${text.toByteArray(Charsets.UTF_8).size} 字节，超过 1024 字节上限")
        }
        if (languageDraft.isBlank() || languageDraft.length > 63) {
            add("语言代码长度必须在 1..63 之间，当前 ${languageDraft.length}")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(NeuDimens.gapMedium)) {
        NeuTextField(
            value = text,
            onValueChange = { text = it },
            label = "文本内容",
            placeholder = "贴卡后手机会显示这段文字",
            singleLine = false,
            minLines = 3,
            supportingText = "写入为 NDEF Text 记录（TNF 1，类型 T）",
            modifier = Modifier.fillMaxWidth(),
        )

        NeuTextField(
            value = languageDraft,
            onValueChange = { languageDraft = it },
            label = "语言代码",
            placeholder = "zh 或 en",
            supportingText = "取 RFC 5646 语言标签，可在「设置」里改默认值",
            isError = languageDraft.isBlank() || languageDraft.length > 63,
            modifier = Modifier.fillMaxWidth(),
        )

        if (problems.isNotEmpty()) {
            NeuBanner(
                text = problems.joinToString("；"),
                level = NeuBannerLevel.WARNING,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        NeuButton(
            onClick = { onWriteText(text, languageDraft) },
            modifier = Modifier.fillMaxWidth(),
            primary = true,
            enabled = problems.isEmpty(),
        ) {
            NeuButtonContent(icon = NeuIconKind.Info, text = "写入文本标签")
        }
    }
}

// --------------------------------------------------------------------- 图片

/** 写入图片（MIME 记录内嵌文件字节）。 */
@Composable
private fun ImageForm(
    onWriteImage: (MediaKind, String, String, ByteArray) -> Unit,
) {
    val palette = NeuTheme.palette
    val context = LocalContext.current
    var fileName by remember { mutableStateOf<String?>(null) }
    var mimeType by remember { mutableStateOf<String?>(null) }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var pickError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val result = MediaFileLoader.load(context, uri)) {
            is MediaFileLoader.Result.Success -> {
                fileName = result.file.displayName
                // 有些取回的 mimeType 是 application/octet-stream，按后缀再兜一次底
                mimeType = result.file.mimeType
                    .takeIf { it.isNotBlank() && it != "application/octet-stream" }
                    ?: MediaPayloads.mimeTypeForFileName(result.file.displayName)
                bytes = result.file.bytes
                pickError = null
            }

            is MediaFileLoader.Result.Failure -> {
                pickError = result.reason
                fileName = null; mimeType = null; bytes = null
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(NeuDimens.gapMedium)) {
        NeuButton(
            onClick = { picker.launch(arrayOf("image/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            NeuButtonContent(icon = NeuIconKind.Media, text = "选择图片")
        }

        pickError?.let {
            NeuBanner(text = it, level = NeuBannerLevel.ERROR, modifier = Modifier.fillMaxWidth())
        }

        val name = fileName
        val mime = mimeType
        val data = bytes
        if (name != null && mime != null && data != null) {
            NeuSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    NeuKeyValueRow("文件名", name)
                    NeuKeyValueRow("MIME 类型", mime)
                    NeuKeyValueRow("文件大小", "${data.size} 字节")
                }
            }
            NeuBanner(
                text = "标准 NDEF 标签可用容量通常只有 144~888 字节，整张照片几乎一定写不进去。" +
                    "若标签容量不足，写入时会给出所需与可用字节数，此时请换更小的图。",
                level = NeuBannerLevel.WARNING,
                modifier = Modifier.fillMaxWidth(),
            )
            NeuButton(
                onClick = { onWriteImage(MediaKind.IMAGE, name, mime, data) },
                modifier = Modifier.fillMaxWidth(),
                primary = true,
            ) {
                NeuButtonContent(icon = NeuIconKind.Media, text = "写入图片标签")
            }
        }

        Text(
            text = "图片以 MIME 记录内嵌写入，贴卡后手机通常会在通知栏给出「打开」提示，" +
                "能否直接预览取决于对方机型对图片 MIME 记录的处理。",
            color = palette.onBackgroundMuted,
            fontSize = 12.sp,
        )
    }
}

// --------------------------------------------------------------------- 拉起应用

/** 写入 Android 应用记录（AAR）：贴卡时唤起指定应用。 */
@Composable
private fun AppForm(
    onWriteApp: (String) -> Unit,
) {
    val palette = NeuTheme.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var packageName by rememberSaveable { mutableStateOf("") }
    // null 表示还没加载过；加载一次后缓存，重复打开不再查
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val problem = PackageNameValidator.problem(packageName)

    fun openPicker() {
        if (apps != null) {
            showPicker = true
            return
        }
        loading = true
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { InstalledAppsLoader.load(context) }
            apps = loaded
            loading = false
            showPicker = true
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(NeuDimens.gapMedium)) {
        NeuTextField(
            value = packageName,
            onValueChange = { packageName = it },
            label = "应用包名",
            placeholder = "例如 com.android.chrome",
            supportingText = "也可以点下面的按钮，从已安装应用里挑",
            isError = problem != null,
            modifier = Modifier.fillMaxWidth(),
        )

        NeuButton(
            onClick = { openPicker() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
        ) {
            NeuButtonContent(
                icon = NeuIconKind.Settings,
                text = if (loading) "正在读取应用列表…" else "从已安装应用中选择",
            )
        }

        if (problem != null && packageName.isNotBlank()) {
            NeuBanner(
                text = problem,
                level = NeuBannerLevel.WARNING,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        NeuButton(
            onClick = { onWriteApp(packageName) },
            modifier = Modifier.fillMaxWidth(),
            primary = true,
            enabled = problem == null,
        ) {
            NeuButtonContent(icon = NeuIconKind.Settings, text = "写入应用唤起标签")
        }

        NeuSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                NeuKeyValueRow("写入记录", "外部类型 android.com:pkg（Android 应用记录）")
                NeuKeyValueRow(
                    "效果",
                    "贴卡时，若指定应用已安装则直接唤起；未安装时的行为由读卡方机型决定，" +
                        "可能是跳应用市场，也可能无反应。",
                )
            }
        }

        Text(
            text = "包名写错不会有任何提示，手机会静静地什么都不做，所以写入前请确认包名。" +
                "列表里只出现有启动图标的应用，其它组件可手动输入包名。",
            color = palette.onBackgroundMuted,
            fontSize = 12.sp,
        )
    }

    if (showPicker) {
        AppPickerDialog(
            apps = apps.orEmpty(),
            onSelect = {
                packageName = it.packageName
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}
