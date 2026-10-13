package com.nfctool.app.core.mifare

import com.google.common.truth.Truth.assertThat
import com.nfctool.app.core.bytes.Hex
import com.nfctool.app.core.ndef.NdefCodec
import com.nfctool.app.core.ndef.NdefRecords
import com.nfctool.app.core.ndef.NdefTlv
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * MIFARE 转储文本编解码测试。
 *
 * 使用 `app/src/test/resources/mifare/classic_1k_sector_text.txt` 作为输入。
 * 该样本按 MIFARE Classic 1K 的公开布局构造（**不是真实卡片抓包**，见
 * tools/generate_mifare_fixtures.py 的说明），其中扇区 1 内嵌了由 ndeflib 产出的
 * 真实 NDEF 消息，因此可以顺着"转储 → TLV → NDEF"整条链路逐字节核对。
 */
class MifareDumpTextCodecTest {

    private val fixtureText: String by lazy { readFixture("classic_1k_sector_text.txt") }

    @Test
    fun `识别分节文本格式`() {
        assertThat(MifareDumpTextCodec.detectFormat(fixtureText))
            .isEqualTo(MifareDumpFormat.SECTOR_TEXT)
        assertThat(MifareDumpTextCodec.detectFormat("NFC-TOOL-MIFARE-DUMP\nversion=1\n"))
            .isEqualTo(MifareDumpFormat.NFC_TOOL)
        assertThat(MifareDumpTextCodec.detectFormat("随便一段文字"))
            .isEqualTo(MifareDumpFormat.UNKNOWN)
    }

    @Test
    fun `解析分节文本样本得到 1K 卡结构`() {
        val dump = MifareDumpTextCodec.decodeSectorText(fixtureText)
        assertThat(dump.size).isEqualTo(MifareClassicSize.ONE_K)
        assertThat(dump.blocks).hasSize(64)
        assertThat(dump.uidHex).isEqualTo("4A1B2C3D")
        assertThat(dump.sectorCount).isEqualTo(16)
    }

    @Test
    fun `厂商块解析出 UID 与 BCC 且自洽`() {
        val dump = MifareDumpTextCodec.decodeSectorText(fixtureText)
        val manufacturer = dump.manufacturerBlock()
        assertThat(manufacturer.uidHex).isEqualTo("4A1B2C3D")
        assertThat(manufacturer.sak).isEqualTo(0x08.toByte())
        assertThat(Hex.encode(manufacturer.atqa)).isEqualTo("0400")
        assertThat(manufacturer.bcc)
            .isEqualTo(MifareManufacturerBlock.computeBcc(manufacturer.uid))
    }

    @Test
    fun `扇区尾块解析出 KeyA 访问位 KeyB`() {
        val dump = MifareDumpTextCodec.decodeSectorText(fixtureText)

        val defaultTrailer = dump.sectorTrailer(0)!!
        assertThat(Hex.encode(defaultTrailer.keyA)).isEqualTo("FFFFFFFFFFFF")
        // 访问位为 4 字节：FF 07 80 69
        assertThat(Hex.encode(defaultTrailer.accessBits)).isEqualTo("FF078069")
        assertThat(Hex.encode(defaultTrailer.keyB)).isEqualTo("FFFFFFFFFFFF")

        val madTrailer = dump.sectorTrailer(2)!!
        assertThat(Hex.encode(madTrailer.keyA)).isEqualTo("A0A1A2A3A4A5")
    }

    @Test
    fun `转储中的 NDEF TLV 可以一路解到文本内容`() {
        val dump = MifareDumpTextCodec.decodeSectorText(fixtureText)

        val tlvBytes = NdefTlv.findNdefMessage(dump.concatenatedDataBlocks())
        assertThat(tlvBytes).isNotNull()

        val message = NdefCodec.decode(tlvBytes!!)
        assertThat(message.records).hasSize(1)

        val text = NdefRecords.parseText(message.records[0])
        assertThat(text).isNotNull()
        assertThat(text!!.text).isEqualTo("Hello NFC")
        assertThat(text.languageCode).isEqualTo("en")

        // 与 ndef 目录下由 ndeflib 产出的样本必须是同一串字节
        assertThat(Hex.encode(tlvBytes)).isEqualTo(readFixtureHex("text_en.ndef"))
    }

    @Test
    fun `可写数据块排除厂商块与全部扇区尾块`() {
        val dump = MifareDumpTextCodec.decodeSectorText(fixtureText)
        val blocks = dump.restorableBlocks()
        assertThat(blocks).hasSize(47)
        assertThat(blocks).doesNotContain(0)
        for (sector in 0 until dump.size.sectorCount) {
            val trailer = MifareClassicLayout.firstBlockOfSector(dump.size, sector) +
                MifareClassicLayout.blocksInSector(dump.size, sector) - 1
            assertThat(blocks).doesNotContain(trailer)
        }
    }

