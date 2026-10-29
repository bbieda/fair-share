package com.fairshare.ui.sample

import androidx.compose.ui.graphics.Color
import com.fairshare.ui.theme.ChartBlue
import com.fairshare.ui.theme.ChartGray
import com.fairshare.ui.theme.ChartOrange
import com.fairshare.ui.theme.ChartPink
import com.fairshare.ui.theme.ChartPurple
import com.fairshare.ui.theme.ChartTeal

// Static placeholder data so the screens have something to show.

data class CategoryShare(
    val name: String,
    val shortName: String,
    val percent: Int,
    val color: Color,
)

val sampleCategories = listOf(
    CategoryShare("Jedzenie na mieście", "Na mieście", 31, ChartTeal),
    CategoryShare("Zakupy spożywcze", "Spożywcze", 26, ChartBlue),
    CategoryShare("Mieszkanie", "Mieszkanie", 22, ChartPink),
    CategoryShare("Transport", "Transport", 10, ChartOrange),
    CategoryShare("Rozrywka", "Rozrywka", 8, ChartPurple),
    CategoryShare("Inne", "Inne", 4, ChartGray),
)
