package com.fairshare.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fairshare.data.db.SettlementLine
import com.fairshare.data.model.Money
import com.fairshare.ui.components.BackHeader
import com.fairshare.ui.components.EmptyHint
import com.fairshare.ui.components.LetterAvatar
import com.fairshare.ui.components.SectionCard
import com.fairshare.ui.components.avatarColorsFor
import com.fairshare.ui.components.initialOf
import com.fairshare.ui.theme.Negative
import com.fairshare.ui.theme.Positive
import com.fairshare.ui.viewmodel.AppViewModelProvider
import com.fairshare.ui.viewmodel.RozliczViewModel
import kotlin.math.abs

@Composable
fun RozliczScreen(
    onBack: () -> Unit = {},
    viewModel: RozliczViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val lines: List<SettlementLine> by viewModel.lines.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()

    // balance < 0: I owe that person, balance > 0: that person owes me
    val toPay = lines.filter { it.balance < 0 }
    val toReceive = lines.filter { it.balance > 0 }
    val totalToPay = toPay.sumOf { abs(it.balance) }
    val totalToReceive = toReceive.sumOf { it.balance }

    // The line that is being settled in the dialog (remembered by person + group, not by object)
    var settling by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var editingPaymentFor by remember { mutableStateOf<Long?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        BackHeader("Rozlicz się", onBack)

        SectionCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryAmount("Oddajesz", Money.format(totalToPay), Negative, Modifier.weight(1f))
                SummaryAmount("Dostaniesz", Money.format(totalToReceive), Positive, Modifier.weight(1f))
            }
        }

        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SectionTitle("Ty oddajesz", Modifier.padding(top = 16.dp))
        if (toPay.isEmpty()) {
            EmptyHint("Nie masz nic do oddania.")
        } else {
            toPay.forEach { line ->
                SettlementRow(
                    line = line,
                    amountColor = Negative,
                    // when I have to pay, show where the money should go
                    paymentHint = line.paymentDetails?.takeIf { it.isNotBlank() }
                        ?.let { "Dane do spłaty: $it" } ?: "Dodaj dane do spłaty tej osoby",
                    onPaymentClick = { editingPaymentFor = line.userId },
                ) {
                    FilledTonalButton(
                        onClick = { settling = line.userId to line.groupId },
                        modifier = Modifier.height(40.dp),
                    ) { Text("Zapisz spłatę") }
                }
            }
        }

        SectionTitle("Oddają tobie", Modifier.padding(top = 8.dp))
        if (toReceive.isEmpty()) {
            EmptyHint("Nikt nie jest ci winien.")
        } else {
            toReceive.forEach { line ->
                SettlementRow(
                    line = line,
                    amountColor = Positive,
                    paymentHint = null,
                    onPaymentClick = {},
                ) {
                    OutlinedButton(
                        onClick = { settling = line.userId to line.groupId },
                        modifier = Modifier.height(40.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    ) { Text("Rozlicz") }
                }
            }
        }

        // Payment details of the phone owner: the other side needs them to pay me back
        me?.paymentDetails?.takeIf { it.isNotBlank() }?.let { mine ->
            val clipboard = LocalClipboardManager.current
            SectionTitle("Twoje dane do spłat", Modifier.padding(top = 8.dp))
            Text(
                mine,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { clipboard.setText(AnnotatedString(mine)) }
                    .padding(vertical = 8.dp),
            )
            Text(
                "Dotknij, żeby skopiować.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Salda są liczone osobno dla każdej grupy. Możesz rozliczyć całą kwotę albo tylko jej część.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // ---- Settle dialog ----
    val line = settling?.let { (userId, groupId) -> lines.firstOrNull { it.userId == userId && it.groupId == groupId } }
    if (settling != null && line == null) settling = null // the line is gone (already settled)
    if (line != null) {
        SettleDialog(
            line = line,
            onDismiss = { settling = null },
            onConfirm = { amount ->
                error = null
                viewModel.settle(line, amount, onError = { error = it })
                settling = null
            },
        )
    }

    // ---- Payment details of another person ----
    editingPaymentFor?.let { userId ->
        val person = lines.firstOrNull { it.userId == userId }
        if (person != null) {
            com.fairshare.ui.components.TextInputDialog(
                title = "Dane do spłaty: ${person.name}",
                label = "Nr konta / telefon BLIK",
                confirmLabel = "Zapisz",
                initialValue = person.paymentDetails.orEmpty(),
                allowEmpty = true,
                onDismiss = { editingPaymentFor = null },
                onConfirm = { viewModel.savePaymentDetails(userId, it) },
            )
        } else {
            editingPaymentFor = null
        }
    }
}

/** Asks for the amount (the full debt is prefilled) so a part of the debt can be settled too. */
@Composable
private fun SettleDialog(
    line: SettlementLine,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val full = abs(line.balance)
    var input by rememberSaveable(line.userId, line.groupId) { mutableStateOf(Money.formatInput(full)) }
    val amount = Money.parse(input)
    val valid = amount != null && amount <= full
    val iOwe = line.balance < 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (iOwe) "Spłata dla ${line.name}" else "Spłata od ${line.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Grupa: ${line.groupName}. Saldo: ${Money.format(full)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { new -> input = new.filter { it.isDigit() || it == ',' || it == '.' } },
                    label = { Text("Kwota") },
                    suffix = { Text(Money.currencyCode) },
                    singleLine = true,
                    isError = input.isNotEmpty() && !valid,
                    supportingText = {
                        if (input.isNotEmpty() && !valid) Text("Wpisz kwotę od 0,01 do ${Money.format(full)}")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { amount?.let(onConfirm) }, enabled = valid) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
private fun SummaryAmount(label: String, amount: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .height(36.dp)
            .padding(top = 8.dp),
    )
}

@Composable
private fun SettlementRow(
    line: SettlementLine,
    amountColor: Color,
    paymentHint: String?,
    onPaymentClick: () -> Unit,
    action: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LetterAvatar(text = initialOf(line.name), colors = avatarColorsFor(line.userId))
        Column(Modifier.weight(1f)) {
            Text(line.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = amountColor)) {
                        append(Money.format(abs(line.balance)))
                    }
                    append(" · ")
                    append(line.groupName)
                },
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (paymentHint != null) {
                Text(
                    paymentHint,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onPaymentClick),
                )
            }
        }
        action()
    }
}
