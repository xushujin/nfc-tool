package com.nfctool.app.core.mifare

import com.nfctool.app.core.bytes.Hex

/** 转储文本的格式。 */
enum class MifareDumpFormat {
    /** 本应用自有格式，带完整的容量 / 密钥 / UID 元信息。 */
    NFC_TOOL,

    /**
     * `+Sector: N` 分节 + 每行 16 字节十六进制的文本格式。
     *
     * 该格式是读卡工具社区的事实约定（Android 端 MIFARE Classic Tool 等使用），
     * 没有规范性文档可引用，因此本工程只按"扇区号从 0 连续递增、每行一个块"这一
     * 最小共识实现解析，其余以 `+` 开头的行一律忽略。
     */
    SECTOR_TEXT,

    UNKNOWN,
}

/**
 * MIFARE Classic 转储的文本编解码。
 *
 * 支持两种格式：
 * 1. 本应用自有格式（[MifareDumpFormat.NFC_TOOL]），可无损往返；
 * 2. `+Sector: N` 分节格式（[MifareDumpFormat.SECTOR_TEXT]），只读导入。
 *
 * 自有格式样例（`#` 开头为注释）：
 * ```
 * NFC-TOOL-MIFARE-DUMP
 * version=1
 * size=ONE_K
 * uid=4A1B2C3D
 * key.0=A:FFFFFFFFFFFF,B:FFFFFFFFFFFF
 * block.0=4A1B2C3D084800000000000000000000
 * block.1=...
 * ```
 */
object MifareDumpTextCodec {

    const val MAGIC = "NFC-TOOL-MIFARE-DUMP"
    const val VERSION = 1

    private const val KEY_LINE_PREFIX = "key."
    private const val BLOCK_LINE_PREFIX = "block."

    // ------------------------------------------------------------ 编码

    fun encodeNative(dump: MifareDump): String = buildString {
        appendLine(MAGIC)
        appendLine("version=$VERSION")
        appendLine("size=${dump.size.name}")
        appendLine("uid=${dump.uidHex}")
        for (sector in 0 until dump.size.sectorCount) {
            val keys = dump.sectorKeys[sector] ?: continue
            append(KEY_LINE_PREFIX).append(sector).append('=')
            append("A:").append(keys.keyA?.let { Hex.encode(it) } ?: "-")
            append(",B:").append(keys.keyB?.let { Hex.encode(it) } ?: "-")
            appendLine()
        }
        for (index in dump.blocks.indices) {
            append(BLOCK_LINE_PREFIX).append(index).append('=')
            append(Hex.encode(dump.blocks[index]))
            appendLine()
        }
    }

    /** 导出为 `+Sector: N` 分节格式，便于与其它读卡工具互通。 */
    fun encodeSectorText(dump: MifareDump): String = buildString {
        for (sector in 0 until dump.size.sectorCount) {
            appendLine("+Sector: $sector")
            val first = MifareClassicLayout.firstBlockOfSector(dump.size, sector)
            val count = MifareClassicLayout.blocksInSector(dump.size, sector)
            for (offset in 0 until count) {
                appendLine(Hex.encode(dump.blocks[first + offset]))
            }
        }
    }

    // ------------------------------------------------------------ 解码

    fun detectFormat(text: String): MifareDumpFormat {
        val trimmed = text.trimStart()
        return when {
            trimmed.startsWith(MAGIC) -> MifareDumpFormat.NFC_TOOL
            trimmed.lineSequence().any { it.trimStart().startsWith("+Sector:") } -> MifareDumpFormat.SECTOR_TEXT
            else -> MifareDumpFormat.UNKNOWN
        }
    }

    /** 自动识别格式并解析。无法识别或内容不完整时抛 [IllegalArgumentException]。 */
    fun decode(text: String): MifareDump = when (detectFormat(text)) {
        MifareDumpFormat.NFC_TOOL -> decodeNative(text)
        MifareDumpFormat.SECTOR_TEXT -> decodeSectorText(text)
        MifareDumpFormat.UNKNOWN -> throw IllegalArgumentException(
            "无法识别的转储格式：既不是 $MAGIC 开头，也找不到 +Sector 行",
        )
    }

