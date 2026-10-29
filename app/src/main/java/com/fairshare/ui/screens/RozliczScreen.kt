package com.fairshare.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fairshare.ui.components.BackHeader
import com.fairshare.ui.components.EmptyHint
import com.fairshare.ui.components.LetterAvatar
import com.fairshare.ui.components.SectionCard
import com.fairshare.ui.theme.AvatarColors
import com.fairshare.ui.theme.FairShareTheme
import com.fairshare.ui.theme.Negative
import com.fairshare.ui.theme.Positive

private data class Settlement(
    val name: String,
    val avatar: AvatarColors,
    val amount: String,
    /** Group name or "2 grupy" */
    val context: String,
)

private val toPay = emptyList<Settlement>()
private val toReceive = emptyList<Settlement>()

@Composable
fun RozliczScreen(onBack: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        BackHeader("Rozlicz się", onBack)

        SectionCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryAmount("Oddajesz", "0,00 zł", Negative, Modifier.weight(1f))
                SummaryAmount("Dostaniesz", "0,00 zł", Positive, Modifier.weight(1f))
            }
        }

        SectionTitle("Ty oddajesz", Modifier.padding(top = 16.dp))
        if (toPay.isEmpty()) {
            EmptyHint("Nie masz nic do oddania.")
        } else {
            toPay.forEach { settlement ->
                SettlementRow(settlement, Negative) {
                    FilledTonalButton(onClick = {}, modifier = Modifier.height(40.dp)) { Text("Zapisz spłatę") }
                }
            }
        }

        SectionTitle("Oddają tobie", Modifier.padding(top = 8.dp))
        if (toReceive.isEmpty()) {
            EmptyHint("Nikt nie jest ci winien.")
        } else {
            toReceive.forEach { settlement ->
                SettlementRow(settlement, Positive) {
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.height(40.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    ) { Text("Przypomnij") }
                }
            }
        }

        Row(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Długi w grupach są uproszczone, więc przelewów jest jak najmniej.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryAmount(label: String, amount: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .height(36.dp)
            .padding(top = 8.dp),
    )
}

@Composable
private fun SettlementRow(
    settlement: Settlement,
    amountColor: Color,
    action: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LetterAvatar(settlement.name.first().toString(), settlement.avatar)
        Column(Modifier.weight(1f)) {
            Text(settlement.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = amountColor)) { append(settlement.amount) }
                    append(" · ")
                    append(settlement.context)
                },
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action()
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RozliczPreview() {
    FairShareTheme {
        Surface(color = MaterialTheme.colorScheme.background) { RozliczScreen() }
    }
}
