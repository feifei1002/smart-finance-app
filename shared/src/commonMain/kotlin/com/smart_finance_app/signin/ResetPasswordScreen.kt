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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.lock
import smart_finance_app.shared.generated.resources.visibility
import smart_finance_app.shared.generated.resources.visibility_off
import smart_finance_app.shared.generated.resources.reset_password_title
import smart_finance_app.shared.generated.resources.reset_password_subtitle
import smart_finance_app.shared.generated.resources.reset_password_new_label
import smart_finance_app.shared.generated.resources.reset_password_confirm_label
import smart_finance_app.shared.generated.resources.reset_password_button
import smart_finance_app.shared.generated.resources.reset_password_back
import smart_finance_app.shared.generated.resources.reset_password_expired_title
import smart_finance_app.shared.generated.resources.reset_password_expired_description
import smart_finance_app.shared.generated.resources.reset_password_expired_message
import smart_finance_app.shared.generated.resources.reset_password_success_title
import smart_finance_app.shared.generated.resources.reset_password_success_description
import smart_finance_app.shared.generated.resources.reset_password_success_message
import smart_finance_app.shared.generated.resources.reset_password_error_length
import smart_finance_app.shared.generated.resources.reset_password_error_mismatch
import smart_finance_app.shared.generated.resources.register_show_password
import smart_finance_app.shared.generated.resources.register_hide_password

@Composable
fun ResetPasswordScreen(
    isLoading: Boolean,
    errorMessage: String?,
    successMessage: String?,
    tokenInvalid: Boolean = false,
    onSubmit: (String) -> Unit,
    onBackToSignIn: () -> Unit
) {
    var password     by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val errorLengthMsg   = stringResource(Res.string.reset_password_error_length)
    val errorMismatchMsg = stringResource(Res.string.reset_password_error_mismatch)
    val showPasswordLabel = stringResource(Res.string.register_show_password)
    val hidePasswordLabel = stringResource(Res.string.register_hide_password)

    val valid = password.isNotBlank() && confirmation.isNotBlank() && !isLoading

    fun submit() {
        validationError = when {
            password.length < 8      -> errorLengthMsg
            password != confirmation -> errorMismatchMsg
            else                     -> null
        }
        if (validationError == null) onSubmit(password)
    }

    // Resolve status screen strings up front so we can pass them as plain strings
    val expiredTitle       = stringResource(Res.string.reset_password_expired_title)
    val expiredDescription = stringResource(Res.string.reset_password_expired_description)
    val expiredMessage     = stringResource(Res.string.reset_password_expired_message)
    val successTitle       = stringResource(Res.string.reset_password_success_title)
    val successDescription = stringResource(Res.string.reset_password_success_description)
    val successMsg         = stringResource(Res.string.reset_password_success_message)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 24.dp else 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = if (compact) Modifier.fillMaxWidth()
                else Modifier.widthIn(max = 620.dp),
                shape = if (compact) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp),
                tonalElevation = if (compact) 0.dp else 2.dp,
                shadowElevation = if (compact) 0.dp else 4.dp
            ) {
                when {
                    tokenInvalid -> ResetPasswordStatusContent(
                        title = expiredTitle,
                        description = expiredDescription,
                        message = expiredMessage,
                        backLabel = stringResource(Res.string.reset_password_back),
                        onBackToSignIn = onBackToSignIn
                    )
                    successMessage != null -> ResetPasswordStatusContent(
                        title = successTitle,
                        description = successDescription,
                        message = successMsg,
                        backLabel = stringResource(Res.string.reset_password_back),
                        onBackToSignIn = onBackToSignIn
                    )
                    else -> Column(
                        modifier = Modifier.padding(if (compact) 0.dp else 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(88.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.lock),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = stringResource(Res.string.reset_password_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = stringResource(Res.string.reset_password_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 420.dp)
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; validationError = null },
                            label = { Text(stringResource(Res.string.reset_password_new_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (showPassword) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        painter = painterResource(
                                            if (showPassword) Res.drawable.visibility_off
                                            else Res.drawable.visibility
                                        ),
                                        contentDescription = if (showPassword) hidePasswordLabel
                                        else showPasswordLabel
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            )
                        )

                        OutlinedTextField(
                            value = confirmation,
                            onValueChange = { confirmation = it; validationError = null },
                            label = { Text(stringResource(Res.string.reset_password_confirm_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() })
                        )

                        validationError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }

                        errorMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }

                        Button(
                            enabled = valid,
                            onClick = { submit() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(stringResource(Res.string.reset_password_button))
                            }
                        }

                        TextButton(onClick = onBackToSignIn) {
                            Text(stringResource(Res.string.reset_password_back))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResetPasswordStatusContent(
    title: String,
    description: String,
    message: String,
    backLabel: String,
    onBackToSignIn: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(Res.drawable.lock),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onBackToSignIn,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(backLabel)
        }
    }
}