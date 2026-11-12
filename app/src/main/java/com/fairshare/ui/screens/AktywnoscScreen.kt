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
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fairshare.data.db.ActivityItem
import com.fairshare.ui.components.AppFilterChip
import com.fairshare.ui.components.EmptyHint
import com.fairshare.ui.components.IconTile
import com.fairshare.ui.components.ScreenHeader
import com.fairshare.ui.components.categoryAvatarColors
import com.fairshare.ui.components.dayLabelOf
import com.fairshare.ui.theme.UnreadBackground
import com.fairshare.ui.viewmodel.AktywnoscViewModel
import com.fairshare.ui.viewmodel.AppViewModelProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AktywnoscScreen(
    onOpenProfile: () -> Unit = {},
    onOpenGroup: (Long) -> Unit = {},
    viewModel: AktywnoscViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableIntStateOf(0) }

    // 0 = everything, 1 = only entries that involve me
    val visible = if (filter == 1) logs.filter { it.involvesMe } else logs

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
            actions = {
                IconButton(
                    onClick = viewModel::markAllRead,
                    enabled = unread > 0,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        Icons.Outlined.DoneAll,
                        contentDescription = "Oznacz wszystko jako przeczytane",
                        tint = if (unread > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Wszystko", "Dotyczy mnie").forEachIndexed { index, label ->
                AppFilterChip(label, selected = filter == index, onClick = { filter = index })
            }
        }

        Spacer(Modifier.height(8.dp))

        if (visible.isEmpty()) {
            EmptyHint(
                if (logs.isEmpty()) "Brak aktywności. Dodaj wydatek albo grupę." else "Nic tu nie pasuje do filtra.",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            // The list is sorted by time, so entries of one day are next to each other
            visible.groupBy { dayLabelOf(it.timestamp) }.forEach { (day, items) ->
                Text(
                    day,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .height(36.dp)
                        .padding(top = 8.dp),
                )
                items.forEach { item ->
                    ActivityRow(
                        item = item,
                        onClick = {
                            if (!item.seen) viewModel.markRead(item.id)
                            item.groupId?.let(onOpenGroup)
                        },
                    )
                }
            }
        }
    }
}

/** Title and icon shown for each action type stored in the log. */
private data class ActionStyle(val title: String, val icon: ImageVector, val colorKey: String)

private fun styleOf(actionType: String): ActionStyle = when (actionType) {
    "ADD_EXPENSE" -> ActionStyle("Nowy wydatek", Icons.Outlined.Receipt, "FOOD")
    "SETTLEMENT" -> ActionStyle("Spłata", Icons.Outlined.Payments, "GROCERIES")
    "DELETE_EXPENSE" -> ActionStyle("Usunięto", Icons.Outlined.DeleteOutline, "TRANSPORT")
    "CREATE_GROUP" -> ActionStyle("Nowa grupa", Icons.Outlined.Group, "HOME")
    "ADD_MEMBER" -> ActionStyle("Nowa osoba", Icons.Outlined.PersonAdd, "FUN")
    "RENAME_GROUP" -> ActionStyle("Zmiana nazwy", Icons.Outlined.Edit, "OTHER")
    "ARCHIVE_GROUP" -> ActionStyle("Archiwum", Icons.Outlined.Archive, "OTHER")
    "PROFILE_UPDATE" -> ActionStyle("Profil", Icons.Outlined.Person, "OTHER")
    else -> ActionStyle(actionType, Icons.AutoMirrored.Outlined.Label, "OTHER")
}

@Composable
private fun ActivityRow(item: ActivityItem, onClick: () -> Unit) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeString = remember(item.timestamp) { timeFormatter.format(Date(item.timestamp)) }
    val style = remember(item.actionType) { styleOf(item.actionType) }
    val colors = remember(item.actionType) { categoryAvatarColors(style.colorKey) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // unread entries have a slightly different background
            .background(if (item.seen) androidx.compose.ui.graphics.Color.Transparent else UnreadBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconTile(
            style.icon,
            colors.container,
            colors.content,
            size = 40.dp,
            shape = androidx.compose.foundation.shape.CircleShape,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                style.title + (item.groupName?.let { " · $it" } ?: ""),
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = if (item.seen) FontWeight.Medium else FontWeight.SemiBold,
            )
            Text(
                item.description,
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
