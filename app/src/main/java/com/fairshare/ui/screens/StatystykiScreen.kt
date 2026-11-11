package com.fairshare.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fairshare.data.db.CategoryTotal
import com.fairshare.data.model.Money
import com.fairshare.ui.components.AppFilterChip
import com.fairshare.ui.components.ColorDot
import com.fairshare.ui.components.ScreenHeader
import com.fairshare.ui.components.SectionCard
import com.fairshare.ui.components.TextInputDialog
import com.fairshare.ui.components.categoryChartColor
import com.fairshare.ui.components.categoryLabel
import com.fairshare.ui.components.monthTitle
import com.fairshare.ui.viewmodel.AppViewModelProvider
import com.fairshare.ui.viewmodel.StatystykiViewModel
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun StatystykiScreen(
    onOpenProfile: () -> Unit = {},
    viewModel: StatystykiViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var monthMenu by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    var editBudget by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        ScreenHeader("Statystyki", onProfileClick = onOpenProfile)

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- Month ----
            Box {
                AppFilterChip(monthTitle(state.month), selected = false, onClick = { monthMenu = true }, dropdown = true)
                DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }) {
                    viewModel.monthOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(monthTitle(option)) },
                            onClick = {
                                viewModel.selectMonth(option)
                                monthMenu = false
                            },
                        )
                    }
                }
            }
            // ---- My share / everything ----
            AppFilterChip(
                if (state.scopeMine) "Mój udział" else "Wszystkie wydatki",
                selected = state.scopeMine,
                onClick = viewModel::toggleScope,
            )
            // ---- Group ----
            Box {
                val groupName = state.groups.firstOrNull { it.id == state.selectedGroupId }?.name ?: "Wszystkie grupy"
                AppFilterChip(groupName, selected = false, onClick = { groupMenu = true }, dropdown = true)
                DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Wszystkie grupy") },
                        onClick = {
                            viewModel.selectGroup(-1L)
                            groupMenu = false
                        },
                    )
                    state.groups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group.name) },
                            onClick = {
                                viewModel.selectGroup(group.id)
                                groupMenu = false
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        MonthTotalCard(month = state.month, totalGrosze = state.monthTotal, mine = state.scopeMine)
        Spacer(Modifier.height(12.dp))
        StructureCard(categories = state.categories, totalGrosze = state.monthTotal)
        Spacer(Modifier.height(12.dp))
        LastMonthsCard(history = state.monthlyHistory)
        Spacer(Modifier.height(12.dp))
        BudgetCard(
            monthTotalGrosze = state.monthTotal,
            budgetGrosze = state.budgetGrosze,
            // the budget is a personal limit, so it only makes sense for "my share"
            mine = state.scopeMine,
            onClick = { editBudget = true },
        )
    }

    if (editBudget) {
        TextInputDialog(
            title = "Miesięczny budżet",
            label = "Kwota, 0 = bez limitu",
            confirmLabel = "Zapisz",
            initialValue = if (state.budgetGrosze > 0) Money.formatInput(state.budgetGrosze) else "",
            onDismiss = { editBudget = false },
            validate = { text -> if (text == "0" || Money.parse(text) != null) null else "Wpisz poprawną kwotę, np. 2000" },
            onConfirm = { text -> viewModel.setBudget(if (text == "0") 0L else Money.parse(text) ?: 0L) },
        )
    }
}

@Composable
private fun MonthTotalCard(month: YearMonth, totalGrosze: Long, mine: Boolean) {
    // Days that already passed: for the current month up to today, otherwise the whole month
    val today = LocalDate.now()
    val days = when {
        month == YearMonth.from(today) -> today.dayOfMonth
        month.isAfter(YearMonth.from(today)) -> 1
        else -> month.lengthOfMonth()
    }
    val avgPerDayGrosze = totalGrosze / days

    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                if (mine) "Twój udział w wydatkach" else "Wydatki grupy",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                Money.format(totalGrosze),
                style = MaterialTheme.typography.displaySmall,
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                "średnio ${Money.format(avgPerDayGrosze)} dziennie",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun StructureCard(categories: List<CategoryTotal>, totalGrosze: Long) {
    // Categories are summed in the query; here only the total of this list is used for percents
    val sum = categories.sumOf { it.total }
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Struktura wydatków", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                    DonutChart(categories = categories, totalGrosze = sum, modifier = Modifier.size(128.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Money.format(totalGrosze), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("razem", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (categories.isEmpty()) {
                        Text(
                            "Brak wydatków w tym miesiącu",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    categories.forEach { cat ->
                        val percent = if (sum > 0) ((cat.total * 100 + sum / 2) / sum).toInt() else 0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ColorDot(categoryChartColor(cat.category))
                            Text(categoryLabel(cat.category), fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("$percent%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            Money.format(cat.total),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutChart(categories: List<CategoryTotal>, totalGrosze: Long, modifier: Modifier = Modifier) {
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val colors = categories.map { categoryChartColor(it.category) }

    Canvas(modifier) {
        val strokeWidth = 18.dp.toPx()
        val radius = size.minDimension * 50f / 128f
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)

        if (categories.isEmpty() || totalGrosze <= 0) {
            drawArc(emptyColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(width = strokeWidth))
        } else {
            var start = -90f
            categories.forEachIndexed { index, cat ->
                val sweep = (cat.total.toFloat() / totalGrosze.toFloat()) * 360f
                // a small gap between slices, but never a negative sweep
                val gap = if (categories.size > 1) 2f else 0f
                drawArc(
                    color = colors[index],
                    startAngle = start,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth),
                )
                start += sweep
            }
        }
    }
}

@Composable
private fun LastMonthsCard(history: List<Pair<String, Long>>) {
    val max = history.maxOfOrNull { it.second } ?: 0L
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Ostatnie 6 miesięcy", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                history.forEachIndexed { index, (label, amount) ->
                    val isCurrent = index == history.lastIndex
                    // bar height is proportional to the biggest month (8 dp minimum so empty months are visible)
                    val heightDp = if (amount > 0 && max > 0) (8 + 72f * amount / max).toInt() else 8
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (amount > 0) {
                            Text(
                                Money.format(amount, withCurrency = false).substringBefore(','),
                                fontSize = 10.sp,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(heightDp.dp)
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    RoundedCornerShape(6.dp),
                                ),
                        )
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetCard(monthTotalGrosze: Long, budgetGrosze: Long, mine: Boolean, onClick: () -> Unit) {
    val hasBudget = budgetGrosze > 0
    val progress = if (hasBudget) (monthTotalGrosze.toFloat() / budgetGrosze).coerceIn(0f, 1f) else 0f
    val over = hasBudget && monthTotalGrosze > budgetGrosze

    SectionCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Budżet",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${Money.format(monthTotalGrosze)} / ${Money.format(budgetGrosze)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp)),
            ) {
                if (progress > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(8.dp)
                            .background(
                                if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(4.dp),
                            ),
                    )
                }
            }
            Text(
                when {
                    !hasBudget -> "Nie ustawiono limitu. Dotknij, żeby go ustawić."
                    !mine -> "Budżet porównujemy z Twoim udziałem. Przełącz na „Mój udział”."
                    over -> "Przekroczono o ${Money.format(monthTotalGrosze - budgetGrosze)}"
                    else -> "Zostało ${Money.format(budgetGrosze - monthTotalGrosze)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (over && mine) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
