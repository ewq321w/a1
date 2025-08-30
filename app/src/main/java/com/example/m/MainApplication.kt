package com.example.m

import android.app.Application
import com.example.m.util.CrashHandler
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named

@HiltAndroidApp
class MainApplication : Application() {

    @Inject
    @Named("NewPipe")
    lateinit var downloader: Downloader

    override fun onCreate() {
        super.onCreate()
        // Set up the crash handler
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        val defaultLocale = Locale.getDefault()
        val localization = Localization(defaultLocale.country, defaultLocale.language)
        NewPipe.init(downloader, localization)
    }
}
