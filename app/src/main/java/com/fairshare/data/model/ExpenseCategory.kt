package com.fairshare.data.model

/** Expense categories. The enum name is what is stored in expenses.category, so don't rename entries. */
enum class ExpenseCategory(val label: String) {
    FOOD("Jedzenie"),
    GROCERIES("Zakupy"),
    TRANSPORT("Transport"),
    HOME("Mieszkanie"),
    FUN("Rozrywka"),
    OTHER("Inne");

    companion object {
        fun fromKey(key: String?): ExpenseCategory? = entries.firstOrNull { it.name == key }
    }
}
