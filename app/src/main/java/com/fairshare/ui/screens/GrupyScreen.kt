package com.fairshare.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fairshare.ui.components.BalanceAmount
import com.fairshare.ui.components.IconTile
import com.fairshare.ui.components.OutlinedRow
import com.fairshare.ui.components.ScreenHeader
import com.fairshare.ui.components.SectionCard
import com.fairshare.ui.theme.AvatarColors
import com.fairshare.ui.theme.FairShareTheme
import com.fairshare.ui.theme.OnPrimaryContainer
import com.fairshare.ui.theme.OnTertiaryContainer
import com.fairshare.ui.theme.PrimaryContainer
import com.fairshare.ui.theme.TertiaryContainer

private data class GroupBalance(val caption: String, val amount: String, val positive: Boolean)

private data class GroupItem(
    val name: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconContainer: Color,
    val iconContent: Color,
    /** null = group is settled */
    val balance: GroupBalance?,
)

private val groups = listOf(
    GroupItem(
        "Mieszkanie", "4 osoby · Ania dodała „Biedronka”", Icons.Outlined.Home,
        PrimaryContainer, OnPrimaryContainer, GroupBalance("winni ci", "336,00 zł", true),
    ),
    GroupItem(
        "Wyjazd Zakopane", "4 osoby · Tomek dodał „Bilety na kolejkę”", Icons.Outlined.Landscape,
        TertiaryContainer, OnTertiaryContainer, GroupBalance("jesteś winien", "90,50 zł", false),
    ),
    GroupItem(
        "Kolacje z ekipą", "6 osób · ostatnio 12 paź", Icons.Outlined.Restaurant,
        AvatarColors.Pink.container, AvatarColors.Pink.content, null,
    ),
)

@Composable
fun GrupyScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
    ) {
        ScreenHeader("Grupy")
        Text(
            "Aktywne",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { GroupCard(it) }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedRow(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Zarchiwizowane (2)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {},
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Nowa grupa")
            }
            OutlinedButton(
                onClick = {},
                modifier = Modifier.weight(1f).height(44.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dołącz z linku")
            }
        }
    }
}

@Composable
private fun GroupCard(group: GroupItem) {
    SectionCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(group.icon, group.iconContainer, group.iconContent)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    group.subtitle,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(16.dp))
            val balance = group.balance
            if (balance != null) {
                BalanceAmount(balance.caption, balance.amount, balance.positive)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "rozliczone",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun GrupyPreview() {
    FairShareTheme {
        Surface(color = MaterialTheme.colorScheme.background) { GrupyScreen() }
    }
}
