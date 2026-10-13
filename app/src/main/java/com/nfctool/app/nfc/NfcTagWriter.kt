package com.nfctool.app.nfc

import android.nfc.FormatException
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.MifareClassic
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.nfctool.app.core.capacity.CapacityCheck
import com.nfctool.app.core.capacity.CapacityResult
import com.nfctool.app.core.mifare.MifareClassicLayout
import com.nfctool.app.core.mifare.MifareClassicSize
import com.nfctool.app.core.mifare.MifareDump
import com.nfctool.app.core.mifare.MifareSectorKeys
import com.nfctool.app.core.ndef.NdefMessage
import java.io.IOException

/** 写入结果。失败时给出可读原因，界面直接展示，不做二次包装。 */
sealed interface WriteOutcome {

    class Success(val detail: String) : WriteOutcome

    /** 部分成功：例如 MIFARE 回写时若干块失败。 */
    class Partial(val detail: String, val failures: List<String>) : WriteOutcome

    class Failure(val reason: String) : WriteOutcome
}

/** 向标签写入 NDEF 数据与 MIFARE Classic 转储。 */
object NfcTagWriter {

    /**
     * 写入一条 NDEF 消息。
     *
     * 若标签尚未格式化为 NDEF，则尝试用 [NdefFormatable] 先格式化再写入。
     */
    fun writeNdef(tag: Tag, message: NdefMessage): WriteOutcome {
        val ndef = Ndef.get(tag)
        if (ndef == null) return formatAndWrite(tag, message)

        return try {
            ndef.connect()
            if (!ndef.isWritable) {
                return WriteOutcome.Failure("标签已被锁定为只读，无法写入")
            }
            val capacity = ndef.maxSize
            when (val check = CapacityCheck.check(message, capacity)) {
                is CapacityResult.Exceeds -> return WriteOutcome.Failure(
                    "内容需要 ${check.usedBytes} 字节，标签仅能容纳 $capacity 字节，" +
                        "超出 ${check.overflowBytes} 字节",
                )

                is CapacityResult.Fits -> Unit
                is CapacityResult.Unknown -> Unit
            }
            ndef.writeNdefMessage(NdefBridge.toPlatform(message))
            WriteOutcome.Success("已写入 ${message.encodedSize} 字节（标签容量 $capacity 字节）")
        } catch (e: FormatException) {
            WriteOutcome.Failure("标签拒绝该 NDEF 数据：${e.message ?: "格式非法"}")
        } catch (e: TagLostException) {
            WriteOutcome.Failure("写入过程中标签离开感应区，请贴稳后重试")
        } catch (e: IOException) {
            WriteOutcome.Failure("写入失败：${e.message ?: "IO 错误"}")
        } finally {
            NfcTagReader.closeQuietly(ndef)
        }
    }

    private fun formatAndWrite(tag: Tag, message: NdefMessage): WriteOutcome {
        val formatable = NdefFormatable.get(tag)
            ?: return WriteOutcome.Failure(
                "该标签既不是 NDEF 标签，也不支持格式化写入（可能已被锁定或为非标准标签）",
            )
        return try {
            formatable.connect()
            formatable.format(NdefBridge.toPlatform(message))
            WriteOutcome.Success("标签已格式化并写入 ${message.encodedSize} 字节")
        } catch (e: FormatException) {
            WriteOutcome.Failure("格式化失败：${e.message ?: "数据格式非法"}")
        } catch (e: TagLostException) {
            WriteOutcome.Failure("格式化过程中标签离开感应区，请贴稳后重试")
        } catch (e: IOException) {
            WriteOutcome.Failure("格式化失败：${e.message ?: "IO 错误"}")
        } finally {
            NfcTagReader.closeQuietly(formatable)
        }
    }

    /** 将标签永久锁定为只读。此操作不可逆。 */
    fun makeReadOnly(tag: Tag): WriteOutcome {
        val ndef = Ndef.get(tag) ?: return WriteOutcome.Failure("该标签不支持 NDEF，无法锁定")
        return try {
            ndef.connect()
            if (!ndef.canMakeReadOnly()) return WriteOutcome.Failure("该标签不支持永久锁定")
            if (ndef.makeReadOnly()) {
                WriteOutcome.Success("标签已锁定为只读（不可撤销）")
            } else {
                WriteOutcome.Failure("锁定未成功，标签可能已处于只读状态")
            }
        } catch (e: TagLostException) {
            WriteOutcome.Failure("锁定过程中标签离开感应区")
        } catch (e: IOException) {
            WriteOutcome.Failure("锁定失败：${e.message ?: "IO 错误"}")
        } finally {
            NfcTagReader.closeQuietly(ndef)
        }
    }

    /**
     * 把转储回写到目标 MIFARE Classic 卡片。
     *
     * 只写数据块：厂商块（块 0）出厂只读，扇区尾块（含访问位）写错会导致扇区永久不可读，
     * 这两类块一律跳过。密钥候选取源卡探明的密钥，其次退回到常见默认密钥。
     */
    fun restoreMifare(tag: Tag, dump: MifareDump): WriteOutcome {
        val classic = MifareClassic.get(tag)
            ?: return WriteOutcome.Failure(
                // 说清楚"读到的目标卡是什么技术"，用户才知道该换成哪种卡
                "目标标签不是 MIFARE Classic —— 手机在目标卡上读到的是：" +
                    tag.techList.joinToString(" / ") { it.substringAfterLast('.') }
                        .ifEmpty { "未知技术" } +
                    "。整卡回写要求目标卡也是 MIFARE Classic，且容量与源卡（" +
                    dump.size.label.substringBefore("（") + "）一致。",
            )
        val targetSize = MifareClassicSize.fromByteCount(classic.size)
            ?: return WriteOutcome.Failure("无法识别目标标签容量：${classic.size} 字节")
        if (targetSize != dump.size) {
            return WriteOutcome.Failure(
                "容量不一致：源卡为 ${dump.size.label}，目标卡为 ${targetSize.label}。" +
                    "请换一张与源卡同容量的 MIFARE Classic 卡。",
            )
        }

        val failures = ArrayList<String>()
        var writtenBlocks = 0
        var skippedSectors = 0

        return try {
            classic.connect()
            for (sector in 0 until dump.size.sectorCount) {
                if (!authenticateWith(classic, sector, dump.sectorKeysOf(sector))) {
                    skippedSectors++
                    failures.add("扇区 $sector：密钥未知，整扇区跳过")
                    continue
                }
                val first = MifareClassicLayout.firstBlockOfSector(dump.size, sector)
                val count = MifareClassicLayout.blocksInSector(dump.size, sector)
                for (offset in 0 until count) {
                    val index = first + offset
                    if (!MifareClassicLayout.isWritableDataBlock(dump.size, index)) continue
                    try {
                        classic.writeBlock(index, dump.block(index))
                        writtenBlocks++
                    } catch (e: IOException) {
                        failures.add("块 $index：写入失败（${e.message ?: "IO 错误"}）")
                    }
                }
            }
            val skippedNote = "，另有 ${MifareClassicLayout.dataBlocks(dump.size).size - writtenBlocks} 块未写入"
            if (failures.isEmpty()) {
                WriteOutcome.Success("已回写 $writtenBlocks 个数据块$skippedNote")
            } else {
                WriteOutcome.Partial(
                    detail = "已回写 $writtenBlocks 个数据块，${failures.size} 项未完成" +
                        if (skippedSectors > 0) "（其中 $skippedSectors 个扇区密钥未知）" else "",
                    failures = failures,
                )
            }
        } catch (e: TagLostException) {
            WriteOutcome.Failure("回写过程中标签离开感应区")
        } catch (e: IOException) {
            WriteOutcome.Failure("回写失败：${e.message ?: "IO 错误"}")
        } finally {
            NfcTagReader.closeQuietly(classic)
        }
    }

    private fun authenticateWith(
        classic: MifareClassic,
        sector: Int,
        keys: MifareSectorKeys?,
    ): Boolean {
        val candidates = ArrayList<ByteArray>(4)
        keys?.keyA?.let { candidates.add(it) }
        keys?.keyB?.let { candidates.add(it) }
        candidates.addAll(MifareSectorKeys.CANDIDATES)

        for (candidate in candidates) {
            val okA = try {
                classic.authenticateSectorWithKeyA(sector, candidate)
            } catch (_: IOException) {
                return false
            }
            if (okA) return true
        }
        for (candidate in candidates) {
            val okB = try {
                classic.authenticateSectorWithKeyB(sector, candidate)
            } catch (_: IOException) {
                return false
            }
            if (okB) return true
        }
        return false
    }
}
