package com.nfctool.app.ui.screen

import com.nfctool.app.core.wifi.CurrentWifi
import com.nfctool.app.core.wifi.CurrentWifiResult

/** 读取当前 WiFi 失败后可点的补救动作。 */
enum class WifiRemedy {
    /** 去系统设置里手动授予权限（用户勾了「不再询问」时）。 */
    APP_SETTINGS,

    /** 打开定位服务开关（读 SSID 的前置条件）。 */
    LOCATION_SETTINGS,

    /** 补申请「位置信息」权限（部分 ROM 在 Android 13+ 上仍以定位为前置）。 */
    LOCATION_PERMISSION,
}

/** 读取结果对应的界面呈现：提示文案 + 可选的补救动作。 */
data class WifiReadPresentation(val hint: String, val remedy: WifiRemedy?)

/**
 * 把读取结果映射成界面呈现。
 *
 * 抽成纯函数是为了能单测：五种失败原因给用户指的下一步完全不同
 * （开权限 / 去设置 / 开定位 / 只能手输），说错了比不说更糟。
 *
 * 「是否被永久拒绝」需要 `Activity.shouldShowRequestPermissionRationale`，
 * 只能由界面侧判定后传入，因此不作为本函数的依赖。
 */
internal fun wifiReadPresentation(
    result: CurrentWifiResult,
    permanentlyDenied: Boolean,
): WifiReadPresentation = when (result) {
    is CurrentWifiResult.Success -> WifiReadPresentation(
        hint = "已填入当前连接的 WiFi「${result.ssid}」。密码仍需手动输入 —— " +
            "系统不向任何应用提供已保存网络的密码。",
        remedy = null,
    )

    is CurrentWifiResult.MissingPermission -> {
        val labels = result.permissions.joinToString("、") { CurrentWifi.permissionLabel(it) }
        if (permanentlyDenied) {
            WifiReadPresentation(
                hint = "需要 $labels 权限才能读取当前 WiFi 名称，但你之前选择了「不再询问」，" +
                    "系统不会再弹窗。请到设置里手动开启，或直接手动输入 SSID。",
                remedy = WifiRemedy.APP_SETTINGS,
            )
        } else {
            WifiReadPresentation(
                hint = "需要 $labels 权限才能读取当前 WiFi 名称。请再点一次按钮并在弹窗里允许，" +
                    "或手动输入 SSID。",
                remedy = null,
            )
        }
    }

    CurrentWifiResult.LocationDisabled -> WifiReadPresentation(
        hint = "已连接 WiFi，权限也已授予，但系统的定位服务是关闭的 —— " +
            "Android 8~12 必须开定位才允许读取 SSID，部分厂商 ROM（如 MIUI）在更高版本上同样如此。" +
            "开启后回来再点一次，或手动输入。",
        remedy = WifiRemedy.LOCATION_SETTINGS,
    )

    CurrentWifiResult.LocationPermissionNeeded -> WifiReadPresentation(
        hint = "本机在仅授予「附近设备」权限时不会给出 WiFi 名称 —— 部分厂商 ROM（如 MIUI）" +
            "仍要求「位置信息」权限，并且要开启定位服务。可点下方按钮补授权后重试，或手动输入 SSID。",
        remedy = WifiRemedy.LOCATION_PERMISSION,
    )

    CurrentWifiResult.UnknownSsid -> WifiReadPresentation(
        hint = "已连接 WiFi 且权限正常，但本机系统始终返回占位名称（部分厂商 ROM 的限制）。" +
            "请手动输入 SSID。",
        remedy = null,
    )

    is CurrentWifiResult.Failure -> WifiReadPresentation(
        hint = "读取 WiFi 状态时被系统拒绝：${result.reason}。" +
            "这通常是厂商 ROM 的限制，请手动输入 SSID。",
        remedy = null,
    )

    CurrentWifiResult.NotConnected -> WifiReadPresentation(
        hint = "当前没有连接任何 WiFi。请手动输入 SSID。",
        remedy = null,
    )
}
