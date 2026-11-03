package com.fairshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fairshare.ui.components.BackHeader
import com.fairshare.ui.components.ConfirmDialog
import com.fairshare.ui.components.ListRow
import com.fairshare.ui.components.TextInputDialog
import com.fairshare.ui.theme.FairShareTheme
import com.fairshare.ui.viewmodel.AppViewModelProvider
import com.fairshare.ui.viewmodel.ProfileViewModel

@Composable
fun ProfilScreen(
    onBack: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val me by viewModel.me.collectAsStateWithLifecycle()
    var editingName by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    ProfilContent(
        name = me?.name ?: "",
        onBack = onBack,
        onEditName = { editingName = true },
        onDeleteAll = { confirmDelete = true },
    )

    if (editingName) {
        TextInputDialog(
            title = "Twoje imię",
            label = "Imię",
            confirmLabel = "Zapisz",
            initialValue = me?.name.orEmpty(),
            onDismiss = { editingName = false },
            onConfirm = viewModel::rename,
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Usunąć wszystkie dane?",
            message = "Grupy, osoby i wydatki zostaną trwale usunięte z tego telefonu.",
            confirmLabel = "Usuń wszystko",
            onDismiss = { confirmDelete = false },
            onConfirm = viewModel::deleteAllData,
        )
    }
}

@Composable
private fun ProfilContent(
    name: String,
    onBack: () -> Unit = {},
    onEditName: () -> Unit = {},
    onDeleteAll: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        BackHeader("Profil i ustawienia", onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                name.ifBlank { "Twoje imię" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp),
            )
            TextButton(onClick = onEditName) { Text("Edytuj imię") }
        }

        SettingsSection("Rozliczenia")
        ListRow(Icons.Outlined.Payments, "Waluta domyślna", supporting = "PLN, złoty polski")
        ListRow(Icons.Outlined.AccountBalance, "Dane do spłat", supporting = "Numer konta lub telefon BLIK")

        SettingsSection("Aplikacja")
        ListRow(Icons.Outlined.Notifications, "Powiadomienia", supporting = "Wydatki, spłaty, podsumowanie miesiąca")
        ListRow(Icons.AutoMirrored.Outlined.Label, "Kategorie", supporting = "Nazwy, ikony i podpowiedzi")
        ListRow(Icons.Outlined.DarkMode, "Motyw", supporting = "Ciemny")
        ListRow(Icons.Outlined.FileDownload, "Eksport do CSV", supporting = "Wszystkie grupy lub wybrany okres")

        SettingsSection("Dane")
        ListRow(
            Icons.Outlined.DeleteOutline,
            "Usuń wszystkie dane",
            supporting = "Z tego telefonu",
            iconTint = MaterialTheme.colorScheme.error,
            headlineColor = MaterialTheme.colorScheme.error,
            showChevron = false,
            onClick = onDeleteAll,
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .height(40.dp)
            .padding(top = 10.dp),
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ProfilPreview() {
    FairShareTheme {
        Surface(color = MaterialTheme.colorScheme.background) { ProfilContent(name = "Oskar") }
    }
}
