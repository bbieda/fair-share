package com.fairshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fairshare.data.AppSettings
import com.fairshare.data.model.Money
import com.fairshare.ui.navigation.FairShareApp
import com.fairshare.ui.screens.LogowanieScreen
import com.fairshare.ui.theme.FairShareTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = application as FairShareApplication
        // While the app is on screen: sync now and then every 30 s, so the others' changes show up by themselves
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    app.sync.syncNow()
                    delay(SYNC_INTERVAL_MS)
                }
            }
        }
        setContent {
            val account by app.session.account.collectAsStateWithLifecycle()
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
                if (account == null) LogowanieScreen() else FairShareApp()
            }
        }
    }

    private companion object {
        const val SYNC_INTERVAL_MS = 30_000L
    }
}
