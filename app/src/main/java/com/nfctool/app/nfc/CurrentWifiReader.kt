package com.nfctool.app.nfc

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nfctool.app.core.wifi.CurrentWifi
import com.nfctool.app.core.wifi.CurrentWifiResult

/**
 * 读取手机当前连接的 WiFi SSID。
 *
 * 三条路径依次尝试，第一条拿到就算成功：
 * 1. `ConnectivityManager` 当前网络的 `TransportInfo`（Android 12+ 首选）；
 * 2. `WifiManager.connectionInfo`（全版本兜底，API 31 起废弃但仍可用）；
 * 3. 两条都拿不到时，按权限 / 定位开关 / 厂商 ROM 给出**具体**原因。
 *
 * 之前只按版本二选一，结果是某些机型上首选路径被系统脱敏返回
 * `<unknown ssid>`，另一条明明能拿到却根本没被调用 —— 所以改成全部都试。
 *
 * 注意：没有任何 API 能读取已保存网络的**密码**，那是系统安全边界，
 * 因此这里只填 SSID，密码仍由用户输入。
 */
object CurrentWifiReader {

    /**
     * 读取当前 WiFi 的 SSID。
     *
     * 整个方法包在 try/catch 里：**读 WiFi 状态会碰系统服务，不同厂商 ROM 上
     * 可能抛 SecurityException、NullPointerException 等各种未声明异常**。
     * 这类异常绝不能冒到调用方 —— 一个"读取当前 WiFi"的辅助功能把应用顶掉
     * 是完全不可接受的降级幅度，宁可返回一个可展示的失败原因。
     */
    fun read(context: Context): CurrentWifiResult = try {
        readUnsafe(context)
    } catch (e: SecurityException) {
        CurrentWifiResult.Failure("系统拒绝读取 WiFi 状态（${e.message ?: "权限不足"}）")
    } catch (e: Throwable) {
        CurrentWifiResult.Failure("读取 WiFi 状态时出错：${e.javaClass.simpleName}")
    }

    private fun readUnsafe(context: Context): CurrentWifiResult {
        if (!isWifiConnected(context)) return CurrentWifiResult.NotConnected

        // 两条路径各自兜异常：第一条失败不能连累第二条，否则又退化成"二选一"
        val ssid = readViaConnectivityManager(context)
            ?: readViaWifiManager(context)
        if (ssid != null) return CurrentWifiResult.Success(ssid)

        val required = CurrentWifi.requiredPermissions(Build.VERSION.SDK_INT)
        return when {
            !hasPermissions(context, required) ->
                CurrentWifiResult.MissingPermission(required.toList())

            // 定位服务关闭：Android 8~12 是硬性前置；部分 ROM 在 13+ 上同样如此
            !isLocationEnabled(context) -> CurrentWifiResult.LocationDisabled

            // Android 13+：主权限已授予仍读不到，且没给定位权限
            // —— 多半是该 ROM 仍以定位为前置（MIUI 一类），引导用户补授权
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasPermissions(context, CurrentWifi.locationPermissions()) ->
                CurrentWifiResult.LocationPermissionNeeded

            else -> CurrentWifiResult.UnknownSsid
        }
    }

    /** Android 12+：不走 WifiManager，从当前网络的 TransportInfo 里取。 */
    private fun readViaConnectivityManager(context: Context): String? = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            null
        } else {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
                ?: return null
            val network = connectivity.activeNetwork ?: return null
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
            val info = capabilities.transportInfo as? WifiInfo ?: return null
            CurrentWifi.normalizeSsid(info.ssid)
        }
    } catch (_: Throwable) {
        null
    }

    /**
     * 兜底路径：直接问 `WifiManager`。
     *
     * `getConnectionInfo()` 自 API 31 起废弃，但它在部分机型上仍返回真实 SSID
     * （尤其当上面那条路径被脱敏时），因此全版本都试一次，不按版本门控掉。
     */
    @Suppress("DEPRECATION")
    private fun readViaWifiManager(context: Context): String? = try {
        val wifiManager = context.getSystemService(WifiManager::class.java) ?: return null
        CurrentWifi.normalizeSsid(wifiManager.connectionInfo?.ssid)
    } catch (_: Throwable) {
        // 该调用需要 ACCESS_WIFI_STATE，个别 ROM 还会在无权限时直接抛异常。
        // 这条只是兜底路径，拿不到就交给下一条，不影响主路径。
        null
    }

    /** 判断当前网络是不是 WiFi —— 用来区分「没连 WiFi」和「连了但读不到」。 */
    private fun isWifiConnected(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /** Android 8~12 要求定位服务开启，否则 SSID 一律被脱敏。 */
    private fun isLocationEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        val manager = context.getSystemService(LocationManager::class.java) ?: return true
        return manager.isLocationEnabled
    }

    /**
     * 权限是否被永久拒绝（用户勾了「不再询问」）。
     *
     * 判定依赖 `Activity.shouldShowRequestPermissionRationale`；拿不到 Activity
     * （例如 Context 被包装过）时保守返回 false，宁可让用户再试一次弹窗，
     * 也不要错误地把他推去设置页。
     */
    fun isPermanentlyDenied(context: Context, permissions: List<String>): Boolean {
        val activity = context.findActivity() ?: return false
        return permissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED &&
                !activity.shouldShowRequestPermissionRationale(it)
        }
    }

    /** 从可能被包装过的 Context 里找回 Activity。 */
    private fun Context.findActivity(): android.app.Activity? {
        var current: Context? = this
        while (current != null) {
            if (current is android.app.Activity) return current
            current = (current as? android.content.ContextWrapper)?.baseContext
        }
        return null
    }

    /** 系统设置里本应用的详情页，用于手动授权。 */
    fun appSettingsIntent(context: Context) = android.content.Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.fromParts("package", context.packageName, null),
    )

    /** 定位服务开关页（Android 8~12 需要用户自己打开）。 */
    fun locationSettingsIntent() = android.content.Intent(
        android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS,
    )

    /**
     * 安全跳转：部分 ROM 没有对应设置页，直接 startActivity 会抛
     * ActivityNotFoundException 把应用顶掉。
     */
    fun safeStartActivity(context: Context, intent: android.content.Intent): Boolean = try {
        context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Throwable) {
        false
    }

}
