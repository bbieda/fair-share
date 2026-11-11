package com.fairshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fairshare.data.AppSettings
import com.fairshare.data.model.Money
import com.fairshare.ui.navigation.FairShareApp
import com.fairshare.ui.theme.FairShareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = application as FairShareApplication
        setContent {
            val themeMode by app.settings.themeMode.collectAsStateWithLifecycle()
            val me by app.repository.me.collectAsStateWithLifecycle(initialValue = null)
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                AppSettings.THEME_LIGHT -> false
                AppSettings.THEME_SYSTEM -> systemDark
                else -> true
            }
            // Money.currencyCode is Compose state, so every screen redraws when the currency changes
            val currency = me?.defaultCurrency ?: "PLN"
            LaunchedEffect(currency) { Money.currencyCode = currency }
            FairShareTheme(darkTheme = dark) {
                FairShareApp()
            }
        }
    }
}
