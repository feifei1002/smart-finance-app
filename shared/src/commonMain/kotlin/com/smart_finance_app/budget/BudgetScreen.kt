package com.smart_finance_app.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.smart_finance_app.AppStrings
import com.smart_finance_app.LocaleController
import com.smart_finance_app.dashboard.TransactionData
import com.smart_finance_app.dashboard.getCurrencySymbol
import com.smart_finance_app.transactions.TransactionCategories
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Clock
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.number
import org.jetbrains.compose.resources.vectorResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.close
import smart_finance_app.shared.generated.resources.delete
import smart_finance_app.shared.generated.resources.edit
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource
import com.smart_finance_app.localiseCategory

// ── Category colours (matches DashboardState) ─────────────────────────────────

val budgetCategories = TransactionCategories.all.filter { it != TransactionCategories.INCOME }

private val categoryColors = mapOf(
    TransactionCategories.FOOD_DINING to Color(0xFF22C55E),
    TransactionCategories.SHOPPING_PERSONAL to Color(0xFFEC4899),
    TransactionCategories.BILLS_HOUSING to Color(0xFF2563EB),
    TransactionCategories.ENTERTAINMENT_SUBSCRIPTIONS to Color(0xFFF97316),
    TransactionCategories.TRANSPORTATION to Color(0xFF06B6D4),
    TransactionCategories.TRANSFERS to Color(0xFF8B5CF6),
    TransactionCategories.OTHERS to Color(0xFFEF4444)
)

private fun Double.formatCurrency(): String {
    val totalCents = kotlin.math.round(this * 100).toLong()
    val whole = totalCents / 100
    val cents = abs(totalCents % 100)
    return "$whole.${cents.toString().padStart(2, '0')}"
}

// ── Computed budget with spending ─────────────────────────────────────────────

data class BudgetWithSpending(
    val budget: BudgetData,
    val spent: Double,
    val color: Color
)

fun computeBudgetsWithSpending(
    budgets: List<BudgetData>,
    transactions: List<TransactionData>
): List<BudgetWithSpending> {
    val now = Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.UTC)

    return budgets.map { budget ->
        val relevant = transactions.filter { tx ->
            if (tx.amount >= 0) return@filter false
            val dateOnly = tx.timestamp.split("T").firstOrNull() ?: tx.timestamp
            val parts = dateOnly.split("-")
            if (parts.size < 3) return@filter false

            val txYear  = parts[0].toIntOrNull() ?: return@filter false
            val txMonth = parts[1].toIntOrNull() ?: return@filter false
            val txDay   = parts[2].take(2).toIntOrNull() ?: return@filter false

            val periodNormalized = budget.period.trim().lowercase()

            val inPeriod = when (periodNormalized) {
                "monthly" -> txYear == now.year && txMonth == now.month.number
                "weekly"  -> {
                    val todayDayOfWeek = now.dayOfWeek.ordinal
                    val weekStart = now.date.minus(
                        kotlinx.datetime.DatePeriod(days = todayDayOfWeek)
                    )
                    val txDate = kotlinx.datetime.LocalDate(txYear, txMonth, txDay)
                    txDate >= weekStart && txDate <= now.date
                }
                else -> true
            }

            if (!inPeriod) return@filter false
            TransactionCategories.normalize(tx.category) == budget.category
        }

        BudgetWithSpending(
            budget = budget,
            spent  = relevant.sumOf { abs(it.amount) },
            color  = categoryColors[budget.category] ?: Color(0xFF94A3B8)
        )
    }
}

// ── Main Budget Screen ─────────────────────────────────────────────────────────

