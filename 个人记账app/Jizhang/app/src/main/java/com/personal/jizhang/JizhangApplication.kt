package com.personal.jizhang

import android.app.Application
import com.personal.jizhang.di.AppContainer

class JizhangApplication : Application() {
    val container by lazy { AppContainer(this) }
}