    fun decodeNative(text: String): MifareDump {
        val lines = text.lineSequence().map { it.trim() }.toList()
        require(lines.firstOrNull() == MAGIC) { "缺少文件头 $MAGIC" }

        var declaredVersion: Int? = null
        var size: MifareClassicSize? = null
        var uid: ByteArray? = null
        var declaredBlockCount: Int? = null
        val keys = LinkedHashMap<Int, MifareSectorKeys>()
        val blocks = LinkedHashMap<Int, ByteArray>()

        for (line in lines) {
            if (line.isEmpty() || line.startsWith("#") || line == MAGIC) continue
            val separator = line.indexOf('=')
            if (separator <= 0) throw IllegalArgumentException("无法解析的行：$line")
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            when {
                key == "version" -> declaredVersion = value.toIntOrNull()
                    ?: throw IllegalArgumentException("version 不是整数：$value")

                key == "size" -> size = MifareClassicSize.entries.firstOrNull { it.name == value }
                    ?: throw IllegalArgumentException("未知的容量标识：$value")

                key == "uid" -> uid = Hex.decode(value)

                key == "blocks" -> declaredBlockCount = value.toIntOrNull()
                    ?: throw IllegalArgumentException("blocks 不是整数：$value")

                key.startsWith(KEY_LINE_PREFIX) -> {
                    val sector = key.removePrefix(KEY_LINE_PREFIX).toIntOrNull()
                        ?: throw IllegalArgumentException("无法解析扇区号：$key")
                    keys[sector] = parseKeys(value)
                }

                key.startsWith(BLOCK_LINE_PREFIX) -> {
                    val index = key.removePrefix(BLOCK_LINE_PREFIX).toIntOrNull()
                        ?: throw IllegalArgumentException("无法解析块号：$key")
                    val block = Hex.decode(value)
                    require(block.size == MifareClassicLayout.BLOCK_SIZE) {
                        "第 $index 块长度为 ${block.size}，应为 ${MifareClassicLayout.BLOCK_SIZE}"
                    }
                    blocks[index] = block
                }

                else -> throw IllegalArgumentException("未知的字段名：$key")
            }
        }

        require(declaredVersion == VERSION) {
            "转储版本为 $declaredVersion，本版本仅支持 $VERSION"
        }
        val resolvedSize = requireNotNull(size) { "缺少 size 字段" }
        if (declaredBlockCount != null) {
            require(declaredBlockCount == resolvedSize.blockCount) {
                "blocks=$declaredBlockCount 与 size=${resolvedSize.name}（应有 ${resolvedSize.blockCount} 块）不一致"
            }
        }
        require(blocks.size == resolvedSize.blockCount) {
            "实际块数 ${blocks.size} 与容量 ${resolvedSize.name}（应有 ${resolvedSize.blockCount} 块）不一致"
        }

        val orderedBlocks = (0 until resolvedSize.blockCount).map { index ->
            blocks[index] ?: throw IllegalArgumentException("缺少第 $index 块")
        }
        val resolvedUid = uid ?: orderedBlocks[0].copyOfRange(0, 4)

        return MifareDump(
            size = resolvedSize,
            uid = resolvedUid,
            blocks = orderedBlocks,
            sectorKeys = keys.toSortedMap(),
        )
    }

    fun decodeSectorText(text: String): MifareDump {
        val blocks = ArrayList<ByteArray>()
        var currentSector = -1
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            // 转储文件里常见的注释行（本工程生成的样本带两行说明）
            if (line.startsWith("#")) continue
            if (line.startsWith("+Sector:")) {
                val sector = line.substringAfter(':').trim().toIntOrNull()
                    ?: throw IllegalArgumentException("无法解析扇区号：$line")
                if (sector != currentSector + 1) {
                    throw IllegalArgumentException(
                        "扇区编号必须从 0 连续递增，期望 ${currentSector + 1}，实际 $sector",
                    )
                }
                currentSector = sector
                continue
            }
            if (line.startsWith("+")) continue // 其它元信息行（如 +UID）忽略
            val block = Hex.decode(line)
            require(block.size == MifareClassicLayout.BLOCK_SIZE) {
                "块长度为 ${block.size}，应为 ${MifareClassicLayout.BLOCK_SIZE}：$line"
            }
            blocks.add(block)
        }

        require(blocks.isNotEmpty()) { "文件中没有任何数据块" }
        val size = MifareClassicSize.fromByteCount(blocks.size * MifareClassicLayout.BLOCK_SIZE)
            ?: throw IllegalArgumentException(
                "块数 ${blocks.size} 无法对应已知的 MIFARE Classic 容量（Mini/1K/2K/4K）",
            )
        return MifareDump(
            size = size,
            uid = blocks[0].copyOfRange(0, 4),
            blocks = blocks,
        )
    }

    private fun parseKeys(value: String): MifareSectorKeys {
        var keyA: ByteArray? = null
        var keyB: ByteArray? = null
        for (part in value.split(',')) {
            val trimmed = part.trim()
            val name = trimmed.substringBefore(':', missingDelimiterValue = "")
            val hex = trimmed.substringAfter(':', missingDelimiterValue = "")
            if (hex.isEmpty() || hex == "-") continue
            val bytes = Hex.decode(hex)
            require(bytes.size == MifareSectorKeys.KEY_LENGTH) {
                "密钥长度必须为 ${MifareSectorKeys.KEY_LENGTH} 字节：$trimmed"
            }
            when (name) {
                "A" -> keyA = bytes
                "B" -> keyB = bytes
                else -> throw IllegalArgumentException("未知的密钥槽位：$trimmed")
            }
        }
        return MifareSectorKeys(keyA, keyB)
    }
}
