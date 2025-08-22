package com.example.m

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application() {

    @Inject
    lateinit var downloader: Downloader // Inject the downloader from our NetworkModule

    override fun onCreate() {
        super.onCreate()
        val defaultLocale = Locale.getDefault()
        val localization = Localization(defaultLocale.country, defaultLocale.language)
        NewPipe.init(downloader, localization) // Use the injected downloader
    }
}