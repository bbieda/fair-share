package com.fairshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fairshare.data.db.CategoryTotal
import com.fairshare.data.db.PersonBalance
import com.fairshare.data.model.Money
import com.fairshare.ui.components.AppFilterChip
import com.fairshare.ui.components.BalanceAmount
import com.fairshare.ui.components.ColorDot
import com.fairshare.ui.components.EmptyHint
import com.fairshare.ui.components.LetterAvatar
import com.fairshare.ui.components.ScreenHeader
import com.fairshare.ui.components.SectionCard
import com.fairshare.ui.components.avatarColorsFor
import com.fairshare.ui.components.categoryChartColor
import com.fairshare.ui.components.categoryLabel
import com.fairshare.ui.components.initialOf
import com.fairshare.ui.components.monthTitle
import com.fairshare.ui.sample.CategoryShare
import com.fairshare.ui.theme.FairShareTheme
import com.fairshare.ui.theme.Negative
import com.fairshare.ui.theme.Positive
import com.fairshare.ui.viewmodel.AppViewModelProvider
import com.fairshare.ui.viewmodel.DashboardUiState
import com.fairshare.ui.viewmodel.DashboardViewModel
import kotlin.math.abs

@Composable
fun PulpitScreen(
    onOpenProfile: () -> Unit = {},
    onSettle: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PulpitContent(state, onOpenProfile, onSettle, onOpenStats)
}

@Composable
private fun PulpitContent(
    state: DashboardUiState,
    onOpenProfile: () -> Unit = {},
    onSettle: () -> Unit = {},
    onOpenStats: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
    ) {
        ScreenHeader("Pulpit", onProfileClick = onOpenProfile)
        Spacer(Modifier.height(12.dp))
        BalanceCard(state)
        Spacer(Modifier.height(12.dp))
        MonthSummaryCard(state, onOpenStats)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Rozliczenia z osobami",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSettle) { Text("Rozlicz się") }
        }

        var filter by rememberSaveable { mutableIntStateOf(0) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Wszyscy", "Winni mi", "Ja winien").forEachIndexed { index, label ->
                AppFilterChip(label, selected = filter == index, onClick = { filter = index })
            }
        }

        val people = when (filter) {
            1 -> state.people.filter { it.balance > 0 }
            2 -> state.people.filter { it.balance < 0 }
            else -> state.people
        }
        if (state.people.isEmpty()) {
            EmptyHint("Brak rozliczeń. Dodaj pierwszy wydatek.")
        } else if (people.isEmpty()) {
            EmptyHint("Nikt tu nie pasuje.")
        } else {
            people.forEach { PersonRow(it) }
        }
    }
}

@Composable
private fun BalanceCard(state: DashboardUiState) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (state.net >= 0) "Ogólnie tobie są winni" else "Ogólnie jesteś winien",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                Money.format(abs(state.net)),
                style = MaterialTheme.typography.displaySmall,
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniBalance("Winni tobie", Money.format(state.owedToMe), Positive, Modifier.weight(1f))
                MiniBalance("Ty winien", Money.format(state.iOwe), Negative, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniBalance(label: String, amount: String, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun MonthSummaryCard(state: DashboardUiState, onClick: () -> Unit) {
    val total = state.monthShare
    val categories = state.categories.filter { it.total > 0 }.map { item ->
        // categoryLabel/categoryChartColor also handle the user's own categories
        CategoryShare(
            name = categoryLabel(item.category),
            shortName = categoryLabel(item.category),
            percent = if (total > 0) ((item.total * 100 + total / 2) / total).toInt() else 0,
            color = categoryChartColor(item.category),
        )
    }
    SectionCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column {
                Text(
                    "${monthTitle(state.month)} · twój udział",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(Money.format(total), style = MaterialTheme.typography.headlineSmall)
            }

            if (categories.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            } else Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                categories.forEach { category ->
                    Box(
                        Modifier
                            .weight(category.percent.coerceAtLeast(1).toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(category.color),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                categories.take(2).forEachIndexed { index, category ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ColorDot(category.color)
                        Text(
                            "${category.name} ${category.percent}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (index == 0) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(person: PersonBalance) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterAvatar(initialOf(person.name), avatarColorsFor(person.userId))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                person.groupNames.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            person.balance > 0 -> BalanceAmount("winien ci", Money.format(person.balance), positive = true)
            person.balance < 0 -> BalanceAmount("jesteś winien", Money.format(-person.balance), positive = false)
            else -> Text(
                "rozliczone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PulpitPreview() {
    FairShareTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PulpitContent(
                DashboardUiState(
                    people = listOf(
                        PersonBalance(2, "Ania", 18_600, "Mieszkanie"),
                        PersonBalance(3, "Kasia", -4_250, "Wyjazd w góry"),
                    ),
                    categories = listOf(CategoryTotal("FOOD", 32_000), CategoryTotal("HOME", 21_000)),
                ),
            )
        }
    }
}
