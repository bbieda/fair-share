package com.fairshare.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
    var editingPayment by rememberSaveable { mutableStateOf(false) }
    var editingCurrency by rememberSaveable { mutableStateOf(false) }
    var showPhotoOptions by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            avatarUri = it
            avatarBitmap = null
            // Pamiętaj aby dodać poniższą metodę w ProfileViewModel
            // viewModel.updatePhotoUri(it.toString())
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            avatarBitmap = it
            avatarUri = null
            // Pamiętaj aby dodać poniższą metodę w ProfileViewModel
            // viewModel.updatePhotoBitmap(it)
        }
    }

    ProfilContent(
        name = me?.name ?: "",
        paymentDetails = me?.paymentDetails ?: "Numer konta lub telefon BLIK",
        currency = me?.defaultCurrency ?: "PLN",
        avatarBitmap = avatarBitmap,
        avatarUri = avatarUri,
        onBack = onBack,
        onEditName = { editingName = true },
        onEditPhoto = { showPhotoOptions = true },
        onEditPayment = { editingPayment = true },
        onEditCurrency = { editingCurrency = true },
        onDeleteAll = { confirmDelete = true },
    )

    if (showPhotoOptions) {
        AlertDialog(
            onDismissRequest = { showPhotoOptions = false },
            title = { Text("Zdjęcie profilowe") },
            text = { Text("Wybierz źródło zdjęcia profilowego:") },
            confirmButton = {
                TextButton(onClick = {
                    showPhotoOptions = false
                    galleryLauncher.launch("image/*")
                }) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Galeria")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPhotoOptions = false
                    cameraLauncher.launch(null)
                }) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Aparat")
                }
            },
        )
    }

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

    if (editingPayment) {
        TextInputDialog(
            title = "Dane do spłat",
            label = "Nr konta / Telefon BLIK",
            confirmLabel = "Zapisz",
            initialValue = me?.paymentDetails.orEmpty(),
            onDismiss = { editingPayment = false },
            onConfirm = {
                // Pamiętaj aby dodać poniższą metodę w ProfileViewModel
                // viewModel.updatePaymentDetails(it)
                editingPayment = false
            },
        )
    }

    if (editingCurrency) {
        CurrencySelectionDialog(
            currentCurrency = me?.defaultCurrency ?: "PLN",
            onDismiss = { editingCurrency = false },
            onSelect = { currency ->
                // Pamiętaj aby dodać poniższą metodę w ProfileViewModel
                // viewModel.updateCurrency(currency)
                editingCurrency = false
            },
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
    paymentDetails: String,
    currency: String,
    avatarBitmap: Bitmap?,
    avatarUri: Uri?,
    onBack: () -> Unit = {},
    onEditName: () -> Unit = {},
    onEditPhoto: () -> Unit = {},
    onEditPayment: () -> Unit = {},
    onEditCurrency: () -> Unit = {},
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
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { onEditPhoto() },
                contentAlignment = Alignment.Center,
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap.asImageBitmap(),
                        contentDescription = "Profil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Text(
                name.ifBlank { "Twoje imię" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row {
                TextButton(onClick = onEditName) { Text("Edytuj imię") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onEditPhoto) { Text("Zmień zdjęcie") }
            }
        }

        SettingsSection("Rozliczenia")
        ListRow(
            Icons.Outlined.Payments,
            "Waluta domyślna",
            supporting = "$currency, waluta rozliczeń",
            onClick = onEditCurrency,
        )
        ListRow(
            Icons.Outlined.AccountBalance,
            "Dane do spłat",
            supporting = paymentDetails.ifBlank { "Dodaj numer konta lub telefon BLIK" },
            onClick = onEditPayment,
        )

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
private fun CurrencySelectionDialog(
    currentCurrency: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val currencies = listOf(
        "PLN" to "PLN, złoty polski",
        "EUR" to "EUR, euro",
        "USD" to "USD, dolar amerykański",
        "GBP" to "GBP, funt brytyjski",
        "CZK" to "CZK, korona czeska",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wybierz domyślną walutę") },
        text = {
            Column {
                currencies.forEach { (code, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(code) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (code == currentCurrency) FontWeight.Bold else FontWeight.Normal,
                            color = if (code == currentCurrency) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        },
    )
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
        Surface(color = MaterialTheme.colorScheme.background) {
            ProfilContent(
                name = "Oskar",
                paymentDetails = "BLIK: 500 600 700",
                currency = "PLN",
                avatarBitmap = null,
                avatarUri = null,
            )
        }
    }
}