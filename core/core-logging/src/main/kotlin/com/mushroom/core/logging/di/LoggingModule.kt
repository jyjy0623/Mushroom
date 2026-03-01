package com.mushroom.core.logging.di

import android.content.Context
import com.mushroom.core.logging.BuildConfig
import com.mushroom.core.logging.DebugLogWriter
import com.mushroom.core.logging.LogExporter
import com.mushroom.core.logging.LogFileWriter
import com.mushroom.core.logging.LogWriter
import com.mushroom.core.logging.ReleaseLogWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {

    @Provides
    @Singleton
    fun provideLogFileWriter(@ApplicationContext context: Context): LogFileWriter =
        LogFileWriter(context)

    @Provides
    @Singleton
    fun provideLogWriter(fileWriter: LogFileWriter): LogWriter =
        if (BuildConfig.DEBUG) DebugLogWriter(fileWriter) else ReleaseLogWriter(fileWriter)

    @Provides
    @Singleton
    fun provideLogExporter(
        @ApplicationContext context: Context,
        fileWriter: LogFileWriter
    ): LogExporter = LogExporter(context, fileWriter)
}
