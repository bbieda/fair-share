package com.fairshare.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.fairshare.data.model.ExpenseCategory
import com.fairshare.ui.theme.AvatarColors
import com.fairshare.ui.theme.ChartBlue
import com.fairshare.ui.theme.ChartGray
import com.fairshare.ui.theme.ChartOrange
import com.fairshare.ui.theme.ChartPink
import com.fairshare.ui.theme.ChartPurple
import com.fairshare.ui.theme.ChartTeal
import java.time.LocalDate
import java.time.YearMonth

private val monthNames = listOf(
    "Styczeń", "Luty", "Marzec", "Kwiecień", "Maj", "Czerwiec",
    "Lipiec", "Sierpień", "Wrzesień", "Październik", "Listopad", "Grudzień",
)
private val monthShort = listOf("sty", "lut", "mar", "kwi", "maj", "cze", "lip", "sie", "wrz", "paź", "lis", "gru")

/** "Listopad", plus the year when it is not the current one: "Listopad 2025". */
fun monthTitle(month: YearMonth, today: LocalDate = LocalDate.now()): String {
    val name = monthNames[month.monthValue - 1]
    return if (month.year == today.year) name else "$name ${month.year}"
}

fun monthShortName(date: LocalDate): String = monthShort[date.monthValue - 1]

/** "Dziś", "Wczoraj" or "12 paź 2026". */
fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Dziś"
    today.minusDays(1) -> "Wczoraj"
    else -> "${date.dayOfMonth} ${monthShortName(date)} ${date.year}"
}

/** Polish plural: plural(5, "wydatek", "wydatki", "wydatków") -> "5 wydatków". */
fun plural(count: Int, one: String, few: String, many: String): String {
    val word = when {
        count == 1 -> one
        count % 10 in 2..4 && count % 100 !in 12..14 -> few
        else -> many
    }
    return "$count $word"
}

fun avatarColorsFor(id: Long): AvatarColors = AvatarColors.entries[(id % AvatarColors.entries.size).toInt()]

fun initialOf(name: String): String = name.trim().firstOrNull()?.uppercase() ?: "?"

val ExpenseCategory?.icon: ImageVector
    get() = when (this) {
        ExpenseCategory.FOOD -> Icons.Outlined.Restaurant
        ExpenseCategory.GROCERIES -> Icons.Outlined.ShoppingCart
        ExpenseCategory.TRANSPORT -> Icons.Outlined.DirectionsCar
        ExpenseCategory.HOME -> Icons.Outlined.Home
        ExpenseCategory.FUN -> Icons.Outlined.SportsEsports
        ExpenseCategory.OTHER, null -> Icons.Outlined.Receipt
    }

val ExpenseCategory?.chartColor: Color
    get() = when (this) {
        ExpenseCategory.FOOD -> ChartTeal
        ExpenseCategory.GROCERIES -> ChartBlue
        ExpenseCategory.TRANSPORT -> ChartOrange
        ExpenseCategory.HOME -> ChartPurple
        ExpenseCategory.FUN -> ChartPink
        ExpenseCategory.OTHER, null -> ChartGray
    }

val ExpenseCategory?.labelOrDefault: String get() = this?.label ?: "Bez kategorii"

fun categoryLabel(key: String?): String = ExpenseCategory.labelOf(key)

fun categoryIcon(key: String?): ImageVector = ExpenseCategory.fromKey(key).icon

fun categoryChartColor(key: String?): Color {
    val known = ExpenseCategory.fromKey(key)
    if (key.isNullOrBlank() || known != null) return known.chartColor
    val palette = listOf(ChartTeal, ChartBlue, ChartPink, ChartOrange, ChartPurple, ChartGray)
    return palette[(key.hashCode() and 0x7FFFFFFF) % palette.size]
}
