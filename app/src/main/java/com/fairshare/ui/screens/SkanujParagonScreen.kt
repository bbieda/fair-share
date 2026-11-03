package com.fairshare.ui.screens

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fairshare.data.model.Money
import com.fairshare.scanner.ReceiptScanner
import com.fairshare.scanner.ScanSource
import com.fairshare.scanner.ScannedReceipt
import com.fairshare.ui.components.BackHeader
import com.fairshare.ui.theme.Negative
import com.fairshare.ui.theme.Positive
import com.fairshare.ui.viewmodel.AppViewModelProvider
import com.fairshare.ui.viewmodel.ScanViewModel
import java.util.Locale
import kotlin.math.abs

/**
 * Receipt scanner (test version): take a photo or pick one from the gallery, see what was read, how long it took and
 * which step read it, then use the total in the expense form.
 */
@Composable
fun SkanujParagonScreen(
    onBack: () -> Unit = {},
    onUse: (ScannedReceipt) -> Unit = {},
    viewModel: ScanViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val vm = viewModel
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        vm.onPhotoTaken(saved)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.scan(uri)
    }
    val openCamera: () -> Unit = {
        try {
            takePicture.launch(vm.newPhotoUri())
        } catch (e: ActivityNotFoundException) {
            vm.showError("Na telefonie nie ma aplikacji aparatu. Wybierz zdjęcie z galerii.")
        }
    }
    val openGallery: () -> Unit = {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val state = vm.state
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (state is ScanViewModel.State.Done) 168.dp else 24.dp),
        ) {
            BackHeader("Skanuj paragon", onBack)
            when (state) {
                ScanViewModel.State.Idle -> {
                    Intro(vm.aiAvailable, vm.aiModel)
                    PhotoButtons(openCamera, openGallery)
                }
                is ScanViewModel.State.Working -> Working(state.stage)
                is ScanViewModel.State.Failed -> {
                    Banner(Icons.Outlined.ErrorOutline, state.message, MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    PhotoButtons(openCamera, openGallery)
                }
                is ScanViewModel.State.Done -> ScanResult(state.outcome, vm.aiAvailable)
            }
        }

        if (state is ScanViewModel.State.Done) {
            val receipt = state.outcome.receipt
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    // the app's Scaffold already pads for the system navigation bar
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                ) {
                    Button(
                        onClick = { onUse(receipt) },
                        enabled = receipt.totalGrosze != null,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Użyj w wydatku", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = vm::reset,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Skanuj inny paragon")
                    }
                }
            }
        }
    }
}

@Composable
private fun Intro(aiAvailable: Boolean, aiModel: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)) {
        Text(
            "Zrób zdjęcie całego paragonu, od nazwy sklepu do sumy. Najlepiej płasko, przy dobrym świetle.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Tekst czyta ML Kit na telefonie, a reguły wyciągają z niego pozycje i sumę. " +
                "Jeśli pozycje nie sumują się do kwoty z paragonu, zdjęcie czyta jeszcze Gemini.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (aiAvailable) {
            Banner(Icons.Outlined.AutoAwesome, "Gemini włączone (${aiModel ?: "?"}).", MaterialTheme.colorScheme.primary)
        } else {
            Banner(
                Icons.Outlined.AutoAwesome,
                "Gemini wyłączone: aplikacja została zbudowana bez klucza API. Działa tylko ML Kit + reguły " +
                    "(klucz: gemini.apiKey w local.properties albo zmienna GEMINI_API_KEY, opis w docs/skaner.md).",
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhotoButtons(openCamera: () -> Unit, openGallery: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = openCamera,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Zrób zdjęcie", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = openGallery,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Wybierz z galerii", fontSize = 16.sp)
        }
    }
}

