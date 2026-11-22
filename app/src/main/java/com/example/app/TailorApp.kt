package com.example.app

import android.app.Application

class TailorApp : Application() {
  lateinit var container: AppContainer

  override fun onCreate() {
    super.onCreate()
    container = DefaultAppContainer(this)
  }
}
