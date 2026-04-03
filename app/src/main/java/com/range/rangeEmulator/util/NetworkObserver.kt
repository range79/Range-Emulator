package com.range.rangeEmulator.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class NetworkObserver(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val observe: Flow<Status> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                launch { send(Status.Available) }
            }

            override fun onLost(network: Network) {
                launch { send(Status.Lost) }
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
        val initialStatus = if (connectivityManager.activeNetwork != null) Status.Available else Status.Lost
        trySend(initialStatus)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    enum class Status { Available, Lost }
}