@Composable
fun BudgetScreen(
    authToken: String,
    transactions: List<TransactionData>,
    currency: String,
    api: BudgetApi
) {
    val scope  = rememberCoroutineScope()
    val symbol = getCurrencySymbol(currency)

    var budgets     by remember { mutableStateOf<List<BudgetData>>(emptyList()) }
    var isLoading   by remember { mutableStateOf(true) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }
    var showDialog  by remember { mutableStateOf(false) }
    var editBudget  by remember { mutableStateOf<BudgetData?>(null) }
    var dialogError by remember { mutableStateOf<String?>(null) }

    val budgetsWithSpending by derivedStateOf {
        computeBudgetsWithSpending(budgets, transactions)
    }

    suspend fun loadBudgets() {
        isLoading = true
        errorMsg  = null
        when (val result = api.getBudgets(authToken)) {
            is BudgetResult.Success -> budgets = result.data
            is BudgetResult.Failure -> errorMsg = AppStrings.get(
                LocaleController.currentLanguageCode,
                result.message
            )
        }
        isLoading = false
    }

    LaunchedEffect(authToken) { loadBudgets() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = appStringResource(StringKey.BUDGETS_TITLE),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            errorMsg != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            errorMsg ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { scope.launch { loadBudgets() } }) {
                            Text(appStringResource(StringKey.COMMON_RETRY))
                        }
                    }
                }
            }
            budgets.isEmpty() -> {
                EmptyBudgetCard(onAddClick = {
                    editBudget = null
                    dialogError = null
                    showDialog = true
                })
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(budgetsWithSpending) { item ->
                        BudgetCard(
                            item   = item,
                            symbol = symbol,
                            onEdit = {
                                editBudget = item.budget
                                dialogError = null
                                showDialog = true
                            },
                            onDelete = {
                                scope.launch {
                                    when (val res = api.deleteBudget(authToken, item.budget.id)) {
                                        is BudgetResult.Success -> {
                                            errorMsg = null
                                            loadBudgets()
                                        }
                                        is BudgetResult.Failure -> errorMsg = AppStrings.get(
                                            LocaleController.currentLanguageCode,
                                            res.message
                                        )
                                    }
                                }
                            }
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                editBudget = null
                                dialogError = null
                                showDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                appStringResource(StringKey.BUDGETS_ADD),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (showDialog) {
        val usedCategories = remember(budgets, editBudget) {
            budgets.filter { editBudget == null || it.id != editBudget?.id }.map { it.category }
        }

        AddBudgetDialog(
            existing = editBudget,
            symbol = symbol,
            usedCategories = usedCategories,
            serverError = dialogError,
            onDismiss = {
                showDialog = false
                editBudget = null
                dialogError = null
            },
            onConfirm = { category, amount, period ->
                val currentEditing = editBudget
                scope.launch {
                    val result = if (currentEditing != null) {
                        api.updateBudget(authToken, currentEditing.id, amount, category, period)
                    } else {
                        api.createBudget(authToken, BudgetRequest(category, amount, period))
                    }
                    when (result) {
                        is BudgetResult.Success -> {
                            showDialog = false
                            editBudget = null
                            dialogError = null
                            loadBudgets()
                        }
                        is BudgetResult.Failure -> dialogError = AppStrings.get(
                            LocaleController.currentLanguageCode,
                            result.message
                        )
                    }
                }
            }
        )
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyBudgetCard(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            "+",
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Light
                        )
                    }
                    Text(
                        text = appStringResource(StringKey.BUDGETS_EMPTY_TITLE),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = appStringResource(StringKey.BUDGETS_EMPTY_SUBTITLE),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── Budget card ───────────────────────────────────────────────────────────────

@Composable
fun BudgetCard(
    item: BudgetWithSpending,
    symbol: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = (item.spent / item.budget.amount).toFloat().coerceIn(0f, 1f)
    val isOverBudget = item.spent > item.budget.amount
    val isWarning = progress >= 0.8f && !isOverBudget
    val barColor = when {
        isOverBudget -> Color(0xFFDC2626)
        isWarning    -> Color(0xFFF59E0B)
        else         -> item.color
    }
    val remaining = item.budget.amount - item.spent

    // Read strings outside the string interpolations below
    val spentLabel     = appStringResource(StringKey.BUDGETS_SPENT)
    val ofLabel        = appStringResource(StringKey.BUDGETS_OF)
    val remainingLabel = appStringResource(StringKey.BUDGETS_REMAINING)
    val overByLabel    = appStringResource(StringKey.BUDGETS_OVER_BY)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(10.dp).background(item.color, CircleShape))
                    Text(localiseCategory(item.budget.category),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = item.budget.period.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Row {
                    TextButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            appStringResource(StringKey.BUDGETS_DIALOG_SAVE),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            "Delete",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$symbol${item.spent.formatCurrency()} $spentLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOverBudget) Color(0xFFDC2626)
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isOverBudget) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "$ofLabel $symbol${item.budget.amount.formatCurrency()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = barColor,
                trackColor = barColor.copy(alpha = 0.2f)
            )

            if (isOverBudget) {
                Text(
                    text = "$overByLabel $symbol${(item.spent - item.budget.amount).formatCurrency()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = "$symbol${remaining.formatCurrency()} $remainingLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isWarning) Color(0xFFF59E0B)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Compact budget progress row (used in dashboard) ───────────────────────────

@Composable
fun CompactBudgetProgressRow(
    item: BudgetWithSpending,
    symbol: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = (item.spent / item.budget.amount).toFloat().coerceIn(0f, 1f)
    val isOver = item.spent > item.budget.amount

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.size(8.dp).background(item.color, CircleShape))
                Text(
                    text = localiseCategory(item.budget.category),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "$symbol${item.spent.formatCurrency()} / $symbol${item.budget.amount.formatCurrency()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOver) Color(0xFFDC2626)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.edit),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.delete),
                        contentDescription = null,
                        tint = Color(0xFFDC2626)
                    )
                }
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (isOver) Color(0xFFDC2626) else item.color,
            trackColor = item.color.copy(alpha = 0.2f)
        )
    }
}

