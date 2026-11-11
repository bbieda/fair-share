package com.fairshare.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fairshare.data.AppSettings
import com.fairshare.data.model.ExpenseCategory
import com.fairshare.ui.components.BackHeader
import com.fairshare.ui.components.ConfirmDialog
import com.fairshare.ui.components.ListRow
import com.fairshare.ui.components.TextInputDialog
import com.fairshare.ui.rememberProfilePhoto
import com.fairshare.ui.theme.FairShareTheme
import com.fairshare.ui.viewmodel.AppViewModelProvider
import com.fairshare.ui.viewmodel.ProfileViewModel

@Composable
fun ProfilScreen(
    onBack: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val context = LocalContext.current
    val me by viewModel.me.collectAsStateWithLifecycle()
    val theme by viewModel.settings.themeMode.collectAsStateWithLifecycle()
    val notifications by viewModel.settings.notificationsEnabled.collectAsStateWithLifecycle()
    val customCategories by viewModel.settings.customCategories.collectAsStateWithLifecycle()
    val photo by rememberProfilePhoto(me?.photoUri)

    var editingName by rememberSaveable { mutableStateOf(false) }
    var editingPayment by rememberSaveable { mutableStateOf(false) }
    var editingCurrency by rememberSaveable { mutableStateOf(false) }
    var showPhotoOptions by rememberSaveable { mutableStateOf(false) }
    var showTheme by rememberSaveable { mutableStateOf(false) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    // Where the camera app writes the picture. Saved, because the app can be killed while the camera is open.
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.savePhoto(it, onError = { error -> message = error }) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved: Boolean ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (saved && uri != null) viewModel.savePhoto(uri, onError = { error -> message = error })
    }

    fun openCamera() {
        val uri = viewModel.newCameraUri()
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    // The manifest declares CAMERA, so the camera app only works after the user allowed it
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else message = "Bez zgody na użycie aparatu nie zrobimy zdjęcia. Możesz wybrać zdjęcie z galerii."
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.setNotifications(true)
        } else {
            message = "Powiadomienia są wyłączone w ustawieniach systemu. Włącz je dla FairShare, żeby ich używać."
        }
    }

    ProfilContent(
        name = me?.name ?: "",
        paymentDetails = me?.paymentDetails.orEmpty(),
        currency = me?.defaultCurrency ?: "PLN",
        photo = photo,
        theme = theme,
        notificationsOn = notifications,
        customCategoryCount = customCategories.size,
        onBack = onBack,
        onEditName = { editingName = true },
        onEditPhoto = { showPhotoOptions = true },
        onEditPayment = { editingPayment = true },
        onEditCurrency = { editingCurrency = true },
        onToggleNotifications = { enabled ->
            val needsPermission = enabled && Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.setNotifications(enabled)
            }
        },
        onEditCategories = { showCategories = true },
        onEditTheme = { showTheme = true },
        onExport = { viewModel.exportCsv(onError = { message = it }) },
        onDeleteAll = { confirmDelete = true },
    )

    if (showPhotoOptions) {
        AlertDialog(
            onDismissRequest = { showPhotoOptions = false },
            title = { Text("Zdjęcie profilowe") },
            text = {
                Column {
                    PhotoOption(Icons.Outlined.PhotoLibrary, "Wybierz z galerii") {
                        showPhotoOptions = false
                        galleryLauncher.launch("image/*")
                    }
                    PhotoOption(Icons.Outlined.CameraAlt, "Zrób zdjęcie aparatem") {
                        showPhotoOptions = false
                        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        if (allowed) openCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    if (photo != null) {
                        PhotoOption(Icons.Outlined.DeleteOutline, "Usuń zdjęcie") {
                            showPhotoOptions = false
                            viewModel.removePhoto()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPhotoOptions = false }) { Text("Anuluj") } },
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
            allowEmpty = true,
            onDismiss = { editingPayment = false },
            onConfirm = viewModel::updatePaymentDetails,
        )
    }

    if (editingCurrency) {
        CurrencySelectionDialog(
            currentCurrency = me?.defaultCurrency ?: "PLN",
            onDismiss = { editingCurrency = false },
            onSelect = { currency ->
                viewModel.updateCurrency(currency)
                editingCurrency = false
            },
        )
    }

    if (showTheme) {
        ThemeDialog(
            current = theme,
            onDismiss = { showTheme = false },
            onSelect = {
                viewModel.setTheme(it)
                showTheme = false
            },
        )
    }

    if (showCategories) {
        CategoriesDialog(
            custom = customCategories,
            onAdd = viewModel::addCustomCategory,
            onRemove = viewModel::removeCustomCategory,
            onDismiss = { showCategories = false },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Usunąć wszystkie dane?",
            message = "Grupy, osoby, wydatki i aktywność zostaną trwale usunięte z tego telefonu.",
            confirmLabel = "Usuń wszystko",
            onDismiss = { confirmDelete = false },
            onConfirm = viewModel::deleteAllData,
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun ProfilContent(
    name: String,
    paymentDetails: String,
    currency: String,
    photo: android.graphics.Bitmap?,
    theme: String,
    notificationsOn: Boolean,
    customCategoryCount: Int,
    onBack: () -> Unit = {},
    onEditName: () -> Unit = {},
    onEditPhoto: () -> Unit = {},
    onEditPayment: () -> Unit = {},
    onEditCurrency: () -> Unit = {},
    onToggleNotifications: (Boolean) -> Unit = {},
    onEditCategories: () -> Unit = {},
    onEditTheme: () -> Unit = {},
    onExport: () -> Unit = {},
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
                if (photo != null) {
                    Image(
                        bitmap = photo.asImageBitmap(),
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
        SwitchRow(
            Icons.Outlined.Notifications,
            "Powiadomienia",
            "Nowe wydatki, spłaty i grupy",
            checked = notificationsOn,
            onCheckedChange = onToggleNotifications,
        )
        ListRow(
            Icons.AutoMirrored.Outlined.Label,
            "Kategorie",
            supporting = if (customCategoryCount == 0) "Wbudowane i własne" else "Własne kategorie: $customCategoryCount",
            onClick = onEditCategories,
        )
        ListRow(
            Icons.Outlined.DarkMode,
            "Motyw",
            supporting = when (theme) {
                AppSettings.THEME_LIGHT -> "Jasny"
                AppSettings.THEME_SYSTEM -> "Jak w systemie"
                else -> "Ciemny"
            },
            onClick = onEditTheme,
        )
        ListRow(
            Icons.Outlined.FileDownload,
            "Eksport do CSV",
            supporting = "Wszystkie grupy i wydatki",
            onClick = onExport,
        )

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
private fun SwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    headline: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(headline, style = MaterialTheme.typography.bodyLarge)
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PhotoOption(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ThemeDialog(current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val options = listOf(
        AppSettings.THEME_DARK to "Ciemny",
        AppSettings.THEME_LIGHT to "Jasny",
        AppSettings.THEME_SYSTEM to "Jak w systemie",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Motyw") },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(key) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = key == current, onClick = { onSelect(key) })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

/** Built-in categories are fixed; own categories can be added and removed here. */
@Composable
private fun CategoriesDialog(
    custom: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kategorie") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Wbudowane: " + ExpenseCategory.entries.joinToString(", ") { it.label },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Własne",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (custom.isEmpty()) {
                    Text(
                        "Nie masz jeszcze własnych kategorii.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                custom.forEach { name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(name) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Usuń kategorię $name")
                        }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nowa kategoria") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                TextButton(
                    onClick = {
                        onAdd(newName)
                        newName = ""
                    },
                    enabled = newName.isNotBlank(),
                ) { Text("Dodaj") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gotowe") } },
    )
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
                photo = null,
                theme = AppSettings.THEME_DARK,
                notificationsOn = false,
                customCategoryCount = 0,
            )
        }
    }
}
