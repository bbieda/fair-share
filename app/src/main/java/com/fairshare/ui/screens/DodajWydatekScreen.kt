package com.fairshare.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fairshare.data.model.ExpenseCategory
import com.fairshare.data.model.Money
import com.fairshare.ui.components.AppFilterChip
import com.fairshare.ui.components.BackHeader
import com.fairshare.ui.components.ListRow
import com.fairshare.ui.components.dayLabel
import com.fairshare.ui.components.icon
import com.fairshare.ui.components.labelOrDefault
import com.fairshare.ui.components.plural
import com.fairshare.ui.viewmodel.AddExpenseViewModel
import com.fairshare.ui.viewmodel.AppViewModelProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DodajWydatekScreen(
    onClose: () -> Unit = {},
    onSave: () -> Unit = {},
    onScanReceipt: () -> Unit = {},
    viewModel: AddExpenseViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val vm = viewModel
    var groupMenuOpen by remember { mutableStateOf(false) }
    var payerMenuOpen by remember { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
        ) {
            BackHeader("Nowy wydatek", onClose, navIcon = Icons.Outlined.Close, navLabel = "Zamknij")

            // ---- Group ----
            FieldLabel("Z kim", Modifier.padding(top = 12.dp))
            Box {
                AppFilterChip(
                    vm.selectedGroup?.name ?: "Wybierz grupę",
                    selected = false,
                    onClick = { groupMenuOpen = true },
                    dropdown = true,
                )
                DropdownMenu(expanded = groupMenuOpen, onDismissRequest = { groupMenuOpen = false }) {
                    if (vm.groups.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Najpierw utwórz grupę w zakładce Grupy") },
                            onClick = { groupMenuOpen = false },
                            enabled = false,
                        )
                    }
                    vm.groups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group.name) },
                            onClick = {
                                vm.selectGroup(group.id)
                                groupMenuOpen = false
                            },
                        )
                    }
                }
            }
            if (vm.groupsLoaded && vm.groups.isEmpty()) {
                Text(
                    "Nie masz jeszcze żadnej grupy. Utwórz ją w zakładce Grupy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // ---- Amount ----
            FieldLabel("Kwota", Modifier.padding(top = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val amountStyle = TextStyle(
                    fontSize = 48.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                BasicTextField(
                    value = vm.amount,
                    onValueChange = vm::updateAmount,
                    textStyle = amountStyle,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { field ->
                        if (vm.amount.isEmpty()) {
                            Text("0,00", style = amountStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                        field()
                    },
                )
                Text("PLN", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.primary)

            TextField(
                value = vm.description,
                onValueChange = { vm.description = it },
                label = { Text("Opis (np. Zakupy w Biedronce)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            )

            Spacer(Modifier.height(16.dp))

            // ---- Payer ----
            Box {
                ListRow(
                    Icons.Outlined.AccountBalanceWallet,
                    vm.payer?.let { if (it.isMe) "Ty" else it.name } ?: "Wybierz osobę",
                    overline = "Płacił",
                    onClick = { if (vm.members.isNotEmpty()) payerMenuOpen = true },
                )
                DropdownMenu(expanded = payerMenuOpen, onDismissRequest = { payerMenuOpen = false }) {
                    vm.members.forEach { member ->
                        DropdownMenuItem(
                            text = { Text(if (member.isMe) "Ty" else member.name) },
                            onClick = {
                                vm.payerId = member.id
                                payerMenuOpen = false
                            },
                        )
                    }
                }
            }

            // ---- Split ----
            val share = vm.sharePreview
            ListRow(
                Icons.Outlined.Percent,
                when {
                    vm.participantIds.isEmpty() -> "Zaznacz osoby poniżej"
                    share != null -> "Równo, po ${Money.format(share)}"
                    else -> "Równo, " + plural(vm.participantIds.size, "osoba", "osoby", "osób")
                },
                overline = "Podział",
                showChevron = false,
            )
            if (vm.members.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 40.dp, bottom = 8.dp),
                ) {
                    vm.members.forEach { member ->
                        AppFilterChip(
                            if (member.isMe) "Ty" else member.name,
                            selected = member.id in vm.participantIds,
                            onClick = { vm.toggleParticipant(member.id) },
                        )
                    }
                }
            }

            // ---- Category ----
            Box {
                val categoryLabel = if (vm.isCustomCategory) {
                    if (vm.customCategory.isNotBlank()) vm.customCategory else "Własna kategoria"
                } else {
                    vm.category?.labelOrDefault ?: "Wybierz kategorię"
                }
                val categoryIcon = if (vm.isCustomCategory) Icons.AutoMirrored.Outlined.Label else (vm.category?.icon ?: Icons.AutoMirrored.Outlined.Label)

                ListRow(
                    categoryIcon,
                    categoryLabel,
                    overline = "Kategoria",
                    onClick = { categoryMenuOpen = true },
                )
                DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                    ExpenseCategory.entries.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.labelOrDefault) },
                            leadingIcon = { Icon(category.icon, contentDescription = null) },
                            onClick = {
                                vm.category = category
                                vm.isCustomCategory = false
                                categoryMenuOpen = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Inna (wpisz własną)") },
                        leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        onClick = {
                            vm.category = null
                            vm.isCustomCategory = true
                            categoryMenuOpen = false
                        }
                    )
                }
            }

            if (vm.isCustomCategory) {
                OutlinedTextField(
                    value = vm.customCategory,
                    onValueChange = { vm.customCategory = it },
                    label = { Text("Wpisz własną kategorię") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp, bottom = 8.dp)
                )
            }

            // ---- Date ----
            ListRow(
                Icons.Outlined.CalendarToday,
                dayLabel(vm.date),
                overline = "Data",
                onClick = { datePickerOpen = true },
            )

            // ---- Receipt ----
            ListRow(
                Icons.Outlined.PhotoCamera,
                vm.scanSummary ?: "Zeskanuj paragon",
                overline = "Paragon",
                headlineColor = if (vm.scanSummary == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                onClick = onScanReceipt,
            )

            vm.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Button(
            onClick = { vm.save(onSaved = onSave) },
            enabled = vm.amount.isNotEmpty() && vm.selectedGroupId != null && !vm.saving,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Zapisz wydatek", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (datePickerOpen) {
        ExpenseDatePicker(
            initial = vm.date,
            onDismiss = { datePickerOpen = false },
            onPicked = { vm.date = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDatePicker(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}