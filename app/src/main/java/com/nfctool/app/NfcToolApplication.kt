package com.nfctool.app

import android.app.Application
import com.nfctool.app.data.AppContainer

class NfcToolApplication : Application() {

    /** 手工依赖容器，全局唯一。 */
    val container: AppContainer by lazy { AppContainer(this) }
}
