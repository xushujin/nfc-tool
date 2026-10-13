package com.nfctool.app.core.mifare

import com.nfctool.app.core.bytes.Hex

/**
 * 一次 MIFARE Classic 整卡转储。
 *
 * [blocks] 按块号顺序存放，每块固定 16 字节；厂商块（块 0）与扇区尾块也在其中，
 * 但这两类块在回写时会被跳过（厂商块出厂只读，尾块写错会导致卡片永久锁死）。
 */
class MifareDump(
    val size: MifareClassicSize,
    val uid: ByteArray,
    val blocks: List<ByteArray>,
    val sectorKeys: Map<Int, MifareSectorKeys> = emptyMap(),
) {
    init {
        require(blocks.size == size.blockCount) {
            "块数 ${blocks.size} 与容量 ${size.name}（应有 ${size.blockCount} 块）不一致"
        }
        blocks.forEachIndexed { index, block ->
            require(block.size == MifareClassicLayout.BLOCK_SIZE) {
                "第 $index 块长度为 ${block.size}，应为 ${MifareClassicLayout.BLOCK_SIZE}"
            }
        }
    }

    val uidHex: String get() = Hex.encode(uid)

    val sectorCount: Int get() = size.sectorCount

    fun block(index: Int): ByteArray {
        require(index in blocks.indices) { "块号 $index 超出范围 0..${blocks.lastIndex}" }
        return blocks[index]
    }

    fun sectorKeysOf(sector: Int): MifareSectorKeys? = sectorKeys[sector]

    fun sectorTrailer(sector: Int): MifareSectorTrailer? {
        if (sector !in 0 until size.sectorCount) return null
        val trailerBlock = MifareClassicLayout.firstBlockOfSector(size, sector) +
            MifareClassicLayout.blocksInSector(size, sector) - 1
        return MifareSectorTrailer.parse(blocks[trailerBlock])
    }

    fun manufacturerBlock(): MifareManufacturerBlock = MifareManufacturerBlock.parse(blocks[0])

    /**
     * 可以被回写的块：排除厂商块与所有扇区尾块。
     *
     * 排除扇区尾块是因为尾块内含访问位，写错会把扇区置为不可读；
     * 本应用不提供尾块回写能力，需要改访问位请使用专用工具。
     */
    fun restorableBlocks(): List<Int> = MifareClassicLayout.dataBlocks(size)

    /**
     * 把所有数据块（不含厂商块与扇区尾块）按块号拼接成一串。
     *
     * 用途是在转储里查找 NDEF TLV：标准 NDEF 格式化的卡片会把 TLV 放在数据块中，
     * 拼接后即可交给 TLV 解析器处理。这是启发式做法，不是规范规定的固定位置。
     */
    fun concatenatedDataBlocks(): ByteArray {
        val blocks = restorableBlocks().map { this.blocks[it] }
        val total = blocks.sumOf { it.size }
        val out = ByteArray(total)
        var offset = 0
        for (block in blocks) {
            block.copyInto(out, offset)
            offset += block.size
        }
        return out
    }

    override fun toString(): String =
        "MifareDump(${size.name}, uid=$uidHex, ${blocks.size} 块, 已知密钥 ${sectorKeys.size} 个扇区)"

    /** 按内容比较（块是 ByteArray，默认的引用比较在测试与缓存场景下都不合适）。 */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MifareDump) return false
        if (size != other.size) return false
        if (!uid.contentEquals(other.uid)) return false
        if (sectorKeys != other.sectorKeys) return false
        if (blocks.size != other.blocks.size) return false
        for (index in blocks.indices) {
            if (!blocks[index].contentEquals(other.blocks[index])) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = size.hashCode()
        result = 31 * result + uid.contentHashCode()
        result = 31 * result + sectorKeys.hashCode()
        for (block in blocks) result = 31 * result + block.contentHashCode()
        return result
    }
}
