package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ConnectivityMonitor {
  val isOnline: StateFlow<Boolean>
}

class AndroidConnectivityMonitor(
  context: Context,
) : ConnectivityMonitor {

  private val connectivityManager =
    context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

  private val _isOnline = MutableStateFlow(checkCurrentlyOnline())
  override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  private val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
      _isOnline.value = checkCurrentlyOnline()
    }

    override fun onLost(network: Network) {
      _isOnline.value = checkCurrentlyOnline()
    }

    override fun onCapabilitiesChanged(
      network: Network,
      networkCapabilities: NetworkCapabilities,
    ) {
      _isOnline.value = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
  }

  init {
    runCatching {
      val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
      connectivityManager?.registerNetworkCallback(request, networkCallback)
    }
  }

  private fun checkCurrentlyOnline(): Boolean {
    val cm = connectivityManager ?: return false
    return runCatching {
      val active = cm.activeNetwork ?: return false
      val caps = cm.getNetworkCapabilities(active) ?: return false
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)
  }
}
