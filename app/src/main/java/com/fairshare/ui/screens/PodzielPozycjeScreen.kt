package com.fairshare.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fairshare.data.db.UserEntity
import com.fairshare.data.model.Money
import com.fairshare.ui.components.BackHeader
import com.fairshare.ui.components.LetterAvatar
import com.fairshare.ui.components.avatarColorsFor
import com.fairshare.ui.components.initialOf
import com.fairshare.ui.components.plural
import com.fairshare.ui.theme.AvatarColors
import com.fairshare.ui.theme.Positive
import com.fairshare.ui.viewmodel.AddExpenseViewModel
import com.fairshare.ui.viewmodel.ItemDraft

/** [editing] value for "add a new item" (item keys start at 1). */
private const val NEW_ITEM = 0L

/**
 * "Podziel pozycje": who took which receipt item. Opened from "Nowy wydatek" and working on the same
 * [AddExpenseViewModel], so nothing is saved here; the form saves the expense with its items.
 *
 * Every item starts shared by everyone. A tap on a person adds or removes them, a long press makes the item theirs
 * alone. An item is split equally between its people; the bar at the bottom shows what each person's items add up to.
 */
@Composable
fun PodzielPozycjeScreen(
    viewModel: AddExpenseViewModel,
    onBack: () -> Unit = {},
) {
    val vm = viewModel
    val split = vm.itemSplit
    // key of the item in the edit dialog, NEW_ITEM for a new one, null when the dialog is closed
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 196.dp),
        ) {
            BackHeader("Podziel pozycje", onBack) {
                IconButton(onClick = { editing = NEW_ITEM }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = "Dodaj pozycję")
                }
            }

            if (vm.members.isEmpty()) {
                Text(
                    "Najpierw wybierz grupę w formularzu wydatku.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                Text(
                    "Stuknij osobę, żeby dodać ją do pozycji albo zdjąć. Przytrzymaj, żeby pozycja była tylko jej.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            vm.itemsGap?.let { gap ->
                GapBanner(itemsSum = vm.itemsSum, receiptTotal = vm.itemsSum + gap, gap = gap, onFix = vm::addBalancingItem)
            }

            if (vm.items.isEmpty()) {
                Text(
                    "Nie ma jeszcze pozycji. Dodaj je przyciskiem + albo zeskanuj paragon w formularzu wydatku.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            vm.items.forEachIndexed { index, item ->
                key(item.key) {
                    ItemCard(
                        item = item,
                        members = vm.members,
                        shares = split.perItem.getOrNull(index).orEmpty(),
                        onToggle = { userId -> vm.toggleItemPerson(item.key, userId) },
                        onOnly = { userId -> vm.assignItemOnlyTo(item.key, userId) },
                        onEveryone = { vm.toggleItemEveryone(item.key) },
                        onEdit = { editing = item.key },
                    )
                }
            }
        }

        // ---- What each person's items add up to ----
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    vm.members.forEach { member ->
                        PersonTotal(member, split.perUser[member.id] ?: 0L)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    val unassigned = vm.unassignedItems
                    Text(
                        if (unassigned > 0) "Bez osoby: " + plural(unassigned, "pozycja", "pozycje", "pozycji")
                        else "Razem ${Money.format(vm.itemsSum)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (unassigned > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Gotowe", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    editing?.let { itemKey ->
        val item = vm.items.firstOrNull { it.key == itemKey }
        if (itemKey != NEW_ITEM && item == null) {
            editing = null
        } else {
            ItemDialog(
                item = item,
                onDismiss = { editing = null },
                onSave = { name, price, discount -> vm.saveItem(item?.key, name, price, discount) },
                onDelete = item?.let { existing ->
                    {
                        vm.removeItem(existing.key)
                        editing = null
                    }
                },
            )
        }
    }
}

/** One receipt item: name, price, the people of the group as toggles, and how it divides. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemCard(
    item: ItemDraft,
    members: List<UserEntity>,
    shares: Map<Long, Long>,
    onToggle: (Long) -> Unit,
    onOnly: (Long) -> Unit,
    onEveryone: () -> Unit,
    onEdit: () -> Unit,
) {
    val nobody = item.sharedBy.isEmpty()
    val everyone = members.isNotEmpty() && members.all { it.id in item.sharedBy }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        border = if (nobody) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Money.format(item.price),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.price < 0) Positive else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 12.dp),
                )
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Zmień pozycję ${item.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(end = 12.dp),
            ) {
                members.forEach { member ->
                    PersonChip(
                        name = if (member.isMe) "Ty" else member.name,
                        initial = initialOf(member.name),
                        colors = avatarColorsFor(member.id),
                        selected = member.id in item.sharedBy,
                        onClick = { onToggle(member.id) },
                        onLongClick = { onOnly(member.id) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        nobody -> "Nikt nie jest przypisany"
                        item.sharedBy.size == 1 -> "W całości: " +
                            (members.firstOrNull { it.id in item.sharedBy }?.let { if (it.isMe) "Ty" else it.name } ?: "1 osoba")
                        // the parts differ by a grosz when the price does not divide evenly
                        else -> "po ${Money.format(shares.values.maxOrNull() ?: 0L)} · " +
                            if (everyone) "wszyscy" else plural(item.sharedBy.size, "osoba", "osoby", "osób")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (nobody) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEveryone) {
                    Text(if (everyone) "Odznacz wszystkich" else "Wszyscy")
                }
            }
        }
    }
}

/**
 * A person on an item: filled in their colour with a tick when they share it. Tap toggles, long press makes the
 * item theirs alone.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonChip(
    name: String,
    initial: String,
    colors: AvatarColors,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) colors.container else Color.Transparent)
            .border(1.dp, if (selected) colors.container else MaterialTheme.colorScheme.outlineVariant, shape)
            .combinedClickable(
                role = Role.Checkbox,
                onLongClickLabel = "Tylko $name",
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics { stateDescription = if (selected) "przypisana" else "nieprzypisana" }
            .padding(start = 6.dp, end = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .background(
                    if (selected) colors.content.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    CircleShape,
                ),
        ) {
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.content, modifier = Modifier.size(16.dp))
            } else {
                Text(initial, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            color = if (selected) colors.content else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One person in the bottom bar: avatar, name, and the sum of their parts of the items. */
@Composable
private fun PersonTotal(member: UserEntity, total: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LetterAvatar(initialOf(member.name), avatarColorsFor(member.id), size = 32.dp)
        Column {
            Text(
                if (member.isMe) "Ty" else member.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                Money.format(total),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (total == 0L) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** The scanned items do not add up to the receipt's total: says by how much and offers an item for the difference. */
@Composable
private fun GapBanner(itemsSum: Long, receiptTotal: Long, gap: Long, onFix: () -> Unit) {
    val color = MaterialTheme.colorScheme.error
    Column(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 4.dp, top = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(
                "Pozycje dają ${Money.format(itemsSum)}, a paragon ${Money.format(receiptTotal)}. " +
                    "Popraw ceny albo dodaj pozycję na różnicę.",
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
        }
        TextButton(onClick = onFix) {
            Text(if (gap > 0) "Dodaj „Inne” ${Money.format(gap)}" else "Dodaj „Rabat” ${Money.format(gap)}")
        }
    }
}

/** Add an item ([item] null) or change its name and price; an existing one can be deleted. */
@Composable
private fun ItemDialog(
    item: ItemDraft?,
    onDismiss: () -> Unit,
    /** Returns an error to show, or null when saved. */
    onSave: (name: String, price: String, discount: Boolean) -> String?,
    onDelete: (() -> Unit)?,
) {
    var name by rememberSaveable { mutableStateOf(item?.name.orEmpty()) }
    var price by rememberSaveable { mutableStateOf(item?.let { Money.formatInput(it.price) }.orEmpty()) }
    var discount by rememberSaveable { mutableStateOf((item?.price ?: 0L) < 0) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Nowa pozycja" else "Zmień pozycję") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("Nazwa") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { input ->
                        price = input.filter { it.isDigit() || it == ',' || it == '.' }
                        error = null
                    },
                    label = { Text("Cena") },
                    suffix = { Text(Money.currencyCode) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = discount, onCheckedChange = { discount = it })
                    Text("To rabat (odejmuje się od sumy)", style = MaterialTheme.typography.bodyMedium)
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = onSave(name, price, discount)
                    if (error == null) onDismiss()
                },
            ) { Text("Zapisz") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        },
    )
}
