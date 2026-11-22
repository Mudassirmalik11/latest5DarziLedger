package com.example.features.orders

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OrderMediaStore(private val context: Context) {
  private val prefs by lazy {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }
  private val json = Json { ignoreUnknownKeys = true }
  private val refreshSignal = MutableStateFlow(0L)

  fun observeMediaUris(orderId: String): Flow<List<String>> =
    refreshSignal.map { loadUris(orderId) }

  fun addMediaUris(orderId: String, uris: List<Uri>) {
    if (uris.isEmpty()) return
    val current = loadUris(orderId).toMutableList()
    uris.forEach { uri ->
      runCatching {
        context.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
      }
      val raw = uri.toString()
      if (raw.isNotBlank() && raw !in current) {
        current.add(raw)
      }
    }
    saveUris(orderId, current)
  }

  fun removeMediaUri(orderId: String, uriString: String) {
    val current = loadUris(orderId).filterNot { it == uriString }
    saveUris(orderId, current)
  }

  private fun loadUris(orderId: String): List<String> {
    val raw = prefs.getString(keyFor(orderId), null) ?: return emptyList()
    return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
  }

  private fun saveUris(orderId: String, uris: List<String>) {
    prefs.edit().putString(keyFor(orderId), json.encodeToString(uris)).apply()
    refreshSignal.value += 1L
  }

  private fun keyFor(orderId: String): String = "order_media_$orderId"

  private companion object {
    const val PREFS_NAME = "darzi_order_media"
  }
}
