package com.smart_finance_app.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smart_finance_app.AppStrings
import com.smart_finance_app.LocaleController
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource
import com.smart_finance_app.budget.AddBudgetDialog
import com.smart_finance_app.budget.BudgetApi
import com.smart_finance_app.budget.BudgetData
import com.smart_finance_app.budget.BudgetRequest
import com.smart_finance_app.budget.BudgetResult
import com.smart_finance_app.budget.CompactBudgetProgressRow
import com.smart_finance_app.budget.computeBudgetsWithSpending
import com.smart_finance_app.currency.CurrencyController
import com.smart_finance_app.currency.getCurrencySymbol
import com.smart_finance_app.localiseCategory
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.add
import smart_finance_app.shared.generated.resources.arrow_downward
import smart_finance_app.shared.generated.resources.arrow_upward
import smart_finance_app.shared.generated.resources.calendar_month
import smart_finance_app.shared.generated.resources.check
import smart_finance_app.shared.generated.resources.moving
import kotlin.math.abs
import kotlin.time.Clock


@Composable
internal fun rememberGreeting(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = now.hour
    return when {
        hour < 12 -> appStringResource(StringKey.DASHBOARD_GREETING_MORNING)
        hour < 18 -> appStringResource(StringKey.DASHBOARD_GREETING_AFTERNOON)
        else      -> appStringResource(StringKey.DASHBOARD_GREETING_NIGHT)
    }
}

// ── Futuristic merged balance/income/expense card ─────────────────────────────

@Composable
internal fun FinancialOverviewCard(
    balance: String,
    balanceTrend: Float,
    income: String,
    incomeTrend: Float,
    expenses: String,
    expensesTrend: Float
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val glowColor   = accentColor.copy(alpha = 0.18f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // Outer glow border using Canvas
        Canvas(modifier = Modifier.matchParentSize()) {
            val cornerR = 16.dp.toPx()
            val strokeW = 2.5.dp.toPx()
            // Outer glow layer
            drawRoundRect(
                color        = glowColor,
                size         = size,
                cornerRadius = CornerRadius(cornerR + 4.dp.toPx()),
                style        = Stroke(width = 8.dp.toPx())
            )
            // Crisp accent border
            drawRoundRect(
                color        = accentColor.copy(alpha = 0.7f),
                size         = size,
                cornerRadius = CornerRadius(cornerR),
                style        = Stroke(width = strokeW)
            )
        }

        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Current Balance (larger)
                Column(
                    modifier = Modifier.weight(1.5f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = appStringResource(StringKey.DASHBOARD_CURRENT_BALANCE),
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text  = balance,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
//                    TrendIndicator(percentageChange = balanceTrend)
                }

                // Vertical divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(72.dp)
                        .background(accentColor.copy(alpha = 0.25f))
                )

                // Right: Income + Expenses stacked
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Income
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF16A34A), CircleShape)
                            )
                            Text(text = appStringResource(StringKey.DASHBOARD_INCOME),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text  = income,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
//                        TrendIndicator(percentageChange = incomeTrend)
                    }

                    HorizontalDivider(color = accentColor.copy(alpha = 0.12f))

                    // Expenses
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFFEF4444), CircleShape)
                            )
                            Text(text = appStringResource(StringKey.DASHBOARD_EXPENSES),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text  = expenses,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
//                        TrendIndicator(percentageChange = expensesTrend)
                    }
                }
            }
        }
    }
}

// ── Customizable card wrapper (delete + move overlays) ────────────────────────

