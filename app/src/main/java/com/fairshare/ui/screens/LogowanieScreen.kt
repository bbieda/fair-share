package com.fairshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fairshare.ui.theme.FairShareTheme
import com.fairshare.ui.viewmodel.AppViewModelProvider
import com.fairshare.ui.viewmodel.AuthViewModel

/** Shown instead of the app while nobody is logged in. */
@Composable
fun LogowanieScreen(viewModel: AuthViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    LogowanieContent(
        busy = viewModel.busy,
        error = viewModel.error,
        serverConfigured = viewModel.serverConfigured,
        onLogin = viewModel::login,
        onRegister = viewModel::register,
        onInputChanged = viewModel::clearError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogowanieContent(
    busy: Boolean,
    error: String?,
    serverConfigured: Boolean,
    onLogin: (email: String, password: String) -> Unit = { _, _ -> },
    onRegister: (email: String, password: String, name: String) -> Unit = { _, _, _ -> },
    onInputChanged: () -> Unit = {},
) {
    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val canSubmit = !busy && serverConfigured && email.isNotBlank() && password.isNotEmpty() && (!register || name.isNotBlank())
    val submit = {
        if (canSubmit) {
            if (register) onRegister(email, password, name) else onLogin(email, password)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Fair Share", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "Wspólne grupy i rozliczenia ze znajomymi. Dane zapisują się na serwerze, więc każdy w grupie widzi to samo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !register,
                onClick = { register = false; onInputChanged() },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Logowanie") }
            SegmentedButton(
                selected = register,
                onClick = { register = true; onInputChanged() },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Nowe konto") }
        }

        if (register) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; onInputChanged() },
                label = { Text("Imię (widzą je znajomi)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; onInputChanged() },
            label = { Text("E-mail") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; onInputChanged() },
            label = { Text("Hasło") },
            supportingText = if (register) {
                { Text("Co najmniej 8 znaków") }
            } else {
                null
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )

        val message = error ?: if (!serverConfigured) "Ta wersja aplikacji nie ma adresu serwera (fairshare.apiUrl)." else null
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Button(onClick = { submit() }, enabled = canSubmit, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (register) "Załóż konto" else "Zaloguj się")
            }
        }
        if (busy) {
            Text(
                "Łączenie z serwerem i pobieranie danych…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Preview
@Composable
private fun LogowaniePreview() {
    FairShareTheme(darkTheme = true) {
        LogowanieContent(busy = false, error = null, serverConfigured = true)
    }
}
