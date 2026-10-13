package com.nfctool.app.ui.screen

import com.google.common.truth.Truth.assertThat
import com.nfctool.app.core.wifi.CurrentWifiResult
import org.junit.Test

/**
 * 读取当前 WiFi 的结果 → 界面呈现的映射。
 *
 * 锁的是「五种失败原因必须指向不同的下一步」：说错方向比不提示更糟。
 */
class WifiReadPresentationTest {

    @Test
    fun `成功时提示已填入并说明密码仍需手输`() {
        val presentation = wifiReadPresentation(
            CurrentWifiResult.Success("Home-5G"),
            permanentlyDenied = false,
        )
        assertThat(presentation.hint).contains("Home-5G")
        assertThat(presentation.hint).contains("密码仍需手动输入")
        assertThat(presentation.remedy).isNull()
    }

    @Test
    fun `权限未授予但还能再弹窗时不给去设置的动作`() {
        val presentation = wifiReadPresentation(
            CurrentWifiResult.MissingPermission(listOf("android.permission.NEARBY_WIFI_DEVICES")),
            permanentlyDenied = false,
        )
        assertThat(presentation.hint).contains("附近 Wi-Fi 设备")
        assertThat(presentation.remedy).isNull()
    }

    @Test
    fun `权限被永久拒绝时给出前往系统设置的动作`() {
        val presentation = wifiReadPresentation(
            CurrentWifiResult.MissingPermission(listOf("android.permission.ACCESS_FINE_LOCATION")),
            permanentlyDenied = true,
        )
        assertThat(presentation.hint).contains("不再询问")
        assertThat(presentation.remedy).isEqualTo(WifiRemedy.APP_SETTINGS)
    }

    @Test
    fun `定位服务关闭时给出打开定位设置的动作`() {
        val presentation = wifiReadPresentation(
            CurrentWifiResult.LocationDisabled,
            permanentlyDenied = false,
        )
        assertThat(presentation.remedy).isEqualTo(WifiRemedy.LOCATION_SETTINGS)
    }

    @Test
    fun `13 加仍需定位权限时给出补授权动作`() {
        val presentation = wifiReadPresentation(
            CurrentWifiResult.LocationPermissionNeeded,
            permanentlyDenied = false,
        )
        assertThat(presentation.hint).contains("位置信息")
        assertThat(presentation.remedy).isEqualTo(WifiRemedy.LOCATION_PERMISSION)
    }

    @Test
    fun `占位名称与未连接都只提示手输、不给动作`() {
        val placeholder = wifiReadPresentation(CurrentWifiResult.UnknownSsid, permanentlyDenied = false)
        val notConnected = wifiReadPresentation(CurrentWifiResult.NotConnected, permanentlyDenied = false)
        assertThat(placeholder.remedy).isNull()
        assertThat(placeholder.hint).contains("手动输入")
        assertThat(notConnected.remedy).isNull()
        assertThat(notConnected.hint).contains("没有连接任何 WiFi")
    }

    @Test
    fun `系统异常时把原因带进提示`() {
        val presentation = wifiReadPresentation(
            CurrentWifiResult.Failure("SecurityException"),
            permanentlyDenied = false,
        )
        assertThat(presentation.hint).contains("SecurityException")
        assertThat(presentation.remedy).isNull()
    }
}
