package com.nfctool.app.core.ndef

import com.nfctool.app.core.bytes.Bytes
import com.nfctool.app.core.bytes.Hex

/**
 * 一条 NDEF 记录。
 *
 * 不使用 `android.nfc.NdefRecord`：核心编解码需要能在 JVM 单元测试中直接验证，
 * 而 `android.nfc.*` 在本地单元测试里是空实现（调用即抛异常）。
 */
class NdefRecord(
    val tnf: Int,
    val type: ByteArray,
    val id: ByteArray,
    val payload: ByteArray,
) {

    val typeAscii: String get() = Bytes.ascii(type)

    val isEmpty: Boolean get() = tnf == NdefTnf.EMPTY

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NdefRecord) return false
        return tnf == other.tnf &&
            type.contentEquals(other.type) &&
            id.contentEquals(other.id) &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = tnf
        result = 31 * result + type.contentHashCode()
        result = 31 * result + id.contentHashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    override fun toString(): String =
        "NdefRecord(tnf=${NdefTnf.name(tnf)}, type=$typeAscii, " +
            "id=${Hex.encode(id)}, payload=${payload.size}B)"

    companion object {
        val EMPTY_BYTES = ByteArray(0)

        fun empty(): NdefRecord = NdefRecord(NdefTnf.EMPTY, EMPTY_BYTES, EMPTY_BYTES, EMPTY_BYTES)
    }
}