@Composable
private fun Working(stage: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp),
    ) {
        CircularProgressIndicator()
        Text(stage, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ScanResult(outcome: ReceiptScanner.Outcome, aiAvailable: Boolean) {
    val receipt = outcome.receipt
    var showOcr by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(
            receipt.store ?: "Nie rozpoznano sklepu",
            style = MaterialTheme.typography.titleLarge,
            color = if (receipt.store == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
        )
        Text(
            receipt.totalGrosze?.let { Money.format(it) } ?: "Nie znaleziono sumy",
            fontSize = if (receipt.totalGrosze == null) 24.sp else 40.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.Medium,
        )
    }
    Spacer(Modifier.height(8.dp))
    SumBadge(receipt)

    // ---- How it was read ----
    Spacer(Modifier.height(16.dp))
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
            InfoLine(
                "Odczyt",
                receipt.source.label + if (receipt.source == ScanSource.GEMINI && outcome.aiModel != null) " · ${outcome.aiModel}" else "",
            )
            InfoLine("Czas", "łącznie ${duration(outcome.totalMs)}")
            InfoLine(
                "",
                buildString {
                    append("ML Kit ${duration(outcome.ocrMs)}")
                    if (outcome.ocrTotalMs > outcome.ocrMs + 50) append(" (z próbami obrotu ${duration(outcome.ocrTotalMs)})")
                    append(" · reguły ${duration(outcome.rulesMs)}")
                    outcome.aiMs?.let { append(" · Gemini ${duration(it)}") }
                },
            )
            if (outcome.rotation != 0) InfoLine("Obrót", "zdjęcie odczytane po obróceniu o ${outcome.rotation}°")

            val byRules = outcome.byRules
            when {
                outcome.aiError != null -> Text(
                    "Gemini nie pomogło: ${outcome.aiError}. Pokazuję odczyt reguł.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                outcome.aiModel != null -> Text(
                    "Reguły: ${byRules.items.size} poz. na ${byRules.itemsSumGrosze.pln()}, suma " +
                        "${byRules.totalGrosze?.pln() ?: "nieznaleziona"}, więc zdjęcie poszło do Gemini" +
                        if (receipt.source == ScanSource.RULES) ", ale jego odczyt był gorszy." else ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                !byRules.sumMatches && !aiAvailable -> Text(
                    "Suma się nie zgadza, a Gemini jest wyłączone, więc to odczyt samych reguł.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // ---- Items ----
    Text(
        "Pozycje (${receipt.items.size})",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
    if (receipt.items.isEmpty()) {
        Text(
            "Nie znaleziono pozycji.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    receipt.items.forEach { item ->
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                Money.format(item.priceGrosze),
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.priceGrosze < 0) Negative else MaterialTheme.colorScheme.onBackground,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    if (receipt.items.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Suma pozycji", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(Money.format(receipt.itemsSumGrosze), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }

    if (receipt.notes.isNotEmpty()) {
        Text(
            "Uwagi",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )
        receipt.notes.forEach { note ->
            Text("• $note", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // ---- Debug: what ML Kit read ----
    TextButton(onClick = { showOcr = !showOcr }, modifier = Modifier.padding(top = 16.dp)) {
        Icon(if (showOcr) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(if (showOcr) "Ukryj tekst z ML Kit" else "Pokaż tekst z ML Kit (debug)")
    }
    if (showOcr) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                outcome.ocrText,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                softWrap = false,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }
    }
}

/** Green when the items add up to the total (the receipt's own checksum), red with the difference otherwise. */
@Composable
private fun SumBadge(receipt: ScannedReceipt) {
    val total = receipt.totalGrosze
    val (icon, text, color) = when {
        receipt.sumMatches -> Triple(Icons.Outlined.CheckCircle, "Pozycje sumują się do kwoty z paragonu", Positive)
        total == null -> Triple(Icons.Outlined.ErrorOutline, "Nie znaleziono sumy na paragonie: sprawdź kwotę", Negative)
        receipt.items.isEmpty() -> Triple(Icons.Outlined.ErrorOutline, "Nie znaleziono pozycji: sprawdź kwotę", Negative)
        else -> Triple(
            Icons.Outlined.ErrorOutline,
            "Pozycje dają ${receipt.itemsSumGrosze.pln()}, paragon ${total.pln()} " +
                "(różnica ${abs(receipt.itemsSumGrosze - total).pln()}): sprawdź kwotę",
            Negative,
        )
    }
    Banner(icon, text, color)
}

@Composable
private fun Banner(icon: ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

private fun Long.pln(): String = Money.format(this)

private fun duration(ms: Long): String =
    if (ms < 1000) "$ms ms" else String.format(Locale.forLanguageTag("pl-PL"), "%.1f s", ms / 1000.0)

/** "Biedronka · 12 pozycji" for the "Paragon" row of the expense form. */
fun ScannedReceipt.summaryLine(): String {
    val count = items.size
    val positions = when {
        count == 1 -> "1 pozycja"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "$count pozycje"
        else -> "$count pozycji"
    }
    return listOfNotNull(store, positions).joinToString(" · ")
}
