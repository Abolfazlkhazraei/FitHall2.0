package com.fithall.app.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.fithall.app.Connectivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Follows the default network. "Online" means Android reports a network that can reach the
 * internet; a captive portal can still fail requests, which the normal error handling covers.
 */
class AndroidConnectivity private constructor(context: Context) : Connectivity {

    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val online = MutableStateFlow(currentlyOnline())
    override val isOnline: StateFlow<Boolean> = online.asStateFlow()

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                online.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }

            override fun onLost(network: Network) {
                online.value = currentlyOnline()
            }
        })
    }

    private fun currentlyOnline(): Boolean =
        manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    companion object {
        @Volatile private var instance: AndroidConnectivity? = null

        /** One monitor for the whole app, so recreating the activity doesn't register more callbacks. */
        fun get(context: Context): AndroidConnectivity =
            instance ?: synchronized(this) { instance ?: AndroidConnectivity(context.applicationContext).also { instance = it } }
    }
}
