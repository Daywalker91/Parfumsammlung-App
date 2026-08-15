package com.daywalker91.parfumsammlung

import android.app.Application
import com.daywalker91.parfumsammlung.di.AppContainer

class AromathekApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
