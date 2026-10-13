package com.nfctool.app.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Bundle

/**
 * 读卡开关。
 *
 * 采用 `NfcAdapter.enableReaderMode`（API 19+）而不是
 * `enableForegroundDispatch`（已在 API 33 废弃），也不注册
 * NDEF_DISCOVERED / TECH_DISCOVERED 意图过滤器 —— 本应用只在自身处于前台时读卡，
 * 不抢占其它应用对标签的处理权。
 */
class NfcReaderModeController(
    private val activity: Activity,
    private val onTagDiscovered: (android.nfc.Tag) -> Unit,
) {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    /** 设备是否具备 NFC 硬件。 */
    val isSupported: Boolean get() = adapter != null

    /** NFC 硬件是否存在且已开启。 */
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    private val readerCallback = NfcAdapter.ReaderCallback { tag -> onTagDiscovered(tag) }

    fun enable() {
        val adapter = adapter ?: return
        adapter.enableReaderMode(activity, readerCallback, READER_FLAGS, Bundle.EMPTY)
    }

    fun disable() {
        val adapter = adapter ?: return
        if (adapter.isEnabled) adapter.disableReaderMode(activity)
    }

    private companion object {
        /**
         * 关闭平台自带提示音：提示音与振动由应用按用户设置自行控制，
         * 否则会出现"设置里关了振动但读卡仍有声响"的不一致。
         */
        val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
    }
}
