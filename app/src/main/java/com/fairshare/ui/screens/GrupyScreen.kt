package com.fairshare.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fairshare.data.db.GroupSummary
import com.fairshare.data.model.Money
import com.fairshare.data.sync.JoinPreview
import com.fairshare.ui.components.BalanceAmount
import com.fairshare.ui.components.EmptyHint
import com.fairshare.ui.components.IconTile
import com.fairshare.ui.components.OutlinedRow
import com.fairshare.ui.components.ScreenHeader
import com.fairshare.ui.components.SectionCard
import com.fairshare.ui.components.TextInputDialog
import com.fairshare.ui.components.avatarColorsFor
import com.fairshare.ui.components.plural
import com.fairshare.ui.theme.FairShareTheme
import com.fairshare.ui.viewmodel.AppViewModelProvider
import com.fairshare.ui.viewmodel.GroupsViewModel

private data class GroupBalance(val caption: String, val amount: String, val positive: Boolean)

private data class GroupItem(
    val id: Long,
    val name: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconContainer: Color,
    val iconContent: Color,
    /** null = group is settled */
    val balance: GroupBalance?,
)

private fun GroupSummary.toItem(): GroupItem {
    val colors = avatarColorsFor(id)
    val parts = listOfNotNull(
        memberNames?.takeIf { it.isNotBlank() },
        plural(expenseCount, "wydatek", "wydatki", "wydatków"),
    )
    return GroupItem(
        id = id,
        name = name,
        subtitle = parts.joinToString(" · "),
        icon = Icons.Outlined.Group,
        iconContainer = colors.container,
        iconContent = colors.content,
        balance = when {
            myBalance > 0 -> GroupBalance("winni ci", Money.format(myBalance), positive = true)
            myBalance < 0 -> GroupBalance("jesteś winien", Money.format(-myBalance), positive = false)
            else -> null
        },
    )
}

@Composable
fun GrupyScreen(
    onOpenProfile: () -> Unit = {},
    onOpenGroup: (Long) -> Unit = {},
    viewModel: GroupsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var showNewGroup by rememberSaveable { mutableStateOf(false) }
    var showJoin by rememberSaveable { mutableStateOf(false) }

    GrupyContent(
        groups = groups,
        onOpenProfile = onOpenProfile,
        onOpenGroup = onOpenGroup,
        onNewGroup = { showNewGroup = true },
        onJoinGroup = { showJoin = true },
    )

    if (showJoin) {
        JoinGroupDialog(
            onDismiss = { showJoin = false },
            onPreview = viewModel::previewJoin,
            onJoin = { code, claim, onError ->
                viewModel.join(
                    code,
                    claim,
                    onJoined = { groupId ->
                        showJoin = false
                        onOpenGroup(groupId)
                    },
                    onError = onError,
                )
            },
        )
    }

    if (showNewGroup) {
        TextInputDialog(
            title = "Nowa grupa",
            label = "Nazwa, np. Mieszkanie albo Wyjazd w góry",
            confirmLabel = "Utwórz",
            onDismiss = { showNewGroup = false },
            onConfirm = { name -> viewModel.createGroup(name, onCreated = onOpenGroup) },
        )
    }
}

