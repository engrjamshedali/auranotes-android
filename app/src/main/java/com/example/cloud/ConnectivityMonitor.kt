package com.example.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object ConnectivityMonitor {
    fun observeNetwork(context: Context): Flow<CloudSyncCoordinator.NetworkAvailability> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(CloudSyncCoordinator.NetworkAvailability.OFFLINE)
            close()
            return@callbackFlow
        }

        fun currentState(): CloudSyncCoordinator.NetworkAvailability {
            val active = cm.activeNetwork ?: return CloudSyncCoordinator.NetworkAvailability.OFFLINE
            val caps = cm.getNetworkCapabilities(active) ?: return CloudSyncCoordinator.NetworkAvailability.OFFLINE
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return CloudSyncCoordinator.NetworkAvailability.OFFLINE
            }
            return if (
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                CloudSyncCoordinator.NetworkAvailability.UNMETERED_WIFI
            } else {
                CloudSyncCoordinator.NetworkAvailability.METERED_CELLULAR
            }
        }

        trySend(currentState())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentState())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(currentState())
            }

            override fun onLost(network: Network) {
                trySend(currentState())
            }
        }

        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            trySend(currentState())
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
    }.distinctUntilChanged()
}
