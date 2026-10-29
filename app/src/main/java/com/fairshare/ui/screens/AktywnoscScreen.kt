package com.fairshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fairshare.ui.components.AppFilterChip
import com.fairshare.ui.components.ColorDot
import com.fairshare.ui.components.LetterAvatar
import com.fairshare.ui.components.ScreenHeader
import com.fairshare.ui.theme.AvatarColors
import com.fairshare.ui.theme.FairShareTheme
import com.fairshare.ui.theme.UnreadBackground

private data class ActivityItem(
    val avatar: String,
    val avatarColors: AvatarColors,
    val actor: String,
    val action: String,
    val detail: String,
    val time: String,
    val unread: Boolean = false,
)

private val activitySections = listOf(
    "Dziś" to listOf(
        ActivityItem("A", AvatarColors.Pink, "Ania", "dodała „Biedronka”", "Mieszkanie · twój udział 21,85 zł", "2 godz.", unread = true),
        ActivityItem("O", AvatarColors.Orange, "Ola", "skomentowała „Nocleg”", "Wyjazd Zakopane", "5 godz.", unread = true),
    ),
    "Wczoraj" to listOf(
        ActivityItem("T", AvatarColors.Green, "Tomek", "dodał „Bilety na kolejkę”", "Wyjazd Zakopane · twój udział 42,00 zł", "18:40", unread = true),
        ActivityItem("K", AvatarColors.Blue, "Kuba", "zmienił „Internet”", "Mieszkanie · 75,00 → 80,00 zł", "11:05"),
    ),
    "Wcześniej" to listOf(
        ActivityItem("Ty", AvatarColors.Teal, "Dodałeś", "„Pizza wieczór”", "Mieszkanie · pożyczyłeś 99,00 zł", "15 paź"),
        ActivityItem("K", AvatarColors.Blue, "Kuba", "oddał ci 200,00 zł", "Mieszkanie · spłata", "5 paź"),
    ),
)

@Composable
fun AktywnoscScreen() {
    // Rows run edge to edge, so horizontal padding is applied per element
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        ScreenHeader("Aktywność", modifier = Modifier.padding(horizontal = 16.dp)) {
            IconButton(onClick = {}, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Outlined.DoneAll,
                    contentDescription = "Oznacz wszystko jako przeczytane",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        var filter by remember { mutableIntStateOf(0) }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Wszystko", "Dotyczy mnie").forEachIndexed { index, label ->
                AppFilterChip(label, selected = filter == index, onClick = { filter = index })
            }
        }

        Spacer(Modifier.height(8.dp))
        activitySections.forEach { (title, items) ->
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .height(36.dp)
                    .padding(top = 8.dp),
            )
            items.forEach { ActivityRow(it) }
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (item.unread) UnreadBackground else Color.Transparent)
            .clickable {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        LetterAvatar(item.avatar, item.avatarColors)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(item.actor) }
                    append(" ")
                    append(item.action)
                },
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
            Text(
                item.detail,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                item.time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.unread) ColorDot(MaterialTheme.colorScheme.primary)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AktywnoscPreview() {
    FairShareTheme {
        Surface(color = MaterialTheme.colorScheme.background) { AktywnoscScreen() }
    }
}
