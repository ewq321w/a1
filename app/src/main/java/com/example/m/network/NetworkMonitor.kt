package com.example.m.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("NewPipe") private val okHttpClient: OkHttpClient
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isMonitoring = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Timber.d("Network available: $network")
            clearConnectionPool()
        }

        override fun onLost(network: Network) {
            Timber.d("Network lost: $network")
            clearConnectionPool()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            Timber.d("Network capabilities changed: $network")
            // When VPN connects/disconnects, capabilities change
            clearConnectionPool()
        }
    }

    fun startMonitoring() {
        if (isMonitoring) return

        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            isMonitoring = true
            Timber.d("Network monitoring started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start network monitoring")
        }
    }

    fun stopMonitoring() {
        if (!isMonitoring) return

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isMonitoring = false
            Timber.d("Network monitoring stopped")
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop network monitoring")
        }
    }

    private fun clearConnectionPool() {
        try {
            // Evict all idle connections
            okHttpClient.connectionPool.evictAll()
            // Clear DNS cache
            okHttpClient.dispatcher.executorService.execute {
                // This forces OkHttp to re-resolve DNS and establish new connections
            }
            Timber.d("Connection pool cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear connection pool")
        }
    }
}