    @Test
    fun `自有格式编码解码可往返`() {
        val original = MifareDumpTextCodec.decodeSectorText(fixtureText)
        val encoded = MifareDumpTextCodec.encodeNative(original)
        val decoded = MifareDumpTextCodec.decodeNative(encoded)

        assertThat(decoded).isEqualTo(original)
        assertThat(MifareDumpTextCodec.encodeNative(decoded)).isEqualTo(encoded)
    }

    @Test
    fun `导出分节文本后可再次解析`() {
        val original = MifareDumpTextCodec.decodeSectorText(fixtureText)
        val exported = MifareDumpTextCodec.encodeSectorText(original)
        val reparsed = MifareDumpTextCodec.decodeSectorText(exported)
        assertThat(reparsed).isEqualTo(original)
    }

    @Test
    fun `自动识别格式的入口能处理两种格式`() {
        val fromSectorText = MifareDumpTextCodec.decode(fixtureText)
        val native = MifareDumpTextCodec.encodeNative(fromSectorText)
        val fromNative = MifareDumpTextCodec.decode(native)
        assertThat(fromNative).isEqualTo(fromSectorText)
    }

    @Test
    fun `自有格式带密钥信息且可往返`() {
        val dump = MifareDump(
            size = MifareClassicSize.ONE_K,
            uid = Hex.decode("4A1B2C3D"),
            blocks = List(64) { index -> ByteArray(16) { index.toByte() } },
            sectorKeys = mapOf(
                0 to MifareSectorKeys(MifareSectorKeys.FACTORY_DEFAULT, null),
                2 to MifareSectorKeys(null, MifareSectorKeys.MAD_KEY),
            ),
        )
        val encoded = MifareDumpTextCodec.encodeNative(dump)
        assertThat(encoded).contains("key.0=A:FFFFFFFFFFFF,B:-")
        assertThat(encoded).contains("key.2=A:-,B:A0A1A2A3A4A5")

        val decoded = MifareDumpTextCodec.decodeNative(encoded)
        assertThat(Hex.encode(decoded.sectorKeysOf(0)!!.keyA!!))
            .isEqualTo(Hex.encode(MifareSectorKeys.FACTORY_DEFAULT))
        assertThat(decoded.sectorKeysOf(0)!!.keyB).isNull()
        assertThat(decoded.sectorKeysOf(2)!!.keyA).isNull()
        assertThat(Hex.encode(decoded.sectorKeysOf(2)!!.keyB!!))
            .isEqualTo(Hex.encode(MifareSectorKeys.MAD_KEY))
    }

    @Test
    fun `扇区编号不连续时报错`() {
        val bad = "+Sector: 0\n" + "00".repeat(16) + "\n" +
            "+Sector: 2\n" + "00".repeat(16) + "\n"
        val error = assertThrows(IllegalArgumentException::class.java) {
            MifareDumpTextCodec.decodeSectorText(bad)
        }
        assertThat(error.message).contains("连续递增")
    }

    @Test
    fun `块数无法对应已知容量时报错`() {
        val bad = "+Sector: 0\n" + ("00".repeat(16) + "\n").repeat(3)
        val error = assertThrows(IllegalArgumentException::class.java) {
            MifareDumpTextCodec.decodeSectorText(bad)
        }
        assertThat(error.message).contains("无法对应已知的 MIFARE Classic 容量")
    }

    @Test
    fun `块长度不合法时报错`() {
        val bad = "+Sector: 0\n" + "0011\n"
        assertThrows(IllegalArgumentException::class.java) {
            MifareDumpTextCodec.decodeSectorText(bad)
        }
    }

    @Test
    fun `自有格式缺字段或版本不符时报错`() {
        assertThrows(IllegalArgumentException::class.java) {
            MifareDumpTextCodec.decodeNative("NFC-TOOL-MIFARE-DUMP\nversion=1\n")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MifareDumpTextCodec.decodeNative("NFC-TOOL-MIFARE-DUMP\nversion=99\nsize=ONE_K\n")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MifareDumpTextCodec.decodeNative("NFC-TOOL-MIFARE-DUMP\nversion=1\nsize=UNKNOWN\n")
        }
    }

    @Test
    fun `无法识别的格式报错`() {
        assertThrows(IllegalArgumentException::class.java) {
            MifareDumpTextCodec.decode("这不是转储文件")
        }
    }

    @Test
    fun `块数与容量不一致时构造转储失败`() {
        assertThrows(IllegalArgumentException::class.java) {
            MifareDump(
                size = MifareClassicSize.ONE_K,
                uid = ByteArray(4),
                blocks = List(63) { ByteArray(16) },
            )
        }
    }

    // ------------------------------------------------------------ 资源读取

    private fun readFixture(name: String): String =
        readFixtureBytes(name).toString(Charsets.UTF_8)

    private fun readFixtureHex(name: String): String =
        Hex.encode(readFixtureBytes(name))

    private fun readFixtureBytes(name: String): ByteArray {
        val path = when {
            name.endsWith(".ndef") -> "/ndef/$name"
            else -> "/mifare/$name"
        }
        val stream = javaClass.getResourceAsStream(path)
            ?: error("找不到测试样本 $path")
        return stream.use { it.readBytes() }
    }
}
