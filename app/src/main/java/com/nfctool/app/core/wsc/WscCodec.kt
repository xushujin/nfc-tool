package com.nfctool.app.core.wsc

import com.nfctool.app.core.bytes.Bytes
import com.nfctool.app.core.ndef.NdefFormatException
import com.nfctool.app.core.ndef.NdefRecord
import com.nfctool.app.core.ndef.NdefRecords
import java.io.ByteArrayOutputStream

/**
 * WSC 属性 TLV 编解码。
 *
 * 线格式：`属性ID(2字节, 大端) | 长度(2字节, 大端) | 值(长度字节)`
 * 依据：Wi-Fi Simple Configuration 规范的属性编码方式；
 * 属性 ID 取值已与 hostap `wps_defs.h` 逐条比对（见 [WscAttributeId]）。
 */
object WscCodec {

    private const val MAC_LENGTH = 6

    /** 生成可写入标签的 NDEF 记录（TNF=Media Type，类型 `application/vnd.wfa.wsc`）。 */
    fun buildRecord(credential: WifiCredential): NdefRecord =
        NdefRecords.mime(NdefRecords.MIME_WIFI_WSC, encode(credential))

    /** 生成 WSC 负载（即 MIME 记录的负载部分）。 */
    fun encode(credential: WifiCredential): ByteArray {
        validate(credential)

        val inner = ByteArrayOutputStream()
        inner.write(attribute(WscAttributeId.NETWORK_INDEX, byteArrayOf(credential.networkIndex.toByte())))
        inner.write(attribute(WscAttributeId.SSID, credential.ssid.toByteArray(Charsets.UTF_8)))
        inner.write(attribute(WscAttributeId.AUTH_TYPE, Bytes.u16be(credential.authType.code)))
        inner.write(attribute(WscAttributeId.ENCRYPTION_TYPE, Bytes.u16be(credential.encryptionType.code)))
        if (credential.networkKey.isNotEmpty()) {
            inner.write(attribute(WscAttributeId.NETWORK_KEY, credential.networkKey.toByteArray(Charsets.UTF_8)))
        }
        credential.macAddress?.let {
            inner.write(attribute(WscAttributeId.MAC_ADDRESS, it))
        }

        val out = ByteArrayOutputStream()
        out.write(attribute(WscAttributeId.VERSION, byteArrayOf(WSC_VERSION_1_0)))
        out.write(attribute(WscAttributeId.CREDENTIAL, inner.toByteArray()))
        return out.toByteArray()
    }

    /** 解析 WSC 负载；结构不符时返回 null，字段缺失时对应字段取默认值。 */
    fun decode(payload: ByteArray): WifiCredential? {
        val top = parseAttributes(payload) ?: return null
        val credentialValue = top.firstOrNull { it.id == WscAttributeId.CREDENTIAL }?.value ?: return null
        val inner = parseAttributes(credentialValue) ?: return null

        val ssidBytes = inner.firstOrNull { it.id == WscAttributeId.SSID }?.value ?: return null
        val authCode = inner.firstOrNull { it.id == WscAttributeId.AUTH_TYPE }?.value
            ?.takeIf { it.size >= 2 }?.let { Bytes.readU16be(it, 0) }
        val encryptionCode = inner.firstOrNull { it.id == WscAttributeId.ENCRYPTION_TYPE }?.value
            ?.takeIf { it.size >= 2 }?.let { Bytes.readU16be(it, 0) }
        val keyBytes = inner.firstOrNull { it.id == WscAttributeId.NETWORK_KEY }?.value
        val indexValue = inner.firstOrNull { it.id == WscAttributeId.NETWORK_INDEX }?.value
            ?.firstOrNull()?.toInt()?.and(0xFF)
        val macValue = inner.firstOrNull { it.id == WscAttributeId.MAC_ADDRESS }?.value

        val authType = authCode?.let { WscAuthType.fromCode(it) }
            ?: if (keyBytes == null || keyBytes.isEmpty()) WscAuthType.OPEN else WscAuthType.WPA2_PERSONAL
        val encryptionType = encryptionCode?.let { WscEncryptionType.fromCode(it) }
            ?: if (authType == WscAuthType.OPEN) WscEncryptionType.NONE else WscEncryptionType.AES

        return WifiCredential(
            ssid = String(ssidBytes, Charsets.UTF_8),
            networkKey = keyBytes?.let { String(it, Charsets.UTF_8) } ?: "",
            authType = authType,
            encryptionType = encryptionType,
            networkIndex = indexValue ?: 1,
            macAddress = macValue?.takeIf { it.size == MAC_LENGTH },
        )
    }

