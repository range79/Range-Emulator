package com.range.rangeEmulator

import android.app.Application
import com.range.rangeEmulator.data.executor.EmulatorExecutor
import timber.log.Timber

class RangeEmulatorApp : Application() {

    lateinit var executor: EmulatorExecutor
        private set

    override fun onCreate() {
        super.onCreate()
        executor = EmulatorExecutor(this)
        Timber.i("RangeEmulatorApp: Global EmulatorExecutor initialized.")
    }
}
