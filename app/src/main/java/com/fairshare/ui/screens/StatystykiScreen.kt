package com.fairshare.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fairshare.data.db.CategoryTotal
import com.fairshare.data.model.ExpenseCategory
import com.fairshare.data.model.Money
import com.fairshare.ui.components.AppFilterChip
import com.fairshare.ui.components.ColorDot
import com.fairshare.ui.components.ScreenHeader
import com.fairshare.ui.components.SectionCard
import com.fairshare.ui.components.labelOrDefault
import com.fairshare.ui.theme.FairShareTheme
import com.fairshare.ui.viewmodel.AppViewModelProvider
import com.fairshare.ui.viewmodel.StatystykiViewModel

@Composable
fun StatystykiScreen(
    onOpenProfile: () -> Unit = {},
    viewModel: StatystykiViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
            AppFilterChip("${state.month.month.name} ${state.month.year}", selected = false, onClick = {}, dropdown = true)
            AppFilterChip("Mój udział", selected = true, onClick = {})
            AppFilterChip("Wszystkie grupy", selected = false, onClick = {}, dropdown = true)
        }

        Spacer(Modifier.height(8.dp))
        MonthTotalCard(totalGrosze = state.monthTotal)
        Spacer(Modifier.height(12.dp))
        StructureCard(categories = state.categories, totalGrosze = state.monthTotal)
        Spacer(Modifier.height(12.dp))
        LastMonthsCard(history = state.monthlyHistory)
        Spacer(Modifier.height(12.dp))
        BudgetCard(monthTotalGrosze = state.monthTotal)
    }
}

@Composable
private fun MonthTotalCard(totalGrosze: Long) {
    val daysInMonth = 30
    val avgPerDayGrosze = totalGrosze / daysInMonth

    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                "Twój udział w wydatkach",
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
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Struktura wydatków",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {}) { Text("Kategorie") }
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                    DonutChart(categories = categories, totalGrosze = totalGrosze, modifier = Modifier.size(128.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Money.format(totalGrosze), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "razem",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                        val percent = if (totalGrosze > 0) (cat.total * 100 / totalGrosze).toInt() else 0
                        val categoryEnum = ExpenseCategory.entries.firstOrNull { it.name == cat.category }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ColorDot(MaterialTheme.colorScheme.primary)
                            Text(categoryEnum.labelOrDefault, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("$percent%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutChart(categories: List<CategoryTotal>, totalGrosze: Long, modifier: Modifier = Modifier) {
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier) {
        val strokeWidth = 18.dp.toPx()
        val radius = size.minDimension * 50f / 128f
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)

        if (categories.isEmpty() || totalGrosze <= 0) {
            drawArc(emptyColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(width = strokeWidth))
        } else {
            var start = -90f
            categories.forEach { cat ->
                val sweep = (cat.total.toFloat() / totalGrosze.toFloat()) * 360f
                drawArc(
                    color = primaryColor,
                    startAngle = start,
                    sweepAngle = sweep - 2f,
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
                    val heightDp = if (amount > 0) 60 else 8
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
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
private fun BudgetCard(monthTotalGrosze: Long) {
    SectionCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Budżet",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text("${Money.format(monthTotalGrosze)} / 0 zł", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp)),
            )
            Text(
                "Nie ustawiono limitu budżetu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}