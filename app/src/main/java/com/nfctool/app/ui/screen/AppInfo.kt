package com.nfctool.app.ui.screen

/**
 * 「关于」页展示的应用信息。
 *
 * 技术栈与支持范围集中在这里，README 中的对应章节与本节内容必须逐项一致；
 * 版本号来自 BuildConfig（由构建脚本注入），不在这里重复声明。
 */
object AppInfo {

    const val TECH_STACK_TITLE = "技术栈"

    val TECH_STACK: List<Pair<String, String>> = listOf(
        "语言" to "Kotlin 2.2.10（由 AGP 9.4.1 内置 Kotlin 支持提供）",
        "UI" to "Jetpack Compose + Material 3，拟态风格组件为自实现",
        "构建" to "Android Gradle Plugin 9.4.1 / Gradle 9.8.1",
        "最低系统" to "Android 8.0（API 26）",
        "编译与目标系统" to "API 37",
        "NFC 核心" to "自实现 NDEF 编解码（含分块记录）、WSC 属性 TLV 编码、MIFARE 扇区布局换算",
        "存储" to "平台自带 SQLite（SQLiteOpenHelper）+ 显式版本迁移，未使用 ORM",
        "依赖注入" to "无框架，手工装配（AppContainer）",
        "第三方库" to "仅 AndroidX 官方库；未引入任何第三方 UI 库或 DI 框架",
    )

    const val SUPPORT_TITLE = "支持范围"

    val SUPPORTED: List<Pair<String, String>> = listOf(
        "读取标签" to "NFC-A / B / F / V，读取 UID、技术类型、NDEF 记录与容量",
        "复制 NDEF" to "读取源卡 NDEF 记录并写入目标卡，适用于 NTAG 系列等标准标签",
        "写入 WiFi" to "按 Wi-Fi Simple Configuration 规范写入凭据，支持开放 / WPA / WPA2-Personal",
        "写入网页" to "URI 记录，支持 http / https / tel / mailto",
        "写入音频视频" to "写入网络地址（URI 记录）；小文件可内嵌为 MIME 记录",
        "MIFARE Classic" to "使用常见密钥逐扇区认证并读取转储，可回写数据块",
    )

    val LIMITATIONS: List<Pair<String, String>> = listOf(
        "UID 无法复制" to "厂商块（块 0）出厂只读，任何应用都无法改写，因此不存在 UID 级的完全克隆。",
        "扇区尾块不写入" to "尾块含访问位与密钥，写错会让扇区永久不可读，本应用一律跳过。",
        "加密卡片不支持" to "银行卡、部分门禁卡与公交卡使用加密认证或安全芯片，无法读取内容，本应用会明确报错。",
        "音视频容量" to "标准 NDEF 标签可用容量通常 144~888 字节，无法承载完整音视频，请改用网络地址。",
        "企业级 WiFi" to "WPA-Enterprise 需要账号与证书，本应用不支持写入。",
    )

    const val PERMISSION_TITLE = "权限说明"

    val PERMISSIONS: List<Triple<String, String, String>> = listOf(
        Triple(
            "android.permission.NFC",
            "普通权限",
            "用于在应用处于前台时读取与写入 NFC 标签。这是本应用的核心功能，缺此权限无法工作。",
        ),
        Triple(
            "android.permission.VIBRATE",
            "普通权限",
            "用于读卡成功时给出振动反馈，可在「设置」中关闭。不读取任何设备信息。",
        ),
        Triple(
            "android.permission.ACCESS_NETWORK_STATE",
            "普通权限（安装时自动获得）",
            "读取当前网络类型，用来区分「没连 WiFi」和「连了 WiFi 但读不到名称」两种情况。"
                + "不涉及位置信息，无需用户单独授权。",
        ),
        Triple(
            "android.permission.ACCESS_WIFI_STATE",
            "普通权限（安装时自动获得）",
            "读取当前连接的 WiFi 名称的兜底路径需要它。仅读取连接状态，"
                + "不扫描周边网络、不获取位置，无需用户单独授权。",
        ),
        Triple(
            "android.permission.NEARBY_WIFI_DEVICES",
            "运行时权限（Android 13+）",
            "读取「当前连接的 WiFi 名称」用到，已声明 neverForLocation，不用于定位。" +
                "拒绝也能手动输入 SSID，功能降级但不会不可用。",
        ),
        Triple(
            "android.permission.ACCESS_FINE_LOCATION",
            "运行时权限",
            "Android 8~12 上读取当前连接的 WiFi 名称必须用它，且需用户开启定位服务。" +
                "Android 13+ 主路径已改用 NEARBY_WIFI_DEVICES，但部分厂商 ROM（如 MIUI）仍以本权限" +
                "为前置；应用只在你主动点「授予位置信息权限」时才申请，不会默认索取。",
        ),
        Triple(
            "android.permission.ACCESS_COARSE_LOCATION",
            "运行时权限",
            "与 ACCESS_FINE_LOCATION 成对申请（Lint 要求：精确权限缺失时系统至少要能给出粗略值）。" +
                "同样只在补授权时申请。",
        ),
        Triple(
            "DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            "signature 级（AndroidX 构建期自动注入）",
            "由 androidx.core 在 manifest 合并阶段自动加入，供其内部的动态广播接收器使用。"
                + "它是 signature 级权限，只有本应用自身能持有，不向任何外部组件授予能力，"
                + "也不是本应用主动申请的权限。本应用并未编写任何广播接收器。",
        ),
    )

    const val WIFI_NOTE_TITLE = "关于 WiFi 密码"

    val WIFI_NOTES: List<Pair<String, String>> = listOf(
        "只能自动获取 SSID" to
            "点「使用当前连接的 WiFi」只会把网络名称填进表单，密码仍要手动输入。",
        "密码拿不到是系统安全边界" to
            "Android 不向任何第三方应用提供已保存 WiFi 的密码，这不是能力问题，也不受权限开关影响。",
        "也可能列不出网络列表" to
            "Android 10 起系统不再向普通应用提供「已保存网络」列表，因此只能读当前连接的那一个。",
    )

    const val PRIVACY_TITLE = "数据与隐私"

    val PRIVACY: List<Pair<String, String>> = listOf(
        "不联网" to "本应用未申请 INTERNET 权限，写入的网址只是被写进标签，应用自身从不发起网络请求。",
        "历史会保存标签原文" to
            "为支持「用历史记录再次写入新卡」，读卡历史会保存标签原文（NDEF 编码与 MIFARE 转储），" +
            "因此 WiFi 密码一类内容会落在本地数据库里。可在设置中关闭自动记录，或随时删除历史。",
        "禁止云备份" to "应用已关闭 allowBackup，数据不会随系统备份离开设备。",
        "剪贴板" to "复制的是十六进制文本，由用户主动触发。",
    )
}
