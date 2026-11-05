package com.fairshare.ui.screens

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fairshare.data.db.ActivityLogEntity
import com.fairshare.ui.components.AppFilterChip
import com.fairshare.ui.components.EmptyHint
import com.fairshare.ui.components.LetterAvatar
import com.fairshare.ui.components.ScreenHeader
import com.fairshare.ui.theme.AvatarColors
import com.fairshare.ui.theme.FairShareTheme
import com.fairshare.ui.viewmodel.AktywnoscViewModel
import com.fairshare.ui.viewmodel.AppViewModelProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AktywnoscScreen(
    onOpenProfile: () -> Unit = {},
    viewModel: AktywnoscViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        ScreenHeader(
            "Aktywność",
            modifier = Modifier.padding(horizontal = 16.dp),
            onProfileClick = onOpenProfile,
        ) {
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

        if (logs.isEmpty()) {
            EmptyHint("Brak aktywności.", modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            Text(
                "Ostatnie zdarzenia",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .height(36.dp)
                    .padding(top = 8.dp),
            )
            logs.forEach { log -> ActivityRow(log) }
        }
    }
}

@Composable
private fun ActivityRow(log: ActivityLogEntity) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeString = remember(log.timestamp) { timeFormatter.format(Date(log.timestamp)) }

    // Dynamicznie wybiera jeden z dostępnych kolorów z AvatarColors na podstawie nazwy akcji
    val avatarColor = remember(log.actionType) {
        val colors = AvatarColors.values()
        colors[(log.actionType.hashCode() and 0x7FFFFFFF) % colors.size]
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        LetterAvatar(
            text = log.actionType.firstOrNull()?.toString() ?: "A",
            colors = avatarColor
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(log.actionType) }
                },
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
            Text(
                log.description,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            timeString,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}