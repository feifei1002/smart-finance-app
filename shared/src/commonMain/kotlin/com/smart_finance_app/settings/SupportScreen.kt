package com.smart_finance_app.settings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
internal fun SupportScreen(
    authToken: String,
    userEmail: String,
    supportApi: SupportApi,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var message by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }

    val canSend = !isSending && message.isNotBlank()

    BoxWithConstraints(modifier = Modifier.fillMaxSize().imePadding()) {
        val compact = maxWidth < 700.dp

        AppScreenContainer(
            compact = compact,
            maxWidth = if (compact) 560.dp else 760.dp
        ) {
            AppPageHeader(
                title = appStringResource(StringKey.SUPPORT_TITLE),
                subtitle = if (sent) null else appStringResource(StringKey.SUPPORT_SUBTITLE),
                onBack = onBack,
                compact = compact
            )

            if (sent) {
                SupportSentCard(
                    title = appStringResource(StringKey.SUPPORT_SUCCESS_TITLE),
                    body = appStringResource(StringKey.SUPPORT_SUCCESS_BODY).withEmail(userEmail),
                    onBack = onBack
                )
            } else {
                OutlinedTextField(
                    value = message,
                    onValueChange = {
                        if (it.length <= SUPPORT_MESSAGE_MAX_LENGTH) {
                            message = it
                            errorMessage = null
                        }
                    },
                    label = { Text(appStringResource(StringKey.SUPPORT_MESSAGE_LABEL)) },
                    placeholder = { Text(appStringResource(StringKey.SUPPORT_MESSAGE_PLACEHOLDER)) },
                    enabled = !isSending,
                    minLines = 6,
                    maxLines = 12,
                    supportingText = {
                        Text(
                            text = "${message.length}/$SUPPORT_MESSAGE_MAX_LENGTH",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (userEmail.isNotBlank()) {
                    Text(
                        text = appStringResource(StringKey.SUPPORT_REPLY_NOTE).withEmail(userEmail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                errorMessage?.let { AppErrorMessage(it) }

                Button(
                    enabled = canSend,
                    onClick = {
                        scope.launch {
                            isSending = true
                            errorMessage = null
                            try {
                                when (val result = supportApi.sendSupport(authToken, message)) {
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
                        else appStringResource(StringKey.SUPPORT_SEND),
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

/** Confirmation shown after a feedback or support message is sent. Shared by both screens. */
@Composable
internal fun SupportSentCard(
    title: String,
    body: String,
    onBack: () -> Unit
) {
    SettingsCard {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = appStringResource(StringKey.COMMON_BACK_TO_SETTINGS),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Fills the "%1$s" placeholder used in the strings with the user's email. */
private fun String.withEmail(email: String): String = replace("%1\$s", email)