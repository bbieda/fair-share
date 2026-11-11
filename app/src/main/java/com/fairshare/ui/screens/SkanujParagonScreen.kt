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
import androidx.compose.material.icons.outlined.Receipt
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
    /** Several receipts in one photo, each as its own expense: the first goes to the form, the rest wait. */
    onUseSeparately: (List<ScannedReceipt>) -> Unit = {},
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
    val several = (state as? ScanViewModel.State.Done)?.outcome?.parts?.takeIf { it.size > 1 }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = when {
                        several != null -> 232.dp
                        state is ScanViewModel.State.Done -> 168.dp
                        else -> 24.dp
                    },
                ),
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
                    state.savedAs?.let { SavedNote(it) }
                    Spacer(Modifier.height(16.dp))
                    PhotoButtons(openCamera, openGallery)
                }
                is ScanViewModel.State.Done ->
                    if (several != null) SeveralReceipts(state.outcome, state.savedAs, onRetry = vm::retry)
                    else ScanResult(state.outcome, state.savedAs, vm.aiAvailable, onRetry = vm::retry)
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
                    if (several != null) {
                        val receipts = several.map { it.receipt }
                        val combined = ScannedReceipt.combine(receipts)
                        Button(
                            onClick = { onUse(combined) },
                            enabled = combined.totalGrosze != null,
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) {
                            Text(
                                "Jeden wydatek: ${combined.totalGrosze?.pln() ?: "?"}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Button(
                            onClick = { onUseSeparately(receipts) },
                            enabled = receipts.all { it.totalGrosze != null },
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) {
                            Text(
                                "Osobno: ${receipts.size} ${plural(receipts.size, "wydatek", "wydatki", "wydatków")}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
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
            "Kilka paragonów naraz: połóż je obok siebie albo jeden pod drugim, z odstępem między nimi.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun ScanResult(outcome: ReceiptScanner.Outcome, savedAs: String?, aiAvailable: Boolean, onRetry: () -> Unit) {
    val receipt = outcome.receipt
    var showOcr by remember { mutableStateOf(false) }

    if (outcome.multipleSuspected) {
        Banner(
            Icons.Outlined.ErrorOutline,
            "Na zdjęciu są chyba 2 paragony, ale nie dało się ich rozdzielić. Połóż je dalej od siebie albo zrób zdjęcie każdego osobno.",
            Negative,
        )
        Spacer(Modifier.height(8.dp))
    }

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
            savedAs?.let { InfoLine("Zapis", "skany/$it (wersja debug)") }

            val byRules = outcome.byRules
            when {
                outcome.aiError != null -> AiFailed(outcome.aiError, onRetry)
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

/** Gemini did not answer: what happened and a button to scan the same photo again. */
@Composable
private fun AiFailed(error: String?, onRetry: () -> Unit) {
    Column {
        Text(
            "Gemini nie pomogło: $error. Pokazuję odczyt reguł.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Spróbuj ponownie")
        }
    }
}

/** A photo with several receipts: each one with its own checksum, the buttons below decide how to add them. */
@Composable
private fun SeveralReceipts(outcome: ReceiptScanner.Outcome, savedAs: String?, onRetry: () -> Unit) {
    var showOcr by remember { mutableStateOf(false) }
    Banner(
        Icons.Outlined.Receipt,
        "Na zdjęciu ${plural(outcome.parts.size, "jest", "są", "jest")} ${outcome.parts.size} " +
            "${plural(outcome.parts.size, "paragon", "paragony", "paragonów")}. Dodaj je jako jeden wydatek albo każdy osobno.",
        MaterialTheme.colorScheme.primary,
    )
    outcome.parts.forEachIndexed { index, part ->
        val receipt = part.receipt
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
                Text(
                    "Paragon ${index + 1}: ${receipt.store ?: "nie rozpoznano sklepu"}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    receipt.totalGrosze?.pln() ?: "Nie znaleziono sumy",
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Medium,
                )
                SumBadge(receipt)
                receipt.items.forEach { item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(item.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(item.priceGrosze.pln(), style = MaterialTheme.typography.bodySmall)
                    }
                }
                InfoLine(
                    "Odczyt",
                    receipt.source.label + if (receipt.source == ScanSource.GEMINI && part.aiModel != null) " · ${part.aiModel}" else "",
                )
                if (part.aiError != null) AiFailed(part.aiError, onRetry)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    InfoLine("Czas", "łącznie ${duration(outcome.totalMs)} (ML Kit ${duration(outcome.ocrMs)})")
    savedAs?.let { InfoLine("Zapis", "skany/$it (wersja debug)") }
    TextButton(onClick = { showOcr = !showOcr }, modifier = Modifier.padding(top = 8.dp)) {
        Icon(if (showOcr) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(if (showOcr) "Ukryj tekst z ML Kit" else "Pokaż tekst z ML Kit (debug)")
    }
    if (showOcr) {
        outcome.parts.forEachIndexed { index, part ->
            Text("Paragon ${index + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    part.ocrText,
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

/** Debug builds: where the photo and its reading were saved (scripts\zapisz-log.bat copies them to the PC). */
@Composable
private fun SavedNote(id: String) {
    Text(
        "Zdjęcie i odczyt zapisane w skany/$id (wersja debug).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
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

/** Polish plural: 1 paragon, 2–4 paragony (but 12–14 paragonów), 5+ paragonów. */
private fun plural(count: Int, one: String, few: String, many: String): String = when {
    count == 1 -> one
    count % 10 in 2..4 && count % 100 !in 12..14 -> few
    else -> many
}

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