@Composable
internal fun CustomizableCard(
    cardKey: String,
    isCustomizing: Boolean,
    onDelete: (String) -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onDragStarted: () -> Unit = {},
    onDragEnded: () -> Unit = {},
    onMoveHorizontally: (Float) -> Unit = {},
    horizontalPosition: Float? = null,
    onOpenPreview: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var dragAccumY by remember { mutableFloatStateOf(0f) }
    var dragAccumX by remember { mutableFloatStateOf(0f) }
    // A smaller threshold makes a complete, full-width card practical to move
    // within a single long-press drag.
    val swapThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        val trackOffset = if (horizontalPosition == null) 0.dp
        else (maxWidth + 12.dp) * horizontalPosition.coerceIn(0f, 1f)
        Box(modifier = Modifier.fillMaxSize().offset(x = trackOffset)) {
            DashboardCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .pointerInput(cardKey, isCustomizing) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (!isCustomizing) {
                                    onOpenPreview(cardKey)
                                }
                            }
                        )
                    }
                    .then(if (isCustomizing) Modifier.blur(3.dp) else Modifier),
                content = content
            )

            if (isCustomizing) {
                // ── Delete button: top-right red minus-in-circle ──
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-10).dp)
                        .size(24.dp)
                        .background(Color(0xFFEF4444), CircleShape)
                        .clickable { onDelete(cardKey) },
                    contentAlignment = Alignment.Center
                ) {
                    MinusIcon(modifier = Modifier.size(12.dp), color = Color.White)
                }

                // ── Move handle: centred icon, drag to reorder ──
                // Uses raw awaitPointerEventScope so the LazyColumn's nested-scroll
                // handler never intercepts the drag — works for both mouse and touch.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), CircleShape)
                        .pointerInput("move_handle_$isCustomizing") {
                            if (!isCustomizing) return@pointerInput
                            awaitEachGesture {

                                var pressed = false
                                while (!pressed) {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                    val change = event.changes.firstOrNull() ?: continue
                                    if (change.pressed) {
                                        change.consume()
                                        pressed = true
                                        dragAccumY = 0f
                                        dragAccumX = 0f
                                        onDragStarted()   // lock LazyColumn scroll
                                    }
                                }
                                var dragging = true
                                while (dragging) {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                    val change = event.changes.firstOrNull() ?: break
                                    if (change.pressed) {
                                        val delta = change.position - change.previousPosition
                                        change.consume()
                                        dragAccumX += delta.x
                                        dragAccumY += delta.y
                                        when {
                                            dragAccumY > swapThresholdPx -> {
                                                onMoveDown()
                                                dragAccumY = 0f; dragAccumX = 0f
                                            }
                                            dragAccumY < -swapThresholdPx -> {
                                                onMoveUp()
                                                dragAccumY = 0f; dragAccumX = 0f
                                            }
                                            abs(dragAccumX) > abs(dragAccumY) ->
                                                onMoveHorizontally(delta.x / 300f)
                                        }
                                    } else {
                                        change.consume()
                                        dragAccumY = 0f; dragAccumX = 0f
                                        dragging = false
                                        onDragEnded()   // unlock LazyColumn scroll
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    MoveIcon(modifier = Modifier.size(50.dp), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// ── Four-arrow move icon drawn with Canvas ────────────────────────────────────
@Composable
private fun MoveIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Icon(
        painter = painterResource(Res.drawable.moving),
        contentDescription = "Move",
        tint = Color.Unspecified,
        modifier = modifier
    )
}

@Composable
internal fun MinusIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier) {
        drawLine(
            color = color,
            start = Offset(size.width * 0.2f, size.height * 0.5f),
            end = Offset(size.width * 0.8f, size.height * 0.5f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun DashboardCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
internal fun BudgetProgressCardContent(
    authToken: String,
    transactions: List<TransactionData>,
    currency: String,
    exchangeRates: Map<String, Double>,
    api: BudgetApi
) {
    val scope  = rememberCoroutineScope()
    val symbol = getCurrencySymbol(currency)

    var budgets    by remember { mutableStateOf<List<BudgetData>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var editBudget by remember { mutableStateOf<BudgetData?>(null) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }

    val budgetsWithSpending by derivedStateOf {
        computeBudgetsWithSpending(
            budgets         = budgets,
            transactions    = transactions,
            displayCurrency = CurrencyController.currentCurrency,
            rates           = exchangeRates
        )
    }

    suspend fun loadBudgets() {
        when (val r = api.getBudgets(authToken)) {
            is BudgetResult.Success -> budgets = r.data
            is BudgetResult.Failure -> {errorMsg = AppStrings.get(
                LocaleController.currentLanguageCode,
                r.message
            )}
        }
    }

    LaunchedEffect(authToken, transactions) {
        loadBudgets()
    }

    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle(appStringResource(StringKey.DASHBOARD_BUDGET_PROGRESS))
        if (errorMsg != null) {
            Text(
                text = errorMsg ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFDC2626)
            )
        }
        if (budgets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            editBudget = null
                            errorMsg = null
                            showDialog = true
                        },
                        modifier = Modifier.size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Text(
                            "+", fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Light
                        )
                    }
                    Text(appStringResource(StringKey.BUDGETS_EMPTY_TITLE),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                budgetsWithSpending.forEach { item ->
                    CompactBudgetProgressRow(
                        item = item,
                        symbol = symbol,
                        onEdit = {
                            editBudget = item.budget
                            errorMsg = null
                            showDialog = true
                        },
                        onDelete = {
                            scope.launch {
                                when (val res = api.deleteBudget(authToken, item.budget.id)) {
                                    is BudgetResult.Success -> {
                                        errorMsg = null
                                        loadBudgets()
                                    }

                                    is BudgetResult.Failure -> {
                                        errorMsg = AppStrings.get(
                                            LocaleController.currentLanguageCode,
                                            res.message
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    editBudget = null
                    errorMsg = null
                    showDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = appStringResource(StringKey.BUDGETS_ADD),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showDialog) {
        val currentEdit = editBudget
        AddBudgetDialog(
            existing       = currentEdit,
            symbol         = symbol,
            usedCategories = budgets.filter { currentEdit == null || it.id != currentEdit.id }
                .map { it.category },
            serverError    = errorMsg,
            onDismiss      = {
                showDialog = false
                editBudget = null
                errorMsg = null
            },
            onConfirm      = { category, amount, period, currency ->
                scope.launch {
                    val result = if (currentEdit != null) {
                        api.updateBudget(authToken, currentEdit.id, amount, category, period, currency)
                    } else {
                        api.createBudget(authToken, BudgetRequest(category, amount, period, currency))
                    }
                    when (result) {
                        is BudgetResult.Success -> {
                            showDialog = false
                            editBudget = null
                            errorMsg = null
                            loadBudgets()
                        }
                        is BudgetResult.Failure -> {
                            errorMsg = AppStrings.get(
                                LocaleController.currentLanguageCode,
                                result.message
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
internal fun SpendingOverviewHeader(
    selectedPeriod: SpendingPeriod,
    onPeriodSelected: (SpendingPeriod) -> Unit
) {
    var periodDropdownExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val periodLabel = when (selectedPeriod) {
            SpendingPeriod.THIS_MONTH -> appStringResource(StringKey.PERIOD_THIS_MONTH)
            SpendingPeriod.LAST_MONTH -> appStringResource(StringKey.PERIOD_LAST_MONTH)
            SpendingPeriod.LAST_3_MONTHS -> appStringResource(StringKey.PERIOD_LAST_3_MONTHS)
            SpendingPeriod.THIS_YEAR -> appStringResource(StringKey.PERIOD_THIS_YEAR)
        }

        val spendingLabel = appStringResource(StringKey.DASHBOARD_SPENDING_PERIOD)
            .replace("%1\$s", periodLabel)

        SectionTitle(spendingLabel)

        Box {
            IconButton(
                onClick = { periodDropdownExpanded = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.calendar_month),
                    contentDescription = "Select period",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            DropdownMenu(
                expanded = periodDropdownExpanded,
                onDismissRequest = { periodDropdownExpanded = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                SpendingPeriod.entries.forEach { period ->
                    val itemLabel = when (period) {
                        SpendingPeriod.THIS_MONTH -> appStringResource(StringKey.PERIOD_THIS_MONTH)
                        SpendingPeriod.LAST_MONTH -> appStringResource(StringKey.PERIOD_LAST_MONTH)
                        SpendingPeriod.LAST_3_MONTHS -> appStringResource(StringKey.PERIOD_LAST_3_MONTHS)
                        SpendingPeriod.THIS_YEAR -> appStringResource(StringKey.PERIOD_THIS_YEAR)
                    }

                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (period == selectedPeriod) {
                                    Icon(
                                        painter = painterResource(Res.drawable.check),
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Spacer(Modifier.width(16.dp))
                                }

                                Text(
                                    text = itemLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        onClick = {
                            onPeriodSelected(period)
                            periodDropdownExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun CategoryLegendRow(cat: SpendingCategory) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(cat.color, CircleShape)
        )

        Text(
            text = localiseCategory(cat.name),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = cat.amount,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )

        Text(
            text = formatPercentLabel(cat.percent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

internal fun formatPercentLabel(percent: Float): String {
    return when {
        percent <= 0f -> "0%"
        percent < 0.01f -> "< 1%"
        else -> "${(percent * 100).toInt()}%"
    }
}

@Composable
internal fun TransactionRow(tx: Transaction) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tx.date, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(tx.amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = if (tx.isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun TrendIndicator(percentageChange: Float) {
    val isPositive = percentageChange >= 0f
    val absValue = abs(percentageChange)
    val color = if (isPositive) Color(0xFF16A34A) else Color(0xFFEF4444)
    val icon = if (isPositive) Res.drawable.arrow_upward else Res.drawable.arrow_downward

    // KMP-safe rounding to 1 decimal place without JVM String.format()
    val formattedPercentage = remember(absValue) {
        val intPart = absValue.toLong()
        val decPart = kotlin.math.round((absValue - intPart) * 10).toLong()
        "$intPart.$decPart"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "$formattedPercentage%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(text = appStringResource(StringKey.DASHBOARD_VS_LAST_MONTH),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Half-size built-in card content ──────────────────────────────────────────

@Composable
internal fun HalfCardContent(
    cardKey: String,
    state: DashboardState,
    showTitle: Boolean = true
) {
    when (cardKey) {
        "trend" -> {
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showTitle) {
                    SectionTitle(appStringResource(StringKey.DASHBOARD_MONTHLY_TREND))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendDot(color = Color(0xFF16A34A), label = "In")
                    LegendDot(color = Color(0xFFEF4444), label = "Out")
                }
                LineChart(data = state.monthlyTrend, modifier = Modifier.fillMaxWidth().weight(1f))
            }
        }
        "top_categories" -> {
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showTitle) {
                    SectionTitle(appStringResource(StringKey.DASHBOARD_HIGHEST_SPENDING))
                }
                HighestSpendingBarChart(
                    data = state.monthlyTopCategories,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun DashboardChartsButton(
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Icon(
            painter = painterResource(Res.drawable.add),
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = appStringResource(StringKey.DASHBOARD_CHARTS_BUTTON),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun DashboardCustomizeButton(
    isCustomizing: Boolean,
    onClick: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    if (isCustomizing) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Done button — sits where Customise was
            Button(
                onClick = onClick,
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.check),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = appStringResource(StringKey.DASHBOARD_DONE),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Cancel button — appears to the right of Done, red background
            if (onCancel != null) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = appStringResource(StringKey.SETTINGS_CANCEL),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(text = appStringResource(StringKey.DASHBOARD_CUSTOMISE),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}