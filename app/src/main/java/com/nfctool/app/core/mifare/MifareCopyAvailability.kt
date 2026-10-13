package com.nfctool.app.core.mifare

import com.nfctool.app.core.model.MifareClassicInfo

/**
 * 「整卡回写 MIFARE Classic」当前是否可用。
 *
 * 抽成纯函数是为了能单测：不可用时到底是「开关没开」「卡不是 MIFARE Classic」，
 * 还是「扇区密钥未知」，这三种情况用户要做的处理完全不同
 * （去开开关 / 换卡或换设备 / 该卡无法复制），必须分别判定并如实展示。
 * 只给一个点不动的灰按钮而不说明原因，用户只会以为功能坏了。
 */
sealed interface MifareCopyAvailability {

    /** 转储完整，可以整卡回写。 */
    data object Available : MifareCopyAvailability

    /** 快照里没有 MIFARE 数据：读卡时没打开「读取 MIFARE 整卡转储」开关。 */
    data object DumpNotRequested : MifareCopyAvailability

    /** 打开了开关，但没能识别为 MIFARE Classic：卡片类型不符，或本机 NFC 芯片不支持。 */
    data object NotMifareClassic : MifareCopyAvailability

    /** 识别到了 MIFARE Classic，但有扇区密钥未知，转储不完整，无法整卡复制。 */
    class IncompleteDump(val unreadableSectors: List<Int>) : MifareCopyAvailability

    companion object {

        /**
         * 判定当前可用性。
         *
         * 判定顺序有意为之：先看有没有完整转储，再看开关是否打开。
         * 开关没打开时 [info] 必然为 null，若先判 `info == null` 就会把
         * 「开关没开」误报成「卡类型不符」，把用户引到错误的方向。
         *
         * @param info 快照里的 MIFARE 读取结果，读不到为 null
         * @param readDumpRequested 读卡时是否打开了「读取 MIFARE 整卡转储」开关
         */
        fun of(info: MifareClassicInfo?, readDumpRequested: Boolean): MifareCopyAvailability = when {
            info?.dump != null -> Available
            !readDumpRequested -> DumpNotRequested
            info == null -> NotMifareClassic
            else -> IncompleteDump(info.unreadableSectors)
        }
    }
}
