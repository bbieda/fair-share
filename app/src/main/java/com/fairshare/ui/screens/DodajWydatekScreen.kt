package com.fairshare.ui.screens

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fairshare.ui.components.AppFilterChip
import com.fairshare.ui.components.BackHeader
import com.fairshare.ui.components.ListRow
import com.fairshare.ui.theme.FairShareTheme

@Composable
fun DodajWydatekScreen(
    onClose: () -> Unit = {},
    onSave: () -> Unit = {},
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
        ) {
            BackHeader("Nowy wydatek", onClose, navIcon = Icons.Outlined.Close, navLabel = "Zamknij")

            FieldLabel("Z kim", Modifier.padding(top = 12.dp))
            AppFilterChip("Wybierz grupę", selected = false, onClick = {}, dropdown = true)

            FieldLabel("Kwota", Modifier.padding(top = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val amountStyle = TextStyle(
                    fontSize = 48.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                BasicTextField(
                    value = amount,
                    onValueChange = { input -> amount = input.filter { it.isDigit() || it == ',' || it == '.' } },
                    textStyle = amountStyle,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { field ->
                        if (amount.isEmpty()) {
                            Text("0,00", style = amountStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                        field()
                    },
                )
                Text("PLN", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.primary)

            TextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Opis") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            )

            Spacer(Modifier.height(16.dp))
            ListRow(Icons.Outlined.AccountBalanceWallet, "Ty", overline = "Płacił")
            ListRow(Icons.Outlined.Percent, "Równo", overline = "Podział")
            ListRow(Icons.AutoMirrored.Outlined.Label, "Wybierz kategorię", overline = "Kategoria")
            ListRow(Icons.Outlined.CalendarToday, "Dziś", overline = "Data")
            ListRow(
                Icons.Outlined.PhotoCamera,
                "Dodaj zdjęcie",
                overline = "Paragon",
                headlineColor = MaterialTheme.colorScheme.primary,
            )
        }

        Button(
            onClick = onSave,
            enabled = amount.isNotEmpty(),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Zapisz wydatek", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DodajWydatekPreview() {
    FairShareTheme {
        Surface(color = MaterialTheme.colorScheme.background) { DodajWydatekScreen() }
    }
}
