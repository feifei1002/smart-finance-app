package com.smart_finance_app.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.lock
import smart_finance_app.shared.generated.resources.visibility
import smart_finance_app.shared.generated.resources.visibility_off

@Composable
fun ResetPasswordScreen(isLoading: Boolean, errorMessage: String?, successMessage: String?,
                        tokenInvalid: Boolean = false, onSubmit: (String) -> Unit,
                        onBackToSignIn: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val valid = password.length >= 8 && password == confirmation && !isLoading

    fun submit() {
        validationError = when {
            password.length < 8 -> "Password must be at least 8 characters."
            password != confirmation -> "Passwords do not match."
            else -> null
        }

        if (validationError == null) {
            onSubmit(password)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 24.dp else 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = if (compact) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.widthIn(max = 620.dp)
                },
                shape = if (compact) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp),
                tonalElevation = if (compact) 0.dp else 2.dp,
                shadowElevation = if (compact) 0.dp else 4.dp,
            ) {
                if (tokenInvalid) {
                    ResetPasswordStatusContent(
                        title = "Reset link expired",
                        description = "This password reset link is invalid or has expired.",
                        message = "Please return to the app and request a new password reset email.",
                        onBackToSignIn = onBackToSignIn
                    )
                } else if (successMessage != null) {
                    ResetPasswordStatusContent(
                        title = "Password updated",
                        description = "Your password has been updated successfully.",
                        message = "You can now return to the app and sign in using your new password.",
                        onBackToSignIn = onBackToSignIn
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(if (compact) 0.dp else 48.dp),
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
                            text = "Create a new password",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Enter and confirm your new password to regain access to your account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 420.dp)
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                validationError = null
                            },
                            label = { Text("New password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        painter = painterResource(
                                            if (showPassword) Res.drawable.visibility_off else Res.drawable.visibility
                                        ),
                                        contentDescription = if (showPassword) "Hide password" else "Show password"
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
                            onValueChange = {
                                confirmation = it
                                validationError = null
                            },
                            label = { Text("Confirm password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submit() }
                            )
                        )

                        validationError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }

                        errorMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }

                        successMessage?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
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
                                Text("Update password")
                            }
                        }

                        TextButton(onClick = onBackToSignIn) {
                            Text("Back to login")
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
            Box(contentAlignment = Alignment.Center) {
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
            Text("Back to login")
        }
    }
}