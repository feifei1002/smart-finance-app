package com.smart_finance_app.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChartsBottomSheet(
    chartCardsOnDashboard: Set<String>,
    deletedCards: Set<String>,
    onAddChart: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val available =
        RESTORABLE_BUILT_IN_CARDS.filter { it.key in deletedCards } +
                ALL_CHART_CARDS.filter { it.key !in chartCardsOnDashboard }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = appStringResource(StringKey.DASHBOARD_ADD_CHARTS),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    MinusIcon(modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (available.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center) {
                    Text(text = appStringResource(StringKey.DASHBOARD_ALL_CHARTS_ADDED),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    available.forEach { def ->
                        ChartOptionRow(def = def, onAdd = { onAddChart(def.key) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChartOptionRow(
    def: ChartCardDef,
    onAdd: () -> Unit
) {
    val title = appStringResource(def.title)
    val description = appStringResource(def.description)
    val sizeLabel = if (def.size == CardSize.FULL) {
        appStringResource(StringKey.DASHBOARD_CHART_SIZE_FULL)
    } else {
        appStringResource(StringKey.DASHBOARD_CHART_SIZE_HALF)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        BoxWithConstraints(
            modifier = Modifier.padding(14.dp)
        ) {
            val veryNarrow = maxWidth < 360.dp

            if (veryNarrow) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChartOptionTextBlock(
                        title = title,
                        description = description,
                        sizeLabel = sizeLabel,
                        size = def.size,
                        modifier = Modifier.fillMaxWidth()
                    )

                    FilledTonalButton(
                        onClick = onAdd,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = appStringResource(StringKey.DASHBOARD_ADD_BUTTON),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChartOptionTextBlock(
                        title = title,
                        description = description,
                        sizeLabel = sizeLabel,
                        size = def.size,
                        modifier = Modifier.weight(1f)
                    )

                    FilledTonalButton(
                        onClick = onAdd,
                        modifier = Modifier.heightIn(min = 44.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = appStringResource(StringKey.DASHBOARD_ADD_BUTTON),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChartOptionTextBlock(
    title: String,
    description: String,
    sizeLabel: String,
    size: CardSize,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (size == CardSize.FULL) {
                Color(0xFF6366F1).copy(alpha = 0.15f)
            } else {
                Color(0xFF22C55E).copy(alpha = 0.15f)
            }
        ) {
            Text(
                text = sizeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (size == CardSize.FULL) Color(0xFF6366F1) else Color(0xFF16A34A),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}