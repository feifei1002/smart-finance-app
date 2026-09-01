package com.smart_finance_app.transactions

object TransactionCategories {
    const val FOOD_DINING = "Food & Dining"
    const val SHOPPING_PERSONAL = "Shopping & Personal"
    const val BILLS_HOUSING = "Bills & Housing"
    const val ENTERTAINMENT_SUBSCRIPTIONS = "Entertainment & Subscriptions"
    const val TRANSPORTATION = "Transportation"

    const val TRANSFERS = "Transfers"

    const val INCOME = "Income"
    const val MISCELLANEOUS = "Miscellaneous"

    val all = listOf(
        FOOD_DINING,
        SHOPPING_PERSONAL,
        BILLS_HOUSING,
        ENTERTAINMENT_SUBSCRIPTIONS,
        TRANSPORTATION,
        TRANSFERS,
        INCOME,
        MISCELLANEOUS
    )

    fun normalize(category: String?): String {
        return category
            ?.takeIf { it in all }
            ?: MISCELLANEOUS
    }
}