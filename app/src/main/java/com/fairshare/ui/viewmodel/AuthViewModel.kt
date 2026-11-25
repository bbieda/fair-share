package com.fairshare.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairshare.data.sync.ApiException
import com.fairshare.data.sync.SyncManager
import kotlinx.coroutines.launch
import java.io.IOException

/** Login and registration. When either succeeds the session changes and MainActivity shows the app. */
class AuthViewModel(private val sync: SyncManager) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val serverConfigured: Boolean get() = sync.serverConfigured

    fun login(email: String, password: String) = launchCall { sync.login(email, password) }

    fun register(email: String, password: String, name: String) = launchCall { sync.register(email, password, name) }

    fun clearError() {
        error = null
    }

    private fun launchCall(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                action()
            } catch (e: Exception) {
                error = e.toUserMessage()
            } finally {
                busy = false
            }
        }
    }
}

/** A message for the user from a failed server call. */
fun Exception.toUserMessage(): String = when (this) {
    is ApiException -> message ?: "Błąd serwera"
    is IOException -> "Brak połączenia z serwerem. Sprawdź internet i spróbuj ponownie."
    else -> message ?: "Coś poszło nie tak"
}
