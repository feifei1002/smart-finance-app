package com.smart_finance_app.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smart_finance_app.AppErrorMessage
import com.smart_finance_app.AppPageHeader
import com.smart_finance_app.AppScreenContainer
import com.smart_finance_app.AppStrings
import com.smart_finance_app.LocaleController
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource
import kotlinx.coroutines.launch

@Composable
internal fun FeedbackScreen(
    authToken: String,
    supportApi: SupportApi,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var rating by remember { mutableStateOf<Int?>(null) }
    var category by remember { mutableStateOf<FeedbackCategory?>(null) }
    var message by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }

    // Same rule as the server: a rating OR some text is enough.
    val canSend = !isSending && (rating != null || message.isNotBlank())

    BoxWithConstraints(modifier = Modifier.fillMaxSize().imePadding()) {
        val compact = maxWidth < 700.dp

        AppScreenContainer(
            compact = compact,
            maxWidth = if (compact) 560.dp else 760.dp
        ) {
            AppPageHeader(
                title = appStringResource(StringKey.FEEDBACK_TITLE),
                subtitle = if (sent) null else appStringResource(StringKey.FEEDBACK_SUBTITLE),
                onBack = onBack,
                compact = compact
            )

            if (sent) {
                SupportSentCard(
                    title = appStringResource(StringKey.FEEDBACK_SUCCESS_TITLE),
                    body = appStringResource(StringKey.FEEDBACK_SUCCESS_BODY),
                    onBack = onBack
                )
            } else {
                // ── Rating 1–5 ───────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(appStringResource(StringKey.FEEDBACK_RATING_LABEL))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (1..5).forEach { value ->
                            SelectablePill(
                                text = value.toString(),
                                selected = rating == value,
                                enabled = !isSending,
                                onClick = {
                                    // Tapping the selected number again clears it.
                                    rating = if (rating == value) null else value
                                    errorMessage = null
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HintText(appStringResource(StringKey.FEEDBACK_RATING_LOW))
                        HintText(appStringResource(StringKey.FEEDBACK_RATING_HIGH))
                    }
                }

                // ── Category ─────────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(appStringResource(StringKey.FEEDBACK_CATEGORY_LABEL))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FeedbackCategory.entries.forEach { option ->
                            SelectablePill(
                                text = appStringResource(option.labelKey()),
                                selected = category == option,
                                enabled = !isSending,
                                onClick = {
                                    category = if (category == option) null else option
                                    errorMessage = null
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ── Message ──────────────────────────────────────────────────
                OutlinedTextField(
                    value = message,
                    onValueChange = {
                        if (it.length <= SUPPORT_MESSAGE_MAX_LENGTH) {
                            message = it
                            errorMessage = null
                        }
                    },
                    label = { Text(appStringResource(StringKey.FEEDBACK_MESSAGE_LABEL)) },
                    enabled = !isSending,
                    minLines = 5,
                    maxLines = 10,
                    supportingText = {
                        Text(
                            text = "${message.length}/$SUPPORT_MESSAGE_MAX_LENGTH",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let { AppErrorMessage(it) }

                Button(
                    enabled = canSend,
                    onClick = {
                        scope.launch {
                            isSending = true
                            errorMessage = null
                            try {
                                when (val result = supportApi.sendFeedback(authToken, rating, category, message)) {
                                    SendSupportMessageResult.Success -> sent = true
                                    is SendSupportMessageResult.Failure -> errorMessage = AppStrings.get(
                                        LocaleController.currentLanguageCode,
                                        result.message
                                    )
                                }
                            } finally {
                                isSending = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isSending) appStringResource(StringKey.COMMON_SENDING)
                        else appStringResource(StringKey.FEEDBACK_SEND),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    enabled = !isSending,
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = appStringResource(StringKey.COMMON_CANCEL),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun FeedbackCategory.labelKey(): StringKey = when (this) {
    FeedbackCategory.Bug   -> StringKey.FEEDBACK_CATEGORY_BUG
    FeedbackCategory.Idea  -> StringKey.FEEDBACK_CATEGORY_IDEA
    FeedbackCategory.Other -> StringKey.FEEDBACK_CATEGORY_OTHER
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Tappable option styled like the Appearance buttons in Settings. */
@Composable
private fun SelectablePill(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            ),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}