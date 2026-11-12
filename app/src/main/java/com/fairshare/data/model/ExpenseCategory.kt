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

        /** Enum name when [label] matches a built-in category, otherwise the trimmed custom label. */
        fun storedKey(label: String): String {
            val clean = label.trim().replace(Regex("\\s+"), " ")
            return entries.firstOrNull { it.label.equals(clean, ignoreCase = true) }?.name ?: clean
        }

        fun labelOf(key: String?): String =
            fromKey(key)?.label ?: key?.takeIf { it.isNotBlank() } ?: "Bez kategorii"
    }
}
