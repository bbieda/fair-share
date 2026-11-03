package com.fairshare.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fairshare.data.db.ExpenseListRow
import com.fairshare.data.db.MemberBalance
import com.fairshare.data.db.UserEntity
import com.fairshare.data.model.ExpenseCategory
import com.fairshare.data.model.Money
import com.fairshare.ui.components.BackHeader
import com.fairshare.ui.components.BalanceAmount
import com.fairshare.ui.components.ConfirmDialog
import com.fairshare.ui.components.EmptyHint
import com.fairshare.ui.components.IconTile
import com.fairshare.ui.components.LetterAvatar
import com.fairshare.ui.components.TextInputDialog
import com.fairshare.ui.components.avatarColorsFor
import com.fairshare.ui.components.icon
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
    onOpenStats: () -> Unit = {},
    viewModel: GroupViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddMember by rememberSaveable { mutableStateOf(false) }
    var expenseToDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    GrupaContent(
        state = state,
        onBack = onBack,
        onSettle = onSettle,
        onOpenStats = onOpenStats,
        onAddMember = { showAddMember = true },
        onExpenseClick = { expenseToDelete = it },
    )

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
            title = "Usunąć wydatek?",
            message = "„$title” zniknie z grupy, a salda zostaną przeliczone.",
            confirmLabel = "Usuń",
            onDismiss = { expenseToDelete = null },
            onConfirm = { viewModel.deleteExpense(expenseId) },
        )
    }
}

@Composable
private fun GrupaContent(
    state: GroupUiState,
    onBack: () -> Unit = {},
    onSettle: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onAddMember: () -> Unit = {},
    onExpenseClick: (Long) -> Unit = {},
) {
    var showMembers by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
    ) {
        BackHeader(state.name, onBack) {
            IconButton(onClick = {}, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Opcje grupy")
            }
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
                        Money.format(monthExpenses.sumOf { it.amount }),
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
    val category = ExpenseCategory.fromKey(expense.category)
    val colors = avatarColorsFor(category?.ordinal?.toLong() ?: 5L)
    val subtitle = if (expense.payerIsMe) {
        "Płaciłeś · ${Money.format(expense.amount)}"
    } else {
        "Płacił(a) ${expense.payerName} · ${Money.format(expense.amount)}"
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
            category.icon,
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
