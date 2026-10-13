package com.nfctool.app.core.mifare

/** MIFARE Classic 的容量规格。 */
enum class MifareClassicSize(
    val byteCount: Int,
    val sectorCount: Int,
    val blockCount: Int,
    val label: String,
) {
    MINI(320, 5, 20, "MIFARE Classic Mini（320 字节 / 5 扇区）"),
    ONE_K(1024, 16, 64, "MIFARE Classic 1K（1024 字节 / 16 扇区）"),
    TWO_K(2048, 32, 128, "MIFARE Classic 2K（2048 字节 / 32 扇区）"),
    FOUR_K(4096, 40, 256, "MIFARE Classic 4K（4096 字节 / 40 扇区）"),
    ;

    companion object {
        fun fromByteCount(byteCount: Int): MifareClassicSize? =
            entries.firstOrNull { it.byteCount == byteCount }

        fun fromLabelPrefix(text: String): MifareClassicSize? = entries.firstOrNull { text.startsWith(it.name) }
    }
}

/**
 * MIFARE Classic 的扇区 / 块布局换算。
 *
 * 布局依据：MIFARE Classic 规格 —— 每块 16 字节；
 * Mini 5 扇区、1K 16 扇区、2K 32 扇区均为每扇区 4 块；
 * 4K 为前 32 个扇区每扇区 4 块、后 8 个扇区（32..39）每扇区 16 块。
 */
object MifareClassicLayout {

    const val BLOCK_SIZE = 16

    private const val SMALL_SECTOR_BLOCKS = 4
    private const val LARGE_SECTOR_BLOCKS = 16
    private const val SMALL_SECTOR_LIMIT = 32

    fun blocksInSector(size: MifareClassicSize, sector: Int): Int {
        require(sector in 0 until size.sectorCount) {
            "扇区号 $sector 超出范围 0..${size.sectorCount - 1}"
        }
        return if (size == MifareClassicSize.FOUR_K && sector >= SMALL_SECTOR_LIMIT) {
            LARGE_SECTOR_BLOCKS
        } else {
            SMALL_SECTOR_BLOCKS
        }
    }

    fun firstBlockOfSector(size: MifareClassicSize, sector: Int): Int {
        require(sector in 0 until size.sectorCount) {
            "扇区号 $sector 超出范围 0..${size.sectorCount - 1}"
        }
        if (size != MifareClassicSize.FOUR_K || sector < SMALL_SECTOR_LIMIT) {
            return sector * SMALL_SECTOR_BLOCKS
        }
        val smallBlocks = SMALL_SECTOR_LIMIT * SMALL_SECTOR_BLOCKS
        return smallBlocks + (sector - SMALL_SECTOR_LIMIT) * LARGE_SECTOR_BLOCKS
    }

    fun sectorOfBlock(size: MifareClassicSize, block: Int): Int {
        require(block in 0 until size.blockCount) {
            "块号 $block 超出范围 0..${size.blockCount - 1}"
        }
        for (sector in 0 until size.sectorCount) {
            val first = firstBlockOfSector(size, sector)
            if (block < first + blocksInSector(size, sector)) return sector
        }
        throw IllegalArgumentException("块号 $block 无法归属到任何扇区")
    }

    /** 块是否是该扇区的尾块（存放 KeyA / 访问位 / KeyB）。 */
    fun isSectorTrailer(size: MifareClassicSize, block: Int): Boolean {
        val sector = sectorOfBlock(size, block)
        val lastBlock = firstBlockOfSector(size, sector) + blocksInSector(size, sector) - 1
        return block == lastBlock
    }

    /** 块 0 为厂商块：存放 UID 与卡片参数，出厂后只读。 */
    fun isManufacturerBlock(block: Int): Boolean = block == 0

    /** 数据块（排除厂商块与扇区尾块），这类块是可以自由写入的。 */
    fun isWritableDataBlock(size: MifareClassicSize, block: Int): Boolean =
        !isManufacturerBlock(block) && !isSectorTrailer(size, block)

    fun dataBlocks(size: MifareClassicSize): List<Int> =
        (0 until size.blockCount).filter { isWritableDataBlock(size, it) }
}
