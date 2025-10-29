package com.example.m

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.playback.MusicService
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.main.MainScreen
import com.example.m.ui.main.MainViewModel
import com.example.m.ui.theme.MTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var musicServiceConnection: MusicServiceConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Explicitly start the MusicService to ensure it's running
        // This prevents the issue where the service doesn't start on app launch
        try {
            val serviceIntent = Intent(this, MusicService::class.java)
            startService(serviceIntent)
            Timber.d("MusicService explicitly started from MainActivity")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start MusicService")
        }

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            LaunchedEffect(Unit) {
                mainViewModel.checkAndResumeDownloadQueue()
                // Ensure service connection is initialized
                mainViewModel.ensureMusicServiceConnected()
            }

            MTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't stop the service here - let it manage its own lifecycle
        // based on whether there's media playing
    }
}