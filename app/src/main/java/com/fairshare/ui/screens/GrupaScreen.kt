package com.fairshare.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fairshare.ui.components.BackHeader
import com.fairshare.ui.components.BalanceAmount
import com.fairshare.ui.components.EmptyHint
import com.fairshare.ui.components.IconTile
import com.fairshare.ui.theme.AvatarColors
import com.fairshare.ui.theme.FairShareTheme

private data class GroupExpense(
    val month: String,
    val day: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColors: AvatarColors,
    /** null = repayment, shown without a lent/borrowed caption */
    val caption: String?,
    val amount: String,
    val positive: Boolean,
)

private val expenses = emptyList<GroupExpense>()

@Composable
fun GrupaScreen(
    name: String,
    onBack: () -> Unit = {},
    onSettle: () -> Unit = {},
    onOpenStats: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
    ) {
        BackHeader(name, onBack) {
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
                Text("Brak członków", fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Saldo grupy",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text("0,00 zł", fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium)
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
                onClick = {},
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            Text(
                "Październik",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text("0,00 zł", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (expenses.isEmpty()) {
            EmptyHint("Brak wydatków w tej grupie.")
        } else {
            expenses.forEach { ExpenseRow(it) }
        }
    }
}

@Composable
private fun ExpenseRow(expense: GroupExpense) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(expense.month, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(expense.day, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
        }
        IconTile(
            expense.icon,
            expense.iconColors.container,
            expense.iconColors.content,
            size = 40.dp,
            shape = RoundedCornerShape(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(expense.title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
            Text(expense.subtitle, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expense.caption != null) {
            BalanceAmount(expense.caption, expense.amount, expense.positive)
        } else {
            Text(
                expense.amount,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun GrupaPreview() {
    FairShareTheme {
        Surface(color = MaterialTheme.colorScheme.background) { GrupaScreen("Mieszkanie") }
    }
}