@Composable
private fun GrupyContent(
    groups: List<GroupSummary>?,
    onOpenProfile: () -> Unit = {},
    onOpenGroup: (Long) -> Unit = {},
    onNewGroup: () -> Unit = {},
    onJoinGroup: () -> Unit = {},
) {
    val active = groups.orEmpty().filter { !it.archived }.map { it.toItem() }
    val archived = groups.orEmpty().filter { it.archived }.map { it.toItem() }
    val archivedCount = archived.size
    var showArchived by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
    ) {
        ScreenHeader("Grupy", onProfileClick = onOpenProfile)
        Text(
            "Aktywne",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )

        if (groups == null) {
            // Still reading the database: show nothing instead of a flash of "no groups"
            Spacer(Modifier.height(72.dp))
        } else if (active.isEmpty()) {
            EmptyHint("Nie masz jeszcze żadnych grup. Utwórz pierwszą poniżej.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                active.forEach { group -> GroupCard(group, onClick = { onOpenGroup(group.id) }) }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedRow(modifier = Modifier.fillMaxWidth(), onClick = { showArchived = !showArchived }) {
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Zarchiwizowane ($archivedCount)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (showArchived) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showArchived) {
            Spacer(Modifier.height(8.dp))
            if (archived.isEmpty()) {
                EmptyHint("Brak zarchiwizowanych grup.")
            } else {
                // Opening an archived group lets you restore it from the group menu
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    archived.forEach { group -> GroupCard(group, onClick = { onOpenGroup(group.id) }) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = onNewGroup,
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Nowa grupa")
            }
            OutlinedButton(
                onClick = onJoinGroup,
                modifier = Modifier.weight(1f).height(44.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dołącz z kodu")
            }
        }
    }
}

@Composable
private fun GroupCard(group: GroupItem, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
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
        Surface(color = MaterialTheme.colorScheme.background) {
            GrupyContent(
                groups = listOf(
                    GroupSummary(1, "Mieszkanie", false, "Ty, Ania, Bartek", 3, 12, 18_600),
                    GroupSummary(2, "Wyjazd w góry", false, "Ty, Kasia", 2, 4, -4_250),
                    GroupSummary(3, "Kino", false, "Ty, Ola", 2, 1, 0),
                ),
            )
        }
    }
}

/**
 * Joining a group with its invite code, in two steps: the code is checked on the server, then the user confirms
 * and, when the group has people added by name, says which of them they are (their expenses become theirs).
 */
@Composable
private fun JoinGroupDialog(
    onDismiss: () -> Unit,
    onPreview: (raw: String, onPreview: (code: String, JoinPreview) -> Unit, onError: (String) -> Unit) -> Unit,
    onJoin: (code: String, claimPersonUid: String?, onError: (String) -> Unit) -> Unit,
) {
    var raw by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<Pair<String, JoinPreview>?>(null) }
    // uid of the person added by name the user says they are; "" = none of them
    var claim by remember { mutableStateOf<String?>(null) }
    val fail: (String) -> Unit = { message ->
        error = message
        busy = false
    }

    val preview = found?.second
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preview?.groupName ?: "Dołącz do grupy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (preview == null) {
                    OutlinedTextField(
                        value = raw,
                        onValueChange = {
                            raw = it
                            error = null
                        },
                        label = { Text("Kod grupy, np. K7MQ-2XPA") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { message -> { Text(message) } },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        "W grupie: " + preview.memberNames.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (preview.alreadyMember) {
                        Text("Już należysz do tej grupy.", style = MaterialTheme.typography.bodyMedium)
                    } else if (preview.placeholders.isNotEmpty()) {
                        Text("Kim jesteś w tej grupie?", style = MaterialTheme.typography.titleSmall)
                        (preview.placeholders + ("" to "Nikim z listy (dołączam jako nowa osoba)")).forEach { (uid, name) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = claim == uid, onClick = { claim = uid }, role = Role.RadioButton),
                            ) {
                                RadioButton(selected = claim == uid, onClick = null)
                                Text(name, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        },
        confirmButton = {
            val needsChoice = preview != null && !preview.alreadyMember && preview.placeholders.isNotEmpty() && claim == null
            TextButton(
                enabled = !busy && raw.isNotBlank() && !needsChoice,
                onClick = {
                    busy = true
                    error = null
                    val current = found
                    if (current == null) {
                        onPreview(raw, { code, result ->
                            found = code to result
                            busy = false
                        }, fail)
                    } else {
                        onJoin(current.first, claim?.takeIf { it.isNotEmpty() }, fail)
                    }
                },
            ) { Text(if (preview == null) "Dalej" else if (preview.alreadyMember) "Otwórz" else "Dołącz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
