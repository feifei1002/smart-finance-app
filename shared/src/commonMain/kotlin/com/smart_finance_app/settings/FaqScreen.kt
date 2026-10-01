package com.smart_finance_app.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smart_finance_app.AppPageHeader
import com.smart_finance_app.AppScreenContainer
import com.smart_finance_app.LocaleController
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.chevron_right
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

@Composable
internal fun FaqScreen(onBack: () -> Unit) {
    val items = FaqContent.get(LocaleController.currentLanguageCode)

    // Only one question open at a time: store the index of the open one (null = all closed).
    var expandedIndex by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp

        AppScreenContainer(
            compact = compact,
            maxWidth = if (compact) 560.dp else 900.dp
        ) {
            AppPageHeader(
                title = appStringResource(StringKey.FAQ_TITLE),
                subtitle = appStringResource(StringKey.FAQ_SUBTITLE),
                onBack = onBack,
                compact = compact
            )

            SettingsCard {
                SettingsGroup {
                    items.forEachIndexed { index, item ->
                        FaqRow(
                            item = item,
                            expanded = expandedIndex == index,
                            onToggle = {
                                expandedIndex = if (expandedIndex == index) null else index
                            }
                        )
                        if (index != items.lastIndex) {
                            SettingsDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqRow(
    item: FaqItem,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "faqChevronRotation"
    )

    // The whole column is clickable, so tapping the question OR the answer toggles it.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.question,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                painter = painterResource(Res.drawable.chevron_right),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation)
            )
        }

        val emphasisColor = MaterialTheme.colorScheme.onSurface
        val answerText = remember(item.answer, emphasisColor) {
            item.answer.withBoldMarkers(emphasisColor)
        }

        AnimatedVisibility(visible = expanded) {
            Text(
                text = answerText,
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun String.withBoldMarkers(boldColor: Color): AnnotatedString = buildAnnotatedString {
    split("**").forEachIndexed { index, part ->
        if (index % 2 == 1) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) { append(part) }
        } else {
            append(part)
        }
    }
}