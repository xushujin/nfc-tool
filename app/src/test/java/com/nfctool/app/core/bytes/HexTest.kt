package com.nfctool.app.core.bytes

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class HexTest {

    @Test
    fun `编码为大写十六进制`() {
        assertThat(Hex.encode(byteArrayOf(0x00, 0x0F, 0x7F, 0xFF.toByte())))
            .isEqualTo("000F7FFF")
        assertThat(Hex.encode(ByteArray(0))).isEmpty()
    }

    @Test
    fun `带分隔符编码`() {
        assertThat(Hex.encode(byteArrayOf(0x4A, 0x1B, 0x2C), ":")).isEqualTo("4A:1B:2C")
    }

    @Test
    fun `解码时容忍空格冒号连字符与 0x 前缀`() {
        val expected = "4A1B2C3D"
        assertThat(Hex.encode(Hex.decode("4A1B2C3D"))).isEqualTo(expected)
        assertThat(Hex.encode(Hex.decode("4a:1b:2c:3d"))).isEqualTo(expected)
        assertThat(Hex.encode(Hex.decode("4A 1B 2C 3D"))).isEqualTo(expected)
        assertThat(Hex.encode(Hex.decode("4A-1B-2C-3D"))).isEqualTo(expected)
        assertThat(Hex.encode(Hex.decode("0x4A1B2C3D"))).isEqualTo(expected)
    }

    @Test
    fun `长度为奇数时拒绝`() {
        val error = assertThrows(IllegalArgumentException::class.java) { Hex.decode("ABC") }
        assertThat(error.message).contains("偶数")
    }

    @Test
    fun `非法字符时拒绝`() {
        assertThrows(IllegalArgumentException::class.java) { Hex.decode("ZZ") }
        assertThat(Hex.decodeOrNull("ZZ")).isNull()
        assertThat(Hex.encode(Hex.decodeOrNull("00FF")!!)).isEqualTo("00FF")
    }
}

class BytesTest {

    @Test
    fun `大端序读写可往返`() {
        assertThat(Hex.encode(Bytes.u16be(0x1234))).isEqualTo("1234")
        assertThat(Bytes.readU16be(byteArrayOf(0x12, 0x34), 0)).isEqualTo(0x1234)

        assertThat(Hex.encode(Bytes.u32be(0x0001_0000))).isEqualTo("00010000")
        assertThat(Bytes.readU32be(byteArrayOf(0, 1, 0, 0), 0)).isEqualTo(0x0001_0000)
    }

    @Test
    fun `越界时拒绝`() {
        assertThrows(IllegalArgumentException::class.java) { Bytes.u16be(0x1_0000) }
        assertThrows(IllegalArgumentException::class.java) { Bytes.u32be(-1) }
        assertThrows(IllegalArgumentException::class.java) { Bytes.readU16be(byteArrayOf(1), 0) }
    }

    @Test
    fun `ascii 编码拒绝非 ASCII 字符`() {
        assertThat(Hex.encode(Bytes.toAscii("T"))).isEqualTo("54")
        assertThrows(IllegalArgumentException::class.java) { Bytes.toAscii("中") }
    }

    @Test
    fun `拼接保持顺序`() {
        assertThat(Hex.encode(Bytes.concat(byteArrayOf(1, 2), ByteArray(0), byteArrayOf(3))))
            .isEqualTo("010203")
    }
}
