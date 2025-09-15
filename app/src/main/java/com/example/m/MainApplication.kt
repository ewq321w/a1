package com.example.m

import android.app.Application
import com.example.m.util.CrashHandler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.localization.Localization
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named

@HiltAndroidApp
class MainApplication : Application() {

    @Inject
    @Named("NewPipe")
    lateinit var downloader: Downloader

    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Set up the crash handler
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        // Launch NewPipe initialization in a background coroutine to avoid blocking the main thread
        applicationScope.launch {
            val defaultLocale = Locale.getDefault()
            val localization = Localization(defaultLocale.country, defaultLocale.language)
            NewPipe.init(downloader, localization)
        }
    }
}