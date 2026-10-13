package com.nfctool.app.nfc

import android.nfc.FormatException
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.MifareClassic
import android.nfc.tech.Ndef
import android.nfc.tech.TagTechnology
import com.nfctool.app.core.mifare.MifareClassicLayout
import com.nfctool.app.core.mifare.MifareClassicSize
import com.nfctool.app.core.mifare.MifareDump
import com.nfctool.app.core.mifare.MifareSectorKeys
import com.nfctool.app.core.model.MifareClassicInfo
import com.nfctool.app.core.model.NdefInfo
import com.nfctool.app.core.model.TagSnapshot
import com.nfctool.app.core.ndef.NdefCodec
import com.nfctool.app.core.ndef.NdefFormatException
import com.nfctool.app.core.ndef.NdefMessage
import com.nfctool.app.core.ndef.NdefTlv
import java.io.IOException

/**
 * 把平台 [Tag] 读成核心层 [TagSnapshot]。
 *
 * 读取过程中的问题不抛异常，而是收进 `warnings`：读卡失败的原因
 * （密钥未知、标签离开感应区、格式非法）需要如实展示给用户，
 * 而不是变成一个笼统的"读取失败"。
 */
object NfcTagReader {

    fun snapshot(tag: Tag, readMifareDump: Boolean): TagSnapshot {
        val warnings = ArrayList<String>()
        return TagSnapshot(
            uid = tag.id ?: ByteArray(0),
            technologies = tag.techList.map { it.substringAfterLast('.') }.sorted(),
            ndef = readNdef(tag, warnings),
            mifareClassic = if (readMifareDump) readMifareClassic(tag, warnings) else null,
            warnings = warnings,
        )
    }

    private fun readNdef(tag: Tag, warnings: MutableList<String>): NdefInfo? {
        val ndef = Ndef.get(tag) ?: return null
        return try {
            ndef.connect()
            NdefInfo(
                message = ndef.ndefMessage?.let(NdefBridge::toCore),
                writable = ndef.isWritable,
                maxSizeBytes = ndef.maxSize,
                canMakeReadOnly = ndef.canMakeReadOnly(),
            )
        } catch (e: FormatException) {
            warnings.add("NDEF 数据格式非法：${e.message ?: "无详细信息"}")
            null
        } catch (e: TagLostException) {
            warnings.add("读取 NDEF 时标签离开感应区")
            null
        } catch (e: IOException) {
            warnings.add("读取 NDEF 失败：${e.message ?: "IO 错误"}")
            null
        } finally {
            closeQuietly(ndef)
        }
    }

    private fun readMifareClassic(tag: Tag, warnings: MutableList<String>): MifareClassicInfo? {
        // 用户既然打开了「读取 MIFARE 转储」开关，读不到就必须说明原因，
        // 否则复制页只会出现一个点不动的灰按钮，用户无从判断是卡不对还是手机不支持。
        val classic = MifareClassic.get(tag) ?: run {
            warnings.add(
                "本次未取得 MIFARE Classic 数据：这张卡不是 MIFARE Classic" +
                    "（银行卡、公交卡、CPU 卡以及不少门禁卡用的是别的芯片），" +
                    "或本机 NFC 芯片不支持 MIFARE Classic。这类卡无法整卡复制。",
            )
            return null
        }
        val size = MifareClassicSize.fromByteCount(classic.size)
        if (size == null) {
            warnings.add("无法识别的 MIFARE Classic 容量：${classic.size} 字节")
            return null
        }
        return try {
            classic.connect()
            val blocks = arrayOfNulls<ByteArray>(size.blockCount)
            val readable = ArrayList<Int>()
            val unreadable = ArrayList<Int>()
            val keys = LinkedHashMap<Int, MifareSectorKeys>()

            for (sector in 0 until size.sectorCount) {
                val sectorKeys = authenticate(classic, sector)
                if (sectorKeys == null) {
                    unreadable.add(sector)
                    continue
                }
                keys[sector] = sectorKeys
                if (readSector(classic, size, sector, blocks)) {
                    readable.add(sector)
                } else {
                    unreadable.add(sector)
                }
            }

            val complete = unreadable.isEmpty()
            if (!complete) {
                warnings.add(
                    "有 ${unreadable.size} 个扇区的密钥未知（扇区 ${unreadable.joinToString("、")}），" +
                        "未能取得完整转储，整卡复制不可用",
                )
            }
            val dump = if (complete) buildDump(size, blocks, keys) else null
            MifareClassicInfo(
                size = size,
                dump = dump,
                readableSectors = readable,
                unreadableSectors = unreadable,
                embeddedNdef = dump?.let(::extractEmbeddedNdef),
            )
        } catch (e: TagLostException) {
            warnings.add("读取 MIFARE Classic 时标签离开感应区")
            null
        } catch (e: IOException) {
            warnings.add("读取 MIFARE Classic 失败：${e.message ?: "IO 错误"}")
            null
        } finally {
            closeQuietly(classic)
        }
    }

    private fun readSector(
        classic: MifareClassic,
        size: MifareClassicSize,
        sector: Int,
        blocks: Array<ByteArray?>,
    ): Boolean {
        val first = MifareClassicLayout.firstBlockOfSector(size, sector)
        val count = MifareClassicLayout.blocksInSector(size, sector)
        for (offset in 0 until count) {
            val index = first + offset
            val data = try {
                classic.readBlock(index)
            } catch (_: IOException) {
                return false
            }
            if (data.size != MifareClassicLayout.BLOCK_SIZE) return false
            blocks[index] = data
        }
        return true
    }

    private fun buildDump(
        size: MifareClassicSize,
        blocks: Array<ByteArray?>,
        keys: Map<Int, MifareSectorKeys>,
    ): MifareDump {
        val ordered = blocks.map { requireNotNull(it) { "内部错误：完整转储中出现了空块" } }
        return MifareDump(
            size = size,
            uid = ordered[0].copyOfRange(0, 4),
            blocks = ordered,
            sectorKeys = keys,
        )
    }

    /** 从转储的数据块中尽力解析 NDEF 消息；解析不出来就返回 null，不影响其余信息。 */
    private fun extractEmbeddedNdef(dump: MifareDump): NdefMessage? {
        val tlvBytes = NdefTlv.findNdefMessage(dump.concatenatedDataBlocks()) ?: return null
        return try {
            NdefCodec.decode(tlvBytes)
        } catch (_: NdefFormatException) {
            null
        }
    }

    /**
     * 逐扇区尝试认证。
     *
     * 先试 KeyA 再试 KeyB，密钥候选按实际卡片出现频率排序
     * （出厂默认 → NDEF → MAD → 全零），命中即停。
     */
    private fun authenticate(classic: MifareClassic, sector: Int): MifareSectorKeys? {
        for (candidate in MifareSectorKeys.CANDIDATES) {
            val ok = try {
                classic.authenticateSectorWithKeyA(sector, candidate)
            } catch (_: IOException) {
                return null
            }
            if (ok) return MifareSectorKeys(keyA = candidate, keyB = null)
        }
        for (candidate in MifareSectorKeys.CANDIDATES) {
            val ok = try {
                classic.authenticateSectorWithKeyB(sector, candidate)
            } catch (_: IOException) {
                return null
            }
            if (ok) return MifareSectorKeys(keyA = null, keyB = candidate)
        }
        return null
    }

    internal fun closeQuietly(tech: TagTechnology?) {
        try {
            tech?.close()
        } catch (_: IOException) {
            // 关闭失败不影响已读到的数据，忽略
        }
    }
}