    /** 解析失败时返回 null（长度字段越界、截断等）。 */
    private fun parseAttributes(bytes: ByteArray): List<Attribute>? {
        val result = ArrayList<Attribute>()
        var offset = 0
        while (offset < bytes.size) {
            if (offset + 4 > bytes.size) return null
            val id = Bytes.readU16be(bytes, offset)
            val length = Bytes.readU16be(bytes, offset + 2)
            offset += 4
            if (offset + length > bytes.size) return null
            result.add(Attribute(id, bytes.copyOfRange(offset, offset + length)))
            offset += length
        }
        return result
    }

    private fun attribute(id: Int, value: ByteArray): ByteArray =
        Bytes.concat(Bytes.u16be(id), Bytes.u16be(value.size), value)

    private data class Attribute(val id: Int, val value: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Attribute && id == other.id && value.contentEquals(other.value)

        override fun hashCode(): Int = 31 * id + value.contentHashCode()
    }

    private fun validate(credential: WifiCredential) {
        val problems = WifiCredentialValidator.validate(credential)
        if (problems.isNotEmpty()) {
            throw NdefFormatException("WiFi 凭据不合法：" + problems.joinToString("；"))
        }
    }
}

/**
 * WiFi 凭据校验。返回问题描述列表，空列表表示通过。
 *
 * 长度约束来自 802.11 与 WSC 规范：SSID 1..32 字节；
 * WPA/WPA2 口令 8..63 个 ASCII 字符；WEP 密钥 5/13 个 ASCII 字符或 10/26 个十六进制字符。
 */
object WifiCredentialValidator {

    private const val SSID_MAX_BYTES = 32

    fun validate(credential: WifiCredential): List<String> = validate(
        ssid = credential.ssid,
        networkKey = credential.networkKey,
        authType = credential.authType,
        encryptionType = credential.encryptionType,
    )

    fun validate(
        ssid: String,
        networkKey: String,
        authType: WscAuthType,
        encryptionType: WscEncryptionType,
    ): List<String> {
        val problems = ArrayList<String>()

        val ssidBytes = ssid.toByteArray(Charsets.UTF_8)
        if (ssidBytes.isEmpty()) {
            problems.add("WiFi 名称不能为空")
        } else if (ssidBytes.size > SSID_MAX_BYTES) {
            problems.add("WiFi 名称最长 32 字节，当前 ${ssidBytes.size} 字节")
        }

        when (authType) {
            WscAuthType.OPEN -> {
                if (networkKey.isNotEmpty()) problems.add("开放网络不应填写密码")
                if (encryptionType != WscEncryptionType.NONE) {
                    problems.add("开放网络的加密方式应为「无加密」")
                }
            }

            WscAuthType.WPA_PERSONAL, WscAuthType.WPA2_PERSONAL -> {
                if (encryptionType == WscEncryptionType.WEP || encryptionType == WscEncryptionType.NONE) {
                    problems.add("WPA-Personal 的加密方式应为 TKIP 或 AES")
                }
                problems.addAll(validatePsk(networkKey))
            }

            WscAuthType.SHARED -> problems.addAll(validateWepKey(networkKey))

            WscAuthType.WPA_ENTERPRISE, WscAuthType.WPA2_ENTERPRISE ->
                problems.add("企业级认证需要账号与证书，本应用暂不支持写入")
        }

        return problems
    }

    private fun validatePsk(key: String): List<String> {
        if (key.isEmpty()) return listOf("WPA/WPA2 网络必须填写密码")
        if (key.length < 8 || key.length > 63) {
            return listOf("WPA/WPA2 密码长度应为 8..63 个字符，当前 ${key.length} 个字符")
        }
        if (key.any { it.code > 0x7F || it.code < 0x20 }) {
            return listOf("WPA/WPA2 密码仅支持可打印 ASCII 字符")
        }
        return emptyList()
    }

    private fun validateWepKey(key: String): List<String> {
        if (key.isEmpty()) return listOf("WEP 网络必须填写密钥")
        val isAsciiLengthValid = key.length == 5 || key.length == 13
        val isHexLengthValid = (key.length == 10 || key.length == 26) && key.all { it.isHexDigit() }
        return if (isAsciiLengthValid || isHexLengthValid) {
            emptyList()
        } else {
            listOf("WEP 密钥应为 5/13 个字符，或 10/26 个十六进制字符，当前 ${key.length} 个字符")
        }
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
