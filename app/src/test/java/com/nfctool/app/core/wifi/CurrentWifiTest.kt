package com.nfctool.app.core.wifi

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class CurrentWifiTest {

    @Test
    fun `去掉 WifiManager 惯常加上的双引号`() {
        assertThat(CurrentWifi.normalizeSsid("\"Home-5G\"")).isEqualTo("Home-5G")
        assertThat(CurrentWifi.normalizeSsid("\"Guest\"")).isEqualTo("Guest")
    }

    @Test
    fun `去掉首尾空白`() {
        assertThat(CurrentWifi.normalizeSsid("  Home-5G  ")).isEqualTo("Home-5G")
        assertThat(CurrentWifi.normalizeSsid("\"  Home-5G  \"")).isEqualTo("Home-5G")
    }

    @Test
    fun `null 与空串视为未获取到`() {
        assertThat(CurrentWifi.normalizeSsid(null)).isNull()
        assertThat(CurrentWifi.normalizeSsid("")).isNull()
        assertThat(CurrentWifi.normalizeSsid("   ")).isNull()
        assertThat(CurrentWifi.normalizeSsid("\"\"")).isNull()
    }

    @Test
    fun `系统占位符一律视为未获取到`() {
        // 权限不足时各版本系统返回的占位串大小写不一，必须都覆盖
        for (placeholder in listOf("unknown ssid", "Unknown SSID", "<unknown ssid>", "ssid", "0x")) {
            assertWithMessage("占位符「$placeholder」应被识别")
                .that(CurrentWifi.normalizeSsid(placeholder))
                .isNull()
        }
    }

    @Test
    fun `只有一个引号时不当成引号包裹`() {
        // 只去掉成对的首尾引号，避免把畸形输入越剪越短
        assertThat(CurrentWifi.normalizeSsid("\"Home")).isEqualTo("\"Home")
    }

    @Test
    fun `单个字符不应被当成引号包裹而清空`() {
        assertThat(CurrentWifi.normalizeSsid("\"")).isEqualTo("\"")
    }

    @Test
    fun `按系统版本选择要申请的权限`() {
        val old = CurrentWifi.requiredPermissions(31)
        assertThat(old).asList().containsExactly("android.permission.ACCESS_FINE_LOCATION")

        val modern = CurrentWifi.requiredPermissions(33)
        assertThat(modern).asList().containsExactly("android.permission.NEARBY_WIFI_DEVICES")
    }

    @Test
    fun `权限列表只有一项，不需要重复申请`() {
        for (sdk in 26..35) {
            assertThat(CurrentWifi.requiredPermissions(sdk)).hasLength(1)
        }
    }
}
