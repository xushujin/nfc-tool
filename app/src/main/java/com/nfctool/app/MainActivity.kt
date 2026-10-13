package com.nfctool.app

import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nfctool.app.nfc.NfcReaderModeController
import com.nfctool.app.ui.AppRoot
import com.nfctool.app.ui.NfcToolViewModel
import com.nfctool.app.ui.theme.NfcToolTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: NfcToolViewModel
    private lateinit var readerController: NfcReaderModeController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as NfcToolApplication).container
        viewModel = ViewModelProvider(
            this,
            NfcToolViewModel.Factory(container),
        )[NfcToolViewModel::class.java]

        readerController = NfcReaderModeController(this) { tag -> handleTag(tag) }

        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            NfcToolTheme(themeId = state.settings.themeId) {
                AppRoot(state = state, viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // NFC 开关状态在进入前台时同步一次；应用在前台期间用户去系统设置里改动开关，
        // 需要下次回到前台才会反映，这里不做常驻监听以免多占一个系统回调。
        viewModel.updateNfcAvailability(
            supported = readerController.isSupported,
            enabled = readerController.isEnabled,
        )
        if (readerController.isEnabled) {
            readerController.enable()
        }
    }

    override fun onPause() {
        readerController.disable()
        super.onPause()
    }

    private fun handleTag(tag: Tag) {
        viewModel.onTagDiscovered(tag)
        if (viewModel.uiState.value.settings.vibrateOnScan) {
            vibrateOnce()
        }
    }

    private fun vibrateOnce() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        } ?: return
        vibrator.vibrate(
            VibrationEffect.createOneShot(VIBRATE_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE),
        )
    }

    private companion object {
        const val VIBRATE_MILLIS = 45L
    }
}
