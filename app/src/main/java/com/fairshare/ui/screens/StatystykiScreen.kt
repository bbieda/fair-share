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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fairshare.ui.components.AppFilterChip
import com.fairshare.ui.components.ColorDot
import com.fairshare.ui.components.ScreenHeader
import com.fairshare.ui.components.SectionCard
import com.fairshare.ui.sample.CategoryShare
import com.fairshare.ui.sample.sampleCategories
import com.fairshare.ui.theme.FairShareTheme

// Bar heights in dp; 0 = no spending that month
private val lastMonths = listOf("maj" to 0, "cze" to 0, "lip" to 0, "sie" to 0, "wrz" to 0, "paź" to 0)

@Composable
fun StatystykiScreen(onOpenProfile: () -> Unit = {}) {
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
            AppFilterChip("Październik 2026", selected = false, onClick = {}, dropdown = true)
            AppFilterChip("Mój udział", selected = true, onClick = {})
            AppFilterChip("Wszystkie grupy", selected = false, onClick = {}, dropdown = true)
        }

        Spacer(Modifier.height(8.dp))
        MonthTotalCard()
        Spacer(Modifier.height(12.dp))
        StructureCard()
        Spacer(Modifier.height(12.dp))
        LastMonthsCard()
        Spacer(Modifier.height(12.dp))
        BudgetCard()
    }
}

@Composable
private fun MonthTotalCard() {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                "Twój udział w wydatkach",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "0 zł",
                style = MaterialTheme.typography.displaySmall,
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                "średnio 0,00 zł dziennie",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun StructureCard() {
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
                    DonutChart(sampleCategories, Modifier.size(128.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("0 zł", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
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
                    if (sampleCategories.isEmpty()) {
                        Text(
                            "Brak wydatków w tym miesiącu",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    sampleCategories.forEach { category ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ColorDot(category.color)
                            Text(category.shortName, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("${category.percent}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutChart(categories: List<CategoryShare>, modifier: Modifier = Modifier) {
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier) {
        val strokeWidth = 18.dp.toPx()
        val radius = size.minDimension * 50f / 128f
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)
        if (categories.isEmpty()) {
            drawArc(emptyColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(width = strokeWidth))
        }
        val gapDegrees = 2.3f
        var start = -90f
        categories.forEach { category ->
            val sweep = category.percent / 100f * 360f
            drawArc(
                color = category.color,
                startAngle = start,
                sweepAngle = sweep - gapDegrees,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth),
            )
            start += sweep
        }
    }
}

@Composable
private fun LastMonthsCard() {
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
                lastMonths.forEachIndexed { index, (label, height) ->
                    val current = index == lastMonths.lastIndex
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(maxOf(height, 4).dp)
                                .background(
                                    if (current) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    RoundedCornerShape(6.dp),
                                ),
                        )
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                            color = if (current) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetCard() {
    SectionCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Budżet",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text("0 / 0 zł", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp)),
            )
            Text(
                "Nie ustawiono budżetu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun StatystykiPreview() {
    FairShareTheme {
        Surface(color = MaterialTheme.colorScheme.background) { StatystykiScreen() }
    }
}
