package com.smart_finance_app.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smart_finance_app.localiseCategory

@Composable
internal fun DonutChart(categories: List<SpendingCategory>, modifier: Modifier = Modifier) {

    if (categories.isEmpty()) return

    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.18f
        val radius = (size.minDimension - strokeWidth) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        val total = categories
            .sumOf { it.percent.toDouble() }
            .toFloat()
            .takeIf { it > 0f }
            ?: return@Canvas

        var startAngle = -90f

        categories.forEach { cat ->
            val sweep = (cat.percent / total) * 360f

            drawArc(
                color = cat.color,
                startAngle = startAngle,
//                sweepAngle = sweep - 0.8f,
                sweepAngle = (sweep - 0.8f).coerceAtLeast(0f),
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )

            startAngle += sweep
        }
    }
}

@Composable
internal fun LineChart(data: List<MonthlyPoint>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) return
    val incomeColor   = Color(0xFF16A34A)
    val expensesColor = Color(0xFFEF4444)
//    val maxVal = data.maxOf { maxOf(it.income, it.expenses) } * 1.2f
    val maxVal = (data.maxOf { maxOf(it.income, it.expenses) } * 1.2f).coerceAtLeast(1f)
    val yLabels = (3 downTo 0).map { i -> (maxVal * i / 3).toInt() }

    // CRITICAL CHANGES: Tightened down the left column layout footprints
    // to physically draw the gridlines and trend lines closer to the left wall.
    val labelWidth = 16.dp // Drastically reduced from 32.dp to eliminate dead workspace
    val spacingGutter = 4.dp // Halved from 8.dp to snap the chart lines leftwards

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Y-axis labels column - tightly fits numbers, hugging the far left
            Column(
                modifier = Modifier
                    .width(labelWidth)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                yLabels.forEach { v ->
                    Text(
                        text = if (v >= 1000) "${v / 1000}k" else "$v",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.width(spacingGutter))

            // The main graphic canvas - now pulled way over to the left!
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val chartWidth = size.width
                val chartHeight = size.height

                val stepX = chartWidth / (data.size - 1).toFloat()
                fun xFor(i: Int) = i * stepX
                fun yFor(v: Float) = chartHeight * (1f - v.coerceIn(0f, maxVal) / maxVal)

                // Render matching grid lines
                repeat(4) { i ->
                    val y = chartHeight * (i / 3f)
                    drawLine(
                        color = Color(0x1F888888),
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Income trend line
                val incomePath = Path()
                data.forEachIndexed { i, p ->
                    if (i == 0) incomePath.moveTo(xFor(i), yFor(p.income))
                    else incomePath.lineTo(xFor(i), yFor(p.income))
                }
                drawPath(incomePath, incomeColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))

                // Expenses trend line
                val expPath = Path()
                data.forEachIndexed { i, p ->
                    if (i == 0) expPath.moveTo(xFor(i), yFor(p.expenses))
                    else expPath.lineTo(xFor(i), yFor(p.expenses))
                }
                drawPath(expPath, expensesColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))

                // Vector point circles matching line vertices
                data.forEachIndexed { i, p ->
                    drawCircle(incomeColor, 3.5.dp.toPx(), Offset(xFor(i), yFor(p.income)))
                    drawCircle(expensesColor, 3.5.dp.toPx(), Offset(xFor(i), yFor(p.expenses)))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Month items automatically tracking the new left-shifted line anchor bounds
        val monthNumberMap = mapOf(
            "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4,
            "May" to 5, "Jun" to 6, "Jul" to 7, "Aug" to 8,
            "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = labelWidth + spacingGutter), // Dynamically stays aligned with the canvas start point
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach {
                Text(
                    text = "${monthNumberMap[it.month] ?: it.month}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(IntrinsicSize.Min),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
internal fun BarChart(data: List<MonthlyTopCategory>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No spending data yet", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val barWidthDp = 32.dp
    val gapDp      = 12.dp
    val totalWidth = (barWidthDp + gapDp) * data.size
//    val maxVal     = data.maxOf { it.amount } * 1.2f
    val maxVal = (data.maxOf { it.amount } * 1.2f).coerceAtLeast(1f)
    val scrollState = rememberScrollState()

    Column {
        Row(modifier = Modifier.horizontalScroll(scrollState)) {
            Canvas(modifier = Modifier.width(totalWidth).height(120.dp)) {
                val paddingTop    = 20.dp.toPx()  // space so tallest bar doesn't touch title
                val paddingBottom = 8.dp.toPx()
                val chartHeight   = size.height - paddingTop - paddingBottom
                val bw  = barWidthDp.toPx()
                val gap = gapDp.toPx()
                data.forEachIndexed { i, point ->
                    val barHeight = (point.amount / maxVal) * chartHeight
                    val x = i * (bw + gap) + gap / 2f
                    val y = paddingTop + chartHeight - barHeight
                    drawRoundRect(
                        color      = point.color.copy(alpha = 0.85f),
                        topLeft    = Offset(x, y),
                        size       = Size(bw, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }
        }
        // Month + category labels
        Row(
            modifier = Modifier.horizontalScroll(scrollState).width(totalWidth),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            data.forEach { point ->
                Column(
                    modifier = Modifier.width(barWidthDp + gapDp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(point.month, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    Text(localiseCategory(point.category), style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = point.color,
                        textAlign = TextAlign.Center,
                        maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun HighestSpendingBarChart(data: List<MonthlyTopCategory>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "No spending data yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val barWidthDp = 32.dp
    val gapDp = 12.dp
    val slotWidthDp = barWidthDp + gapDp
    val totalWidth = slotWidthDp * data.size
    val maxVal = (data.maxOf { it.amount } * 1.2f).coerceAtLeast(1f)
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(scrollState)
        ) {
            Canvas(
                modifier = Modifier
                    .width(totalWidth)
                    .fillMaxHeight()
            ) {
                val paddingTop = 16.dp.toPx()
                val paddingBottom = 8.dp.toPx()
                val chartHeight = size.height - paddingTop - paddingBottom
                val bw = barWidthDp.toPx()
                val slot = slotWidthDp.toPx()

                data.forEachIndexed { i, point ->
                    if (point.amount <= 0f) return@forEachIndexed

                    val barHeight = (point.amount / maxVal) * chartHeight
                    val x = i * slot + gapDp.toPx() / 2f
                    val y = paddingTop + chartHeight - barHeight

                    drawRoundRect(
                        color = point.color.copy(alpha = 0.85f),
                        topLeft = Offset(x, y),
                        size = Size(bw, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .width(totalWidth)
                .height(34.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            data.forEach { point ->
                Column(
                    modifier = Modifier.width(slotWidthDp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = point.month,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = if (point.category.isBlank()) "" else localiseCategory(point.category),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                        color = point.color,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}