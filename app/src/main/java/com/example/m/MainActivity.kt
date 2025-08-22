package com.example.m

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
import com.example.m.ui.main.MainScreen
import com.example.m.ui.main.MainViewModel
import com.example.m.ui.theme.MTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            LaunchedEffect(Unit) {
                mainViewModel.checkAndResumeDownloadQueue()
            }

            MTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Pass the same ViewModel instance to MainScreen
                    MainScreen(mainViewModel = mainViewModel)
                }
            }
        }
    }
}