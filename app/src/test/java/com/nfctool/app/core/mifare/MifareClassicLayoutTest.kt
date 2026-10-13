package com.nfctool.app.core.mifare

import com.google.common.truth.Truth.assertThat
import com.nfctool.app.core.bytes.Hex
import org.junit.Assert.assertThrows
import org.junit.Test

class MifareClassicLayoutTest {

    @Test
    fun `容量规格的块数与字节数自洽`() {
        for (size in MifareClassicSize.entries) {
            assertThat(size.blockCount * MifareClassicLayout.BLOCK_SIZE).isEqualTo(size.byteCount)
        }
        assertThat(MifareClassicSize.MINI.blockCount).isEqualTo(20)
        assertThat(MifareClassicSize.ONE_K.blockCount).isEqualTo(64)
        assertThat(MifareClassicSize.TWO_K.blockCount).isEqualTo(128)
        assertThat(MifareClassicSize.FOUR_K.blockCount).isEqualTo(256)
    }

    @Test
    fun `按字节数反查容量`() {
        assertThat(MifareClassicSize.fromByteCount(1024)).isEqualTo(MifareClassicSize.ONE_K)
        assertThat(MifareClassicSize.fromByteCount(4096)).isEqualTo(MifareClassicSize.FOUR_K)
        assertThat(MifareClassicSize.fromByteCount(999)).isNull()
    }

    @Test
    fun `1K 卡每扇区四块且首块编号正确`() {
        val size = MifareClassicSize.ONE_K
        for (sector in 0 until size.sectorCount) {
            assertThat(MifareClassicLayout.blocksInSector(size, sector)).isEqualTo(4)
            assertThat(MifareClassicLayout.firstBlockOfSector(size, sector)).isEqualTo(sector * 4)
        }
        assertThat(MifareClassicLayout.firstBlockOfSector(size, 15)).isEqualTo(60)
    }

    @Test
    fun `4K 卡前 32 扇区四块后 8 扇区十六块`() {
        val size = MifareClassicSize.FOUR_K
        assertThat(MifareClassicLayout.blocksInSector(size, 31)).isEqualTo(4)
        assertThat(MifareClassicLayout.blocksInSector(size, 32)).isEqualTo(16)
        assertThat(MifareClassicLayout.blocksInSector(size, 39)).isEqualTo(16)

        assertThat(MifareClassicLayout.firstBlockOfSector(size, 31)).isEqualTo(124)
        assertThat(MifareClassicLayout.firstBlockOfSector(size, 32)).isEqualTo(128)
        assertThat(MifareClassicLayout.firstBlockOfSector(size, 39)).isEqualTo(240)

        val lastSectorFirst = MifareClassicLayout.firstBlockOfSector(size, 39)
        assertThat(lastSectorFirst + MifareClassicLayout.blocksInSector(size, 39) - 1)
            .isEqualTo(size.blockCount - 1)
    }

    @Test
    fun `块与扇区可互相换算`() {
        val size = MifareClassicSize.FOUR_K
        for (sector in 0 until size.sectorCount) {
            val first = MifareClassicLayout.firstBlockOfSector(size, sector)
            val count = MifareClassicLayout.blocksInSector(size, sector)
            for (block in first until first + count) {
                assertThat(MifareClassicLayout.sectorOfBlock(size, block)).isEqualTo(sector)
            }
        }
    }

    @Test
    fun `扇区尾块为每扇区最后一块`() {
        val size = MifareClassicSize.ONE_K
        assertThat(MifareClassicLayout.isSectorTrailer(size, 3)).isTrue()
        assertThat(MifareClassicLayout.isSectorTrailer(size, 2)).isFalse()
        assertThat(MifareClassicLayout.isSectorTrailer(size, 7)).isTrue()
        assertThat(MifareClassicLayout.isSectorTrailer(size, 63)).isTrue()
    }

    @Test
    fun `厂商块是块 0 且不可写`() {
        val size = MifareClassicSize.ONE_K
        assertThat(MifareClassicLayout.isManufacturerBlock(0)).isTrue()
        assertThat(MifareClassicLayout.isManufacturerBlock(1)).isFalse()
        assertThat(MifareClassicLayout.isWritableDataBlock(size, 0)).isFalse()
        assertThat(MifareClassicLayout.isWritableDataBlock(size, 1)).isTrue()
        assertThat(MifareClassicLayout.isWritableDataBlock(size, 3)).isFalse()
    }

    @Test
    fun `可写数据块数量等于总块数减去厂商块与尾块`() {
        val size = MifareClassicSize.ONE_K
        // 64 块 - 1 个厂商块 - 16 个尾块 = 47
        assertThat(MifareClassicLayout.dataBlocks(size)).hasSize(47)
        assertThat(MifareClassicLayout.dataBlocks(size)).doesNotContain(0)
        assertThat(MifareClassicLayout.dataBlocks(size)).doesNotContain(3)
        assertThat(MifareClassicLayout.dataBlocks(size)).contains(1)
    }

    @Test
    fun `越界访问报错`() {
        assertThrows(IllegalArgumentException::class.java) {
            MifareClassicLayout.blocksInSector(MifareClassicSize.ONE_K, 16)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MifareClassicLayout.sectorOfBlock(MifareClassicSize.ONE_K, 64)
        }
    }
}

class MifareClassicPartsTest {

    @Test
    fun `扇区尾块按 KeyA 访问位 KeyB 拆分`() {
        // 出厂默认尾块：KeyA 6 字节 + 访问位 4 字节(FF 07 80 69) + KeyB 6 字节
        val block = Hex.decode("FFFFFFFFFFFFFF078069FFFFFFFFFFFF")
        val trailer = MifareSectorTrailer.parse(block)
        assertThat(Hex.encode(trailer.keyA)).isEqualTo("FFFFFFFFFFFF")
        assertThat(Hex.encode(trailer.accessBits)).isEqualTo("FF078069")
        assertThat(Hex.encode(trailer.keyB)).isEqualTo("FFFFFFFFFFFF")
        assertThat(Hex.encode(MifareSectorTrailer.compose(trailer))).isEqualTo(Hex.encode(block))
    }

    @Test
    fun `尾块长度不合法时报错`() {
        assertThrows(IllegalArgumentException::class.java) {
            MifareSectorTrailer.parse(ByteArray(15))
        }
    }

    @Test
    fun `厂商块按 UID BCC SAK ATQA 拆分`() {
        val block = Hex.decode("4A1B2C3D400804000000000000000000")
        val manufacturer = MifareManufacturerBlock.parse(block)
        assertThat(manufacturer.uidHex).isEqualTo("4A1B2C3D")
        assertThat(manufacturer.bcc).isEqualTo(0x40.toByte())
        assertThat(manufacturer.sak).isEqualTo(0x08.toByte())
        assertThat(Hex.encode(manufacturer.atqa)).isEqualTo("0400")
        assertThat(manufacturer.manufacturerData).hasLength(8)
    }

    @Test
    fun `BCC 为 UID 四字节异或`() {
        assertThat(MifareManufacturerBlock.computeBcc(Hex.decode("4A1B2C3D")))
            .isEqualTo(0x40.toByte())
        assertThat(MifareManufacturerBlock.computeBcc(Hex.decode("00000000")))
            .isEqualTo(0.toByte())
    }

    @Test
    fun `密钥长度必须为 6 字节`() {
        assertThrows(IllegalArgumentException::class.java) {
            MifareSectorKeys(Hex.decode("FFFFFFFF"), null)
        }
    }

    @Test
    fun `常见候选密钥的取值正确`() {
        assertThat(Hex.encode(MifareSectorKeys.FACTORY_DEFAULT)).isEqualTo("FFFFFFFFFFFF")
        assertThat(Hex.encode(MifareSectorKeys.NDEF_KEY)).isEqualTo("D3F7D3F7D3F7")
        assertThat(Hex.encode(MifareSectorKeys.MAD_KEY)).isEqualTo("A0A1A2A3A4A5")
        assertThat(MifareSectorKeys.CANDIDATES).hasSize(4)
    }
}
