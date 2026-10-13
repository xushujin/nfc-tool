package com.nfctool.app.core.wsc

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class WifiCredentialValidatorTest {

    @Test
    fun `合法的 WPA2 凭据没有问题`() {
        val problems = WifiCredentialValidator.validate(
            ssid = "MyNetwork",
            networkKey = "12345678",
            authType = WscAuthType.WPA2_PERSONAL,
            encryptionType = WscEncryptionType.AES,
        )
        assertThat(problems).isEmpty()
    }

    @Test
    fun `SSID 不能为空且不超过 32 字节`() {
        assertThat(
            WifiCredentialValidator.validate("", "12345678", WscAuthType.WPA2_PERSONAL, WscEncryptionType.AES),
        ).contains("WiFi 名称不能为空")

        val tooLong = "a".repeat(33)
        assertThat(
            WifiCredentialValidator.validate(tooLong, "12345678", WscAuthType.WPA2_PERSONAL, WscEncryptionType.AES),
        ).contains("WiFi 名称最长 32 字节，当前 33 字节")
    }

    @Test
    fun `中文 SSID 按字节数判断长度`() {
        // 每个汉字 3 字节，11 个汉字 = 33 字节，超限
        val problems = WifiCredentialValidator.validate(
            "测试网络名称占位符超长",
            "12345678",
            WscAuthType.WPA2_PERSONAL,
            WscEncryptionType.AES,
        )
        assertThat(problems).hasSize(1)
        assertThat(problems.first()).contains("33 字节")
    }

    @Test
    fun `WPA 密码长度必须在 8 到 63 之间`() {
        assertThat(
            WifiCredentialValidator.validate("net", "1234567", WscAuthType.WPA2_PERSONAL, WscEncryptionType.AES),
        ).contains("WPA/WPA2 密码长度应为 8..63 个字符，当前 7 个字符")

        assertThat(
            WifiCredentialValidator.validate("net", "a".repeat(64), WscAuthType.WPA2_PERSONAL, WscEncryptionType.AES),
        ).hasSize(1)

        assertThat(
            WifiCredentialValidator.validate("net", "12345678", WscAuthType.WPA2_PERSONAL, WscEncryptionType.AES),
        ).isEmpty()
    }

    @Test
    fun `WPA 密码拒绝非 ASCII 字符`() {
        val problems = WifiCredentialValidator.validate(
            "net",
            "密码密码密码密码",
            WscAuthType.WPA2_PERSONAL,
            WscEncryptionType.AES,
        )
        assertThat(problems).contains("WPA/WPA2 密码仅支持可打印 ASCII 字符")
    }

    @Test
    fun `开放网络不应填写密码也不应设置加密`() {
        val problems = WifiCredentialValidator.validate(
            ssid = "Guest",
            networkKey = "should-not-be-here",
            authType = WscAuthType.OPEN,
            encryptionType = WscEncryptionType.AES,
        )
        assertThat(problems).contains("开放网络不应填写密码")
        assertThat(problems).contains("开放网络的加密方式应为「无加密」")
    }

    @Test
    fun `WPA 不应使用 WEP 或明文加密`() {
        assertThat(
            WifiCredentialValidator.validate("net", "12345678", WscAuthType.WPA2_PERSONAL, WscEncryptionType.WEP),
        ).contains("WPA-Personal 的加密方式应为 TKIP 或 AES")
    }

    @Test
    fun `WEP 密钥支持 5 或 13 字符以及 10 或 26 位十六进制`() {
        val ok = listOf("abcde", "abcdefghijklm", "0123456789", "0123456789ABCDEF0123456789")
        for (key in ok) {
            assertWithMessage("WEP 密钥 $key 应通过校验").that(
                WifiCredentialValidator.validate("net", key, WscAuthType.SHARED, WscEncryptionType.WEP),
            ).isEmpty()
        }

        assertThat(
            WifiCredentialValidator.validate("net", "abcdef", WscAuthType.SHARED, WscEncryptionType.WEP),
        ).hasSize(1)
    }

    @Test
    fun `企业级认证明确标注为不支持`() {
        assertThat(
            WifiCredentialValidator.validate(
                "corp",
                "anything",
                WscAuthType.WPA2_ENTERPRISE,
                WscEncryptionType.AES,
            ),
        ).contains("企业级认证需要账号与证书，本应用暂不支持写入")
    }
}
