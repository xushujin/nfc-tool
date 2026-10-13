package com.nfctool.app.core.ndef

/** 一条 NDEF 消息（至少一条记录）。 */
class NdefMessage(val records: List<NdefRecord>) {

    init {
        require(records.isNotEmpty()) { "NDEF 消息至少需要一条记录" }
    }

    /** 编码后的字节数，用于写入前的容量校验。 */
    val encodedSize: Int get() = NdefCodec.encode(this).size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NdefMessage) return false
        return records == other.records
    }

    override fun hashCode(): Int = records.hashCode()

    override fun toString(): String = "NdefMessage(${records.size} 条记录, $encodedSize 字节)"

    companion object {
        fun of(vararg records: NdefRecord): NdefMessage = NdefMessage(records.toList())
    }
}
