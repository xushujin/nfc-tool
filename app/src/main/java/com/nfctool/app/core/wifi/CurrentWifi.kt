package com.nfctool.app.core.wifi

/** 读取手机当前连接 WiFi 的结果。 */
sealed interface CurrentWifiResult {

    /** 拿到 SSID。 */
    class Success(val ssid: String) : CurrentWifiResult

    /** 当前没有连接到任何 WiFi。 */
    data object NotConnected : CurrentWifiResult

    /** 权限未授予，系统拒绝给出 SSID。 */
    class MissingPermission(val permissions: List<String>) : CurrentWifiResult

    /**
     * 权限有了，但系统定位服务处于关闭状态。
     * Android 8~12 是硬性前置；部分厂商 ROM 在更高版本上同样如此。
     */
    data object LocationDisabled : CurrentWifiResult

    /**
     * Android 13+ 上「附近设备」已授予仍读不到，且本机未授予定位权限。
     * 部分厂商 ROM（如 MIUI）即便有了 `NEARBY_WIFI_DEVICES`，仍要求定位权限才给出 SSID。
     */
    data object LocationPermissionNeeded : CurrentWifiResult

    /** 权限与定位都正常，但系统仍返回占位值（多见于个别厂商 ROM）。 */
    data object UnknownSsid : CurrentWifiResult

    /** 读取过程中系统抛出异常（权限被拒、ROM 限制等）。绝不让异常冒到调用方。 */
    class Failure(val reason: String) : CurrentWifiResult
}

/**
 * 当前连接 WiFi 的 SSID 处理逻辑。
 *
 * 抽成纯函数是为了能单测：Android 在不同版本、不同权限状态下返回的
 * SSID 格式五花八门（带引号、占位符、空串），这些分支必须在 JVM 上验过。
 */
object CurrentWifi {

    /**
     * 系统在权限不足或未连接时返回的占位 SSID。
     * 见到这些一律按「未获取到」处理，不能当成真实名称写进标签。
     */
    private val PLACEHOLDERS = setOf(
        "unknown ssid",
        "<unknown ssid>",
        "ssid",
        "null",
        "0x",
        "",
    )

    /**
     * 把系统返回的原始 SSID 规整成可用的名称，拿不到时返回 null。
     *
     * 处理规则：
     * - 去掉 `WifiManager` 惯常加上的首尾双引号；
     * - 去掉首尾空白；
     * - 空串或已知占位符视为未获取到。
     */
    fun normalizeSsid(raw: String?): String? {
        if (raw == null) return null
        var text = raw.trim()
        if (text.length >= 2 && text.startsWith('"') && text.endsWith('"')) {
            text = text.substring(1, text.length - 1)
        }
        text = text.trim()
        if (text.isEmpty()) return null
        if (PLACEHOLDERS.any { it.equals(text, ignoreCase = true) }) return null
        return text
    }

    /** 按系统版本计算读取 SSID 需要申请的权限（主路径）。 */
    fun requiredPermissions(sdkInt: Int): Array<String> = when {
        // Android 13 起用 NEARBY_WIFI_DEVICES 即可，且可声明 neverForLocation 表示不用于定位
        sdkInt >= 33 -> arrayOf("android.permission.NEARBY_WIFI_DEVICES")
        // Android 8~12 必须拿精确位置权限，且要用户开着定位服务
        else -> arrayOf("android.permission.ACCESS_FINE_LOCATION")
    }

    /**
     * 兜底权限：主路径（Android 13+ 的「附近设备」）已授予却仍拿不到 SSID 时，
     * 部分厂商 ROM 还要求定位权限。这两个权限成对申请，由用户决定是否补授。
     */
    fun locationPermissions(): Array<String> = arrayOf(
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
    )

    /** 把权限名映射成用户看得懂的说法。 */
    fun permissionLabel(permission: String): String = when (permission) {
        "android.permission.NEARBY_WIFI_DEVICES" -> "「附近 Wi-Fi 设备」"
        "android.permission.ACCESS_FINE_LOCATION" -> "「位置信息」"
        else -> permission
    }
}
