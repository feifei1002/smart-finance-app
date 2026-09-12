package com.smart_finance_app.profile

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.lock
import smart_finance_app.shared.generated.resources.visibility
import smart_finance_app.shared.generated.resources.visibility_off

@Composable
fun UpdatePasswordScreen(
    authToken: String,
    profileApi: ProfileApi,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember{ mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (isSaving) return

        validationError = when {
            currentPassword.isBlank() -> "Current password is required."
            newPassword.length < 8 -> "New password must be at least 8 characters."
            newPassword != confirmPassword -> "Passwords do not match."
            else -> null
        }

        if (validationError != null) return

        scope.launch {
            isSaving = true
            errorMessage = null
            successMessage = null

            try {
                when (
                    val result = profileApi.changePassword(
                        token = authToken,
                        currentPassword = currentPassword,
                        newPassword = newPassword
                    )
                ) {
                    ChangePasswordResult.Success -> {
                        currentPassword = ""
                        newPassword = ""
                        confirmPassword = ""
                        successMessage = "Password updated successfully."
                    }

                    is ChangePasswordResult.Failure -> {
                        errorMessage = result.message
                    }
                }
            } finally {
                isSaving = false
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 24.dp else 40.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (compact) 560.dp else 620.dp),
                shape = RoundedCornerShape(if (compact) 0.dp else 12.dp),
                tonalElevation = if (compact) 0.dp else 2.dp,
                shadowElevation = if (compact) 0.dp else 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(if (compact) 0.dp else 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text("Back")
                    }

                    Surface(
                        modifier = Modifier.size(80.dp),
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
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Text(
                        text = "Update Password",
                        style = if (compact) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Update your current password here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PasswordField(
                            value = currentPassword,
                            onValueChange = {
                                currentPassword = it
                                validationError = null
                                errorMessage = null
                                successMessage = null
                            },
                            label = "Current password",
                            visible = showCurrentPassword,
                            onVisibilityChange = { showCurrentPassword = !showCurrentPassword },
                            imeAction = ImeAction.Next
                        )

                        PasswordField(
                            value = newPassword,
                            onValueChange = {
                                newPassword = it
                                validationError = null
                                errorMessage = null
                                successMessage = null
                            },
                            label = "New password",
                            visible = showNewPassword,
                            onVisibilityChange = { showNewPassword = !showNewPassword },
                            imeAction = ImeAction.Next
                        )

                        PasswordField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                validationError = null
                                errorMessage = null
                                successMessage = null
                            },
                            label = "Confirm new password",
                            visible = showConfirmPassword,
                            onVisibilityChange = { showConfirmPassword = !showConfirmPassword },
                            imeAction = ImeAction.Done,
                            onDone = { submit() }
                        )
                    }

                    validationError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                            )
                    }

                    errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    successMessage?.let {
                        Text(text = it,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        enabled = !isSaving &&
                                currentPassword.isNotBlank() &&
                                newPassword.isNotBlank() &&
                                confirmPassword.isNotBlank(),
                        onClick = { submit() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (compact) 52.dp else 44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save password")
                        }
                    }

                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onVisibilityChange: () -> Unit,
    imeAction: ImeAction,
    onDone: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = onVisibilityChange) {
                Icon(
                    painter = painterResource(
                        if (visible) Res.drawable.visibility_off else Res.drawable.visibility
                    ),
                    contentDescription = if (visible) "Hide password" else "Show password"
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() }
        )
    )
}