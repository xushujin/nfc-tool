package com.nfctool.app.core.ndef

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.nfctool.app.core.bytes.Hex
import com.nfctool.app.core.wsc.WscAuthType
import com.nfctool.app.core.wsc.WscCodec
import com.nfctool.app.core.wsc.WscEncryptionType
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 用真实二进制样本验证 NDEF 解码器。
 *
 * 样本由独立第三方实现 **ndeflib 0.3.3** 编码产出（见 tools/generate_ndef_fixtures.py），
 * 期望值表由同一脚本生成。这样做能发现"编码与解码自洽、但都不符合规范"的问题，
 * 只跑自己 encode → 自己 decode 的往返测试是发现不了的。
 */
class NdefFixtureTest {

    @Test
    fun `全部样本的解码结果与期望值逐项一致`() {
        val expectations = loadExpectations()
        assertThat(expectations).isNotEmpty()

        for ((fixture, expected) in expectations) {
            val bytes = readFixture(fixture)

            if (expected["decode_error"] == "true") {
                assertThrows("样本 $fixture 应当解码失败", NdefFormatException::class.java) {
                    NdefCodec.decode(bytes)
                }
                continue
            }

            assertThat(bytes.size).isEqualTo(expected.getValue("encoded_size").toInt())
            assertThat(Hex.encode(byteArrayOf(bytes[0]))).isEqualTo(
                expected.getValue("header_first_byte").removePrefix("0x"),
            )

            val message = NdefCodec.decode(bytes)
            assertThat(message.records.size).isEqualTo(expected.getValue("record_count").toInt())

            expected["record_count"]!!.toInt().let { count ->
                for (index in 0 until count) {
                    assertRecord(fixture, index, message.records[index], expected)
                }
            }

            if (expected["reencode_identical"] != "false") {
                assertWithMessage("样本 $fixture 重新编码后的字节")
                    .that(Hex.encode(NdefCodec.encode(message)))
                    .isEqualTo(Hex.encode(bytes))
            }
        }
    }

    private fun assertRecord(
        fixture: String,
        index: Int,
        record: NdefRecord,
        expected: Map<String, String>,
    ) {
        val prefix = "record.$index"
        fun message(aspect: String) = "$fixture $prefix.$aspect"

        expected["$prefix.tnf"]?.let {
            assertWithMessage(message("tnf")).that(record.tnf).isEqualTo(it.toInt())
        }
        expected["$prefix.type"]?.let {
            assertWithMessage(message("type")).that(record.typeAscii).isEqualTo(it)
        }
        expected["$prefix.id_hex"]?.let {
            assertWithMessage(message("id")).that(Hex.encode(record.id)).isEqualTo(it)
        }
        expected["$prefix.payload_hex"]?.let {
            assertWithMessage(message("payload")).that(Hex.encode(record.payload)).isEqualTo(it)
        }
        expected["$prefix.payload_utf8"]?.let {
            assertWithMessage(message("payload")).that(String(record.payload, Charsets.UTF_8))
                .isEqualTo(it)
        }

        expected["$prefix.text"]?.let { text ->
            val payload = NdefRecords.parseText(record)
            assertWithMessage(message("text")).that(payload).isNotNull()
            assertWithMessage(message("text")).that(payload!!.text).isEqualTo(text)
        }
        expected["$prefix.language"]?.let { language ->
            val payload = NdefRecords.parseText(record)
            assertWithMessage(message("language")).that(payload).isNotNull()
            assertWithMessage(message("language")).that(payload!!.languageCode).isEqualTo(language)
        }
        expected["$prefix.uri"]?.let { uri ->
            assertWithMessage(message("uri")).that(NdefRecords.parseUri(record)).isEqualTo(uri)
        }
        expected["$prefix.sp_uri"]?.let { uri ->
            val poster = NdefRecords.parseSmartPoster(record)
            assertWithMessage(message("sp_uri")).that(poster).isNotNull()
            assertWithMessage(message("sp_uri")).that(poster!!.uri).isEqualTo(uri)
        }
        expected["$prefix.sp_title"]?.let { title ->
            val poster = NdefRecords.parseSmartPoster(record)
            assertWithMessage(message("sp_title")).that(poster).isNotNull()
            assertWithMessage(message("sp_title")).that(poster!!.title).isEqualTo(title)
        }

        if (expected.containsKey("$prefix.wifi_ssid")) {
            assertWithMessage(message("wifi")).that(NdefRecords.isWifiConfiguration(record)).isTrue()
            val credential = WscCodec.decode(record.payload)
            assertWithMessage(message("wifi")).that(credential).isNotNull()
            assertWithMessage(message("wifi_ssid")).that(credential!!.ssid)
                .isEqualTo(expected.getValue("$prefix.wifi_ssid"))
            assertWithMessage(message("wifi_key")).that(credential.networkKey)
                .isEqualTo(expected.getValue("$prefix.wifi_key"))
            assertWithMessage(message("wifi_auth")).that(credential.authType)
                .isEqualTo(authTypeOf(expected.getValue("$prefix.wifi_auth")))
            assertWithMessage(message("wifi_encr")).that(credential.encryptionType)
                .isEqualTo(encryptionTypeOf(expected.getValue("$prefix.wifi_encr")))
        }
    }

    private fun authTypeOf(hex: String): WscAuthType {
        val code = hex.removePrefix("0x").toInt(16)
        return requireNotNull(WscAuthType.fromCode(code)) { "样本中的认证类型 0x$hex 未在枚举中定义" }
    }

    private fun encryptionTypeOf(hex: String): WscEncryptionType {
        val code = hex.removePrefix("0x").toInt(16)
        return requireNotNull(WscEncryptionType.fromCode(code)) {
            "样本中的加密类型 0x$hex 未在枚举中定义"
        }
    }

    // ------------------------------------------------------------ 资源读取

    private fun readFixture(name: String): ByteArray {
        val stream = javaClass.getResourceAsStream("/ndef/$name")
            ?: error("找不到样本文件 /ndef/$name，请先运行 tools/generate_ndef_fixtures.py")
        return stream.use { it.readBytes() }
    }

    /** 读取期望值表，返回 `样本名 -> (维度 -> 期望值)`。 */
    private fun loadExpectations(): Map<String, Map<String, String>> {
        val stream = javaClass.getResourceAsStream("/ndef/expectations.tsv")
            ?: error("找不到期望值表 /ndef/expectations.tsv")
        val result = LinkedHashMap<String, MutableMap<String, String>>()
        stream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEach
                // 不 trim 整行：期望值可能为空串（如开放网络的密码），
                // 一旦 trim 掉行尾制表符就会把三列压成两列。
                val parts = line.split('\t')
                check(parts.size == 3) { "期望值表行格式错误（应为 3 列）：$line" }
                result.getOrPut(parts[0]) { LinkedHashMap() }[parts[1]] = parts[2]
            }
        }
        return result
    }
}
