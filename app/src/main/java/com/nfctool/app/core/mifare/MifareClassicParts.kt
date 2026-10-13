package com.nfctool.app.core.mifare

import com.nfctool.app.core.bytes.Bytes
import com.nfctool.app.core.bytes.Hex

/** 一个扇区的密钥（KeyA / KeyB 各 6 字节，可能只探明其一）。 */
class MifareSectorKeys(
    val keyA: ByteArray?,
    val keyB: ByteArray?,
) {
    init {
        keyA?.let { require(it.size == KEY_LENGTH) { "KeyA 长度必须为 $KEY_LENGTH 字节" } }
        keyB?.let { require(it.size == KEY_LENGTH) { "KeyB 长度必须为 $KEY_LENGTH 字节" } }
    }

    fun describe(): String = buildString {
        append("A=").append(keyA?.let { Hex.encode(it) } ?: "未探明")
        append(" B=").append(keyB?.let { Hex.encode(it) } ?: "未探明")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MifareSectorKeys) return false
        return keyA.contentEqualsNullable(other.keyA) && keyB.contentEqualsNullable(other.keyB)
    }

    override fun hashCode(): Int = 31 * (keyA?.contentHashCode() ?: 0) + (keyB?.contentHashCode() ?: 0)

    override fun toString(): String = "MifareSectorKeys(${describe()})"

    companion object {
        const val KEY_LENGTH = 6

        /** 出厂默认密钥，MIFARE Classic 最常见的初始值。 */
        val FACTORY_DEFAULT = Hex.decode("FFFFFFFFFFFF")

        /** MAD（Mifare Application Directory）密钥，用于公开目录扇区。 */
        val MAD_KEY = Hex.decode("A0A1A2A3A4A5")

        /** NFC Forum 为 NDEF 数据指定的密钥。 */
        val NDEF_KEY = Hex.decode("D3F7D3F7D3F7")

        /** 常见空密钥。 */
        val ZERO_KEY = Hex.decode("000000000000")

        /**
         * 探测顺序：出厂默认 → NDEF → MAD → 全零。
         * 顺序按实际卡片出现频率排列，命中即停止。
         */
        val CANDIDATES: List<ByteArray> = listOf(FACTORY_DEFAULT, NDEF_KEY, MAD_KEY, ZERO_KEY)
    }
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
    this == null -> other == null
    other == null -> false
    else -> contentEquals(other)
}

/** 扇区尾块（KeyA + 访问位 + KeyB）的解析结果。 */
class MifareSectorTrailer(
    val keyA: ByteArray,
    val accessBits: ByteArray,
    val keyB: ByteArray,
) {
    init {
        require(keyA.size == MifareSectorKeys.KEY_LENGTH) { "KeyA 必须为 6 字节" }
        require(accessBits.size == 4) { "访问位必须为 4 字节" }
        require(keyB.size == MifareSectorKeys.KEY_LENGTH) { "KeyB 必须为 6 字节" }
    }

    fun describe(): String =
        "KeyA=${Hex.encode(keyA)} Access=${Hex.encode(accessBits)} KeyB=${Hex.encode(keyB)}"

    companion object {
        const val TRAILER_SIZE = 16

        /** 按 MIFARE Classic 尾块布局拆分：KeyA(6) + 访问位(4) + KeyB(6)。 */
        fun parse(block: ByteArray): MifareSectorTrailer {
            require(block.size == TRAILER_SIZE) { "扇区尾块必须为 $TRAILER_SIZE 字节，实际 ${block.size} 字节" }
            return MifareSectorTrailer(
                keyA = block.copyOfRange(0, 6),
                accessBits = block.copyOfRange(6, 10),
                keyB = block.copyOfRange(10, 16),
            )
        }

        /** 按同一布局拼回 16 字节尾块。 */
        fun compose(trailer: MifareSectorTrailer): ByteArray =
            Bytes.concat(trailer.keyA, trailer.accessBits, trailer.keyB)
    }
}

/** 厂商块（块 0）解析结果：UID + BCC + SAK + ATQA + 厂商数据。 */
class MifareManufacturerBlock(
    val uid: ByteArray,
    val bcc: Byte,
    val sak: Byte,
    val atqa: ByteArray,
    val manufacturerData: ByteArray,
) {
    /** UID 的十六进制表示，用于展示。 */
    val uidHex: String get() = Hex.encode(uid)

    companion object {
        const val BLOCK_SIZE = 16

        /**
         * 解析厂商块。
         *
         * 单倍长 UID（4 字节）时布局为：UID(4) BCC(1) SAK(1) ATQA(2) 厂商数据(8)。
         * 7 字节 UID（双倍长）的布局在 MIFARE Classic 上不适用（其 UID 恒为 4 字节），
         * 因此这里只处理 4 字节 UID 的形态。
         */
        fun parse(block: ByteArray): MifareManufacturerBlock {
            require(block.size == BLOCK_SIZE) { "厂商块必须为 $BLOCK_SIZE 字节，实际 ${block.size} 字节" }
            return MifareManufacturerBlock(
                uid = block.copyOfRange(0, 4),
                bcc = block[4],
                sak = block[5],
                atqa = block.copyOfRange(6, 8),
                manufacturerData = block.copyOfRange(8, 16),
            )
        }

        /** UID 的 BCC 校验：四个字节逐字节异或。 */
        fun computeBcc(uid: ByteArray): Byte {
            require(uid.size == 4) { "单倍长 UID 必须为 4 字节，实际 ${uid.size} 字节" }
            var value = 0
            for (b in uid) value = value xor (b.toInt() and 0xFF)
            return value.toByte()
        }
    }
}
