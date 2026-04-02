package com.range.phoneLinuxer

import android.app.Application
import com.range.phoneLinuxer.data.executor.EmulatorExecutor
import timber.log.Timber

class PhoneLinuxerApp : Application() {

    lateinit var executor: EmulatorExecutor
        private set

    override fun onCreate() {
        super.onCreate()
        executor = EmulatorExecutor(this)
        Timber.i("PhoneLinuxerApp: Global EmulatorExecutor initialized.")
    }
}
