package com.fairshare.ui.sample

import androidx.compose.ui.graphics.Color

// Expense categories with their share of the month; empty until real data exists.

data class CategoryShare(
    val name: String,
    val shortName: String,
    val percent: Int,
    val color: Color,
)

val sampleCategories = emptyList<CategoryShare>()
