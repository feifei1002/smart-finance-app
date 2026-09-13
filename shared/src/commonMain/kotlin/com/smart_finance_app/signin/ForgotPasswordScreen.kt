package com.smart_finance_app.signin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.arrow_back
import smart_finance_app.shared.generated.resources.lock
import smart_finance_app.shared.generated.resources.mail
import smart_finance_app.shared.generated.resources.verified_user
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource

@Composable
fun ForgotPasswordScreen(
    isLoading: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onSubmit: (String) -> Unit,
    onBackToSignIn: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val emailTrimmed = email.trim()
    val validEmail   = emailTrimmed.contains("@") && emailTrimmed.contains(".")
    val canSubmit    = emailTrimmed.isNotBlank() && !isLoading

    // Resolve outside submit() so it's accessible in a non-composable lambda
    val invalidEmailMsg = appStringResource(StringKey.FORGOT_PASSWORD_INVALID_EMAIL)

    fun submit() {
        if (!validEmail) {
            validationError = invalidEmailMsg
            return
        }
        validationError = null
        onSubmit(emailTrimmed)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 24.dp else 40.dp),
            contentAlignment = Alignment.Center
        ) {
            if (compact) {
                ResetPasswordContent(
                    email = email,
                    onEmailChange = { email = it; validationError = null },
                    isLoading = isLoading,
                    canSubmit = canSubmit,
                    validationError = validationError,
                    errorMessage = errorMessage,
                    successMessage = successMessage,
                    onSubmit = { submit() },
                    onBackToSignIn = onBackToSignIn,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Surface(
                    modifier = Modifier.widthIn(max = 560.dp).heightIn(min = 860.dp),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp
                ) {
                    ResetPasswordContent(
                        email = email,
                        onEmailChange = { email = it; validationError = null },
                        isLoading = isLoading,
                        canSubmit = canSubmit,
                        validationError = validationError,
                        errorMessage = errorMessage,
                        successMessage = successMessage,
                        onSubmit = { submit() },
                        onBackToSignIn = onBackToSignIn,
                        modifier = Modifier.padding(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResetPasswordContent(
    email: String,
    onEmailChange: (String) -> Unit,
    isLoading: Boolean,
    canSubmit: Boolean,
    validationError: String?,
    errorMessage: String?,
    successMessage: String?,
    onSubmit: () -> Unit,
    onBackToSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(
                onClick = onBackToSignIn,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.arrow_back),
                    contentDescription = appStringResource(StringKey.COMMON_BACK)
                )
            }
        }

        Surface(
            modifier = Modifier.size(76.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(Res.drawable.lock),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = appStringResource(StringKey.FORGOT_PASSWORD_TITLE),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = appStringResource(StringKey.FORGOT_PASSWORD_SUBTITLE),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = appStringResource(StringKey.FORGOT_PASSWORD_EMAIL_LABEL),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        painter = painterResource(Res.drawable.mail),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                placeholder = { Text(appStringResource(StringKey.FORGOT_PASSWORD_EMAIL_PLACEHOLDER)) },
                isError = validationError != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() })
            )
            validationError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        successMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        Button(
            enabled = canSubmit,
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(appStringResource(StringKey.FORGOT_PASSWORD_SEND_BUTTON))
            }
        }

        TextButton(onClick = onBackToSignIn) {
            Text(appStringResource(StringKey.FORGOT_PASSWORD_BACK))
        }

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(Res.drawable.verified_user),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Column {
                Text(
                    text = appStringResource(StringKey.FORGOT_PASSWORD_SECURITY_TITLE),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = appStringResource(StringKey.FORGOT_PASSWORD_SECURITY_SUBTITLE),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}