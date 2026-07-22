package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.data.SettingsManager
import com.example.ui.MainApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  val startMonitoringState = mutableStateOf(false)
  val callerIdState = mutableStateOf("")

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    handleIntent(intent)

    setContent {
      val context = LocalContext.current
      val settingsManager = remember { SettingsManager(context) }
      val themeMode by settingsManager.themeModeFlow.collectAsState(initial = "system")
      
      val isDarkTheme = when (themeMode) {
          "light" -> false
          "dark" -> true
          else -> isSystemInDarkTheme()
      }

      MyApplicationTheme(darkTheme = isDarkTheme) {
        MainApp(startMonitoringState.value, callerIdState.value) {
            startMonitoringState.value = false
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
      super.onNewIntent(intent)
      handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
      if (intent?.getBooleanExtra("start_monitoring", false) == true) {
          startMonitoringState.value = true
          callerIdState.value = intent.getStringExtra("caller_id") ?: "Unknown"
      }
  }
}