// ── Add / Edit budget dialog ──────────────────────────────────────────────────

@Composable
fun AddBudgetDialog(
    existing: BudgetData?,
    symbol: String,
    usedCategories: List<String>,
    serverError: String?,
    onDismiss: () -> Unit,
    onConfirm: (category: String, amount: Double, period: String) -> Unit
) {
    val isEdit = existing != null

    val defaultCategory = remember(usedCategories, existing) {
        existing?.category
            ?: (budgetCategories.firstOrNull { it !in usedCategories } ?: budgetCategories.first())
    }

    var selectedCategory by remember { mutableStateOf(defaultCategory) }
    var amountText by remember { mutableStateOf(existing?.amount?.formatCurrency() ?: "") }
    var selectedPeriod by remember { mutableStateOf(existing?.period ?: "monthly") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf<String?>(null) }

    val amountErrorMsg   = appStringResource(StringKey.BUDGETS_DIALOG_AMOUNT_ERROR)
    val categorySetLabel = appStringResource(StringKey.BUDGETS_DIALOG_CATEGORY_SET)
    val monthlyLabel     = appStringResource(StringKey.BUDGETS_DIALOG_PERIOD_MONTHLY)
    val weeklyLabel      = appStringResource(StringKey.BUDGETS_DIALOG_PERIOD_WEEKLY)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEdit) appStringResource(StringKey.BUDGETS_DIALOG_EDIT_TITLE)
                        else appStringResource(StringKey.BUDGETS_DIALOG_ADD_TITLE),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.close),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (serverError != null) {
                    Text(
                        text = serverError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Category selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        appStringResource(StringKey.BUDGETS_DIALOG_CATEGORY),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box {
                        OutlinedButton(
                            onClick = { if (!isEdit) categoryExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isEdit
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        Modifier.size(10.dp).background(
                                            categoryColors[selectedCategory] ?: Color.Gray,
                                            CircleShape
                                        )
                                    )
                                    Text(localiseCategory(selectedCategory))
                                }
                                Text("▾", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            budgetCategories.forEach { cat ->
                                val alreadyUsed = cat in usedCategories && cat != existing?.category
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                Modifier.size(10.dp).background(
                                                    categoryColors[cat] ?: Color.Gray,
                                                    CircleShape
                                                )
                                            )
                                            Text(
                                                localiseCategory(cat),
                                                color = if (alreadyUsed)
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                else
                                                    MaterialTheme.colorScheme.onSurface
                                            )
                                            if (alreadyUsed) {
                                                Text(
                                                    categorySetLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    enabled = !alreadyUsed,
                                    onClick = {
                                        selectedCategory = cat
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Period selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        appStringResource(StringKey.BUDGETS_DIALOG_PERIOD),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Map internal keys to display labels
                        listOf(
                            "monthly" to monthlyLabel,
                            "weekly"  to weeklyLabel
                        ).forEach { (period, label) ->
                            val selected = selectedPeriod == period
                            OutlinedButton(
                                onClick = { selectedPeriod = period },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Amount text field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        appStringResource(StringKey.BUDGETS_DIALOG_LIMIT),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            amountError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        prefix = { Text(symbol) },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = amountError != null,
                        supportingText = amountError?.let { { Text(it) } },
                        singleLine = true
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(appStringResource(StringKey.BUDGETS_DIALOG_CANCEL))
                    }
                    Button(onClick = {
                        val cleanedAmountText = amountText.trim().replace(",", ".")
                        val amount = cleanedAmountText.toDoubleOrNull()
                        if (amount == null || amount <= 0) {
                            amountError = amountErrorMsg
                            return@Button
                        }
                        onConfirm(selectedCategory, amount, selectedPeriod)
                    }) {
                        Text(
                            if (isEdit) appStringResource(StringKey.BUDGETS_DIALOG_SAVE)
                            else appStringResource(StringKey.BUDGETS_DIALOG_ADD_BUTTON)
                        )
                    }
                }
            }
        }
    }
}