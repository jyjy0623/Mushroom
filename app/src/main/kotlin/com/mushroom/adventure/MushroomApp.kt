package com.mushroom.adventure

import android.app.Application
import com.mushroom.core.logging.LogFileWriter
import com.mushroom.core.logging.LogWriter
import com.mushroom.core.logging.MushroomLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MushroomApp : Application() {

    @Inject lateinit var logWriter: LogWriter
    @Inject lateinit var logFileWriter: LogFileWriter

    override fun onCreate() {
        super.onCreate()
        MushroomLogger.init(logWriter)
        logFileWriter.writeSessionStart()
        MushroomLogger.i("APP", "MushroomApp.onCreate — application started")
    }
}
