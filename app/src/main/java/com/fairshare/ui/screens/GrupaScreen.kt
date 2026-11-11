package com.fairshare.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fairshare.data.db.ExpenseListRow
import com.fairshare.data.db.MemberBalance
import com.fairshare.data.db.UserEntity
import com.fairshare.data.model.Money
import com.fairshare.ui.components.BackHeader
import com.fairshare.ui.components.BalanceAmount
import com.fairshare.ui.components.ConfirmDialog
import com.fairshare.ui.components.EmptyHint
import com.fairshare.ui.components.IconTile
import com.fairshare.ui.components.LetterAvatar
import com.fairshare.ui.components.TextInputDialog
import com.fairshare.data.model.InviteCodes
import com.fairshare.ui.components.avatarColorsFor
import com.fairshare.ui.components.categoryAvatarColors
import com.fairshare.ui.components.categoryIcon
import com.fairshare.ui.components.categoryLabel
import com.fairshare.ui.components.dayLabel
import androidx.compose.material.icons.outlined.Payments
import com.fairshare.ui.components.initialOf
import com.fairshare.ui.components.monthShortName
import com.fairshare.ui.components.monthTitle
import com.fairshare.ui.components.plural
import com.fairshare.ui.theme.FairShareTheme
import com.fairshare.ui.theme.Negative
import com.fairshare.ui.theme.Positive
import com.fairshare.ui.viewmodel.AppViewModelProvider
import com.fairshare.ui.viewmodel.GroupUiState
import com.fairshare.ui.viewmodel.GroupViewModel
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun GrupaScreen(
    onBack: () -> Unit = {},
    onSettle: () -> Unit = {},
    onOpenStats: (groupId: Long) -> Unit = {},
    viewModel: GroupViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddMember by rememberSaveable { mutableStateOf(false) }
    var expenseToDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    var expenseDetails by rememberSaveable { mutableStateOf<Long?>(null) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showCode by rememberSaveable { mutableStateOf(false) }

    GrupaContent(
        state = state,
        groupId = viewModel.groupId,
        onBack = onBack,
        onSettle = onSettle,
        onOpenStats = { onOpenStats(viewModel.groupId) },
        onAddMember = { showAddMember = true },
        onExpenseClick = { expenseDetails = it },
        onRename = { showRename = true },
        onShowCode = { showCode = true },
        onToggleArchive = { viewModel.setArchived(!state.archived) },
    )

    if (showRename) {
        TextInputDialog(
            title = "Nazwa grupy",
            label = "Nazwa",
            confirmLabel = "Zapisz",
            initialValue = state.name,
            onDismiss = { showRename = false },
            onConfirm = viewModel::rename,
        )
    }

    if (showCode) {
        GroupCodeDialog(code = InviteCodes.format(viewModel.groupId), onDismiss = { showCode = false })
    }

    expenseDetails?.let { expenseId ->
        val expense = state.expenses.firstOrNull { it.id == expenseId }
        if (expense == null) {
            expenseDetails = null
        } else {
            ExpenseDetailsDialog(
                expense = expense,
                onDismiss = { expenseDetails = null },
                onDelete = {
                    expenseDetails = null
                    expenseToDelete = expenseId
                },
            )
        }
    }

    if (showAddMember) {
        AddMemberDialog(
            contacts = state.contacts,
            onDismiss = { showAddMember = false },
            onAddName = viewModel::addMemberByName,
            onAddContact = viewModel::addContact,
        )
    }

    expenseToDelete?.let { expenseId ->
        val title = state.expenses.firstOrNull { it.id == expenseId }?.description ?: "wydatek"
        ConfirmDialog(
            title = "Usunąć wpis?",
            message = "„$title” zniknie z grupy, a salda i statystyki zostaną przeliczone.",
            confirmLabel = "Usuń",
            onDismiss = { expenseToDelete = null },
            onConfirm = { viewModel.deleteExpense(expenseId) },
        )
    }
}

@Composable
private fun GrupaContent(
    state: GroupUiState,
    groupId: Long = 0L,
    onBack: () -> Unit = {},
    onSettle: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onAddMember: () -> Unit = {},
    onExpenseClick: (Long) -> Unit = {},
    onRename: () -> Unit = {},
    onShowCode: () -> Unit = {},
    onToggleArchive: () -> Unit = {},
) {
    var showMembers by rememberSaveable { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
    ) {
        BackHeader(state.name, onBack) {
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Opcje grupy")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Zmień nazwę") },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Kod grupy") },
                        onClick = {
                            menuOpen = false
                            onShowCode()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (state.archived) "Przywróć z archiwum" else "Archiwizuj") },
                        onClick = {
                            menuOpen = false
                            onToggleArchive()
                        },
                    )
                }
            }
        }
        if (state.archived) {
            Text(
                "Ta grupa jest zarchiwizowana. Przywróć ją, żeby dodawać wydatki.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(
                Icons.Outlined.Group,
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                size = 56.dp,
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    plural(state.members.size, "osoba", "osoby", "osób"),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val balance = state.myBalance
                Text(
                    when {
                        balance > 0 -> "Grupa jest ci winna"
                        balance < 0 -> "Jesteś winien grupie"
                        else -> "Twoje saldo w grupie"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    Money.format(kotlin.math.abs(balance)),
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        balance > 0 -> Positive
                        balance < 0 -> Negative
                        else -> MaterialTheme.colorScheme.onBackground
                    },
                )
            }
        }

        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val buttonPadding = PaddingValues(horizontal = 8.dp)
            FilledTonalButton(
                onClick = onSettle,
                modifier = Modifier.weight(1f).height(44.dp),
                contentPadding = buttonPadding,
            ) { Text("Rozlicz się") }
            OutlinedButton(
                onClick = { showMembers = !showMembers },
                modifier = Modifier.weight(1f).height(44.dp),
                contentPadding = buttonPadding,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) { Text("Salda") }
            OutlinedButton(
                onClick = onOpenStats,
                modifier = Modifier.weight(1f).height(44.dp),
                contentPadding = buttonPadding,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) { Text("Statystyki") }
        }

        // ---- Members and their balances ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Członkowie",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAddMember) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dodaj osobę")
            }
        }
        if (showMembers) {
            state.members.forEach { MemberRow(it) }
        }
        if (state.loaded && state.members.size <= 1) {
            EmptyHint("Dodaj znajomych, żeby dzielić z nimi wydatki.")
        }

        // ---- Expenses by month ----
        if (state.loaded && state.expenses.isEmpty()) {
            Text(
                monthTitle(YearMonth.now()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp),
            )
            EmptyHint("Brak wydatków w tej grupie.")
        }
        state.expenses
            .groupBy { YearMonth.from(LocalDate.ofEpochDay(it.date)) }
            .forEach { (month, monthExpenses) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 4.dp),
                ) {
                    Text(
                        monthTitle(month),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        // repayments move money between people, they are not spending
                        Money.format(monthExpenses.filter { !it.isSettlement }.sumOf { it.amount }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                monthExpenses.forEach { expense -> ExpenseRow(expense, onClick = { onExpenseClick(expense.id) }) }
            }
    }
}

@Composable
private fun MemberRow(member: MemberBalance) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterAvatar(initialOf(member.name), avatarColorsFor(member.userId), size = 36.dp)
        Spacer(Modifier.width(16.dp))
        Text(
            if (member.isMe && !member.name.equals("Ty", ignoreCase = true)) "${member.name} (ty)" else member.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        when {
            member.balance > 0 -> BalanceAmount("na plusie", Money.format(member.balance), positive = true)
            member.balance < 0 -> BalanceAmount("na minusie", Money.format(-member.balance), positive = false)
            else -> Text(
                "rozliczony",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseListRow, onClick: () -> Unit) {
    val date = LocalDate.ofEpochDay(expense.date)
    val colors = categoryAvatarColors(expense.category)
    val subtitle = when {
        expense.isSettlement -> "Spłata · ${Money.format(expense.amount)}"
        expense.payerIsMe -> "Płaciłeś · ${Money.format(expense.amount)}"
        else -> "Płacił(a) ${expense.payerName} · ${Money.format(expense.amount)}"
    }
    val lentByMe = expense.amount - expense.myShare

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(monthShortName(date), fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date.dayOfMonth.toString(), fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
        }
        IconTile(
            if (expense.isSettlement) Icons.Outlined.Payments else categoryIcon(expense.category),
            colors.container,
            colors.content,
            size = 40.dp,
            shape = RoundedCornerShape(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(expense.description, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(subtitle, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        when {
            // A repayment: I paid someone back (I was the payer) or someone paid me back
            expense.isSettlement && expense.payerIsMe -> BalanceAmount("spłaciłeś", Money.format(expense.amount), positive = true)
            expense.isSettlement && expense.myShare > 0 -> BalanceAmount("spłacono ci", Money.format(expense.amount), positive = false)
            expense.isSettlement -> Text(
                "nie dotyczy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            expense.payerIsMe && lentByMe > 0 -> BalanceAmount("pożyczyłeś", Money.format(lentByMe), positive = true)
            !expense.payerIsMe && expense.myShare > 0 -> BalanceAmount("pożyczono ci", Money.format(expense.myShare), positive = false)
            else -> Text(
                "nie dotyczy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Details of one expense with the option to delete it. */
@Composable
private fun ExpenseDetailsDialog(expense: ExpenseListRow, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(expense.description) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailLine("Kwota", Money.format(expense.amount))
                DetailLine("Data", dayLabel(LocalDate.ofEpochDay(expense.date)))
                DetailLine("Płacił(a)", if (expense.payerIsMe) "Ty" else expense.payerName)
                if (!expense.isSettlement) {
                    DetailLine("Kategoria", categoryLabel(expense.category))
                    DetailLine("Uczestnicy", plural(expense.participantCount, "osoba", "osoby", "osób"))
                    DetailLine("Twój udział", Money.format(expense.myShare))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** Short code of the group, e.g. "FS-A". It can be typed in "Dołącz z kodu" on the Grupy tab. */
@Composable
private fun GroupCodeDialog(code: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kod grupy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(code, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Grupa jest zapisana tylko na tym telefonie. Kod pozwala ją szybko otworzyć na zakładce Grupy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
        dismissButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("Kopiuj") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMemberDialog(
    contacts: List<UserEntity>,
    onDismiss: () -> Unit,
    onAddName: (String) -> Unit,
    onAddContact: (Long) -> Unit,
) {
    TextInputDialog(
        title = "Dodaj osobę",
        label = "Imię",
        confirmLabel = "Dodaj",
        onDismiss = onDismiss,
        onConfirm = onAddName,
    ) {
        if (contacts.isNotEmpty()) {
            Text(
                "Albo wybierz z innych grup:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                contacts.forEach { contact ->
                    AssistChip(
                        onClick = {
                            onAddContact(contact.id)
                            onDismiss()
                        },
                        label = { Text(contact.name) },
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun GrupaPreview() {
    val today = LocalDate.now().toEpochDay()
    FairShareTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            GrupaContent(
                GroupUiState(
                    loaded = true,
                    name = "Mieszkanie",
                    members = listOf(
                        MemberBalance(1, "Ty", isMe = true, balance = 8_000),
                        MemberBalance(2, "Ania", isMe = false, balance = -4_000),
                        MemberBalance(3, "Bartek", isMe = false, balance = -4_000),
                    ),
                    expenses = listOf(
                        ExpenseListRow(1, "Zakupy Biedronka", "GROCERIES", 12_000, today, 1, "Ty", true, 4_000, 3),
                    ),
                )
            )
        }
    }
}
