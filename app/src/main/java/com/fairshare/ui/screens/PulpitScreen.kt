package com.fairshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fairshare.ui.components.AppFilterChip
import com.fairshare.ui.components.BalanceAmount
import com.fairshare.ui.components.ColorDot
import com.fairshare.ui.components.EmptyHint
import com.fairshare.ui.components.LetterAvatar
import com.fairshare.ui.components.ScreenHeader
import com.fairshare.ui.components.SectionCard
import com.fairshare.ui.sample.sampleCategories
import com.fairshare.ui.theme.AvatarColors
import com.fairshare.ui.theme.FairShareTheme
import com.fairshare.ui.theme.Negative
import com.fairshare.ui.theme.Positive

private data class PersonBalance(
    val name: String,
    val subtitle: String,
    val caption: String,
    val amount: String,
    val positive: Boolean,
    val avatar: AvatarColors,
)

private val people = emptyList<PersonBalance>()

@Composable
fun PulpitScreen(
    onOpenProfile: () -> Unit = {},
    onSettle: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
    ) {
        ScreenHeader("Pulpit", onProfileClick = onOpenProfile)
        Spacer(Modifier.height(12.dp))
        BalanceCard()
        Spacer(Modifier.height(12.dp))
        MonthSummaryCard()
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

        var filter by remember { mutableIntStateOf(0) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Wszyscy", "Winni mi", "Ja winien").forEachIndexed { index, label ->
                AppFilterChip(label, selected = filter == index, onClick = { filter = index })
            }
        }

        if (people.isEmpty()) {
            EmptyHint("Brak rozliczeń. Dodaj pierwszy wydatek.")
        } else {
            people.forEach { PersonRow(it) }
        }
    }
}

@Composable
private fun BalanceCard() {
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
            Text("Ogólnie tobie są winni", style = MaterialTheme.typography.titleSmall)
            Text(
                "0,00 zł",
                style = MaterialTheme.typography.displaySmall,
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniBalance("Winni tobie", "0,00 zł", Positive, Modifier.weight(1f))
                MiniBalance("Ty winien", "0,00 zł", Negative, Modifier.weight(1f))
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
private fun MonthSummaryCard() {
    SectionCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column {
                Text(
                    "Październik · twój udział",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("0 zł", style = MaterialTheme.typography.headlineSmall)
            }

            if (sampleCategories.isEmpty()) {
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
                sampleCategories.forEach { category ->
                    Box(
                        Modifier
                            .weight(category.percent.toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(category.color),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                sampleCategories.take(2).forEachIndexed { index, category ->
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
            .height(60.dp)
            .clickable {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterAvatar(person.name.first().toString(), person.avatar)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                person.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BalanceAmount(person.caption, person.amount, person.positive)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PulpitPreview() {
    FairShareTheme {
        Surface(color = MaterialTheme.colorScheme.background) { PulpitScreen() }
    }
}
