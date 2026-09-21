package com.smart_finance_app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smart_finance_app.AppErrorMessage
import com.smart_finance_app.AppStrings
import com.smart_finance_app.LocaleController
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource
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
    onPasswordUpdated: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Resolve validation strings outside the non-composable submit() function
    val lang = LocaleController.currentLanguageCode
    val errorCurrentRequired = AppStrings.get(lang, StringKey.UPDATE_PASSWORD_ERROR_CURRENT_REQUIRED)
    val errorLength          = AppStrings.get(lang, StringKey.UPDATE_PASSWORD_ERROR_LENGTH)
    val errorMismatch        = AppStrings.get(lang, StringKey.UPDATE_PASSWORD_ERROR_MISMATCH)

    fun submit() {
        if (isSaving) return
        validationError = when {
            currentPassword.isBlank() -> errorCurrentRequired
            newPassword.length < 8   -> errorLength
            newPassword != confirmPassword -> errorMismatch
            else -> null
        }
        if (validationError != null) return

        scope.launch {
            isSaving = true
            errorMessage = null
            try {
                when (val result = profileApi.changePassword(
                    token = authToken,
                    currentPassword = currentPassword,
                    newPassword = newPassword
                )) {
                    ChangePasswordResult.Success -> {
                        currentPassword = ""
                        newPassword = ""
                        confirmPassword = ""
                        showSuccessDialog = true
                    }
                    is ChangePasswordResult.Failure -> {
                        errorMessage = AppStrings.get(
                            LocaleController.currentLanguageCode,
                            result.message
                        )
                    }
                }
            } finally {
                isSaving = false
            }
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(appStringResource(StringKey.UPDATE_PASSWORD_SUCCESS_TITLE)) },
            text = { Text(appStringResource(StringKey.UPDATE_PASSWORD_SUCCESS_BODY)) },
            confirmButton = {
                Button(onClick = {
                    showSuccessDialog = false
                    onPasswordUpdated()
                }) {
                    Text(appStringResource(StringKey.UPDATE_PASSWORD_OK))
                }
            }
        )
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
                        Text(appStringResource(StringKey.UPDATE_PASSWORD_BACK))
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
                        text = appStringResource(StringKey.UPDATE_PASSWORD_TITLE),
                        style = if (compact) MaterialTheme.typography.headlineSmall
                        else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = appStringResource(StringKey.UPDATE_PASSWORD_SUBTITLE),
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
                            },
                            label = appStringResource(StringKey.UPDATE_PASSWORD_CURRENT),
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
                            },
                            label = appStringResource(StringKey.UPDATE_PASSWORD_NEW),
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
                            },
                            label = appStringResource(StringKey.UPDATE_PASSWORD_CONFIRM),
                            visible = showConfirmPassword,
                            onVisibilityChange = { showConfirmPassword = !showConfirmPassword },
                            imeAction = ImeAction.Done,
                            onDone = { submit() }
                        )
                    }

                    validationError?.let { AppErrorMessage(it) }

                    errorMessage?.let { AppErrorMessage(it) }

                    Button(
                        enabled = !isSaving &&
                                currentPassword.isNotBlank() &&
                                newPassword.isNotBlank() &&
                                confirmPassword.isNotBlank(),
                        onClick = { submit() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = appStringResource(StringKey.UPDATE_PASSWORD_BUTTON),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            appStringResource(StringKey.UPDATE_PASSWORD_CANCEL),
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
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onVisibilityChange: () -> Unit,
    imeAction: ImeAction,
    onDone: () -> Unit = {}
) {
    val showLabel  = appStringResource(StringKey.REGISTER_SHOW_PASSWORD)
    val hideLabel  = appStringResource(StringKey.REGISTER_HIDE_PASSWORD)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (visible) VisualTransformation.None
        else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onVisibilityChange) {
                Icon(
                    painter = painterResource(
                        if (visible) Res.drawable.visibility_off
                        else Res.drawable.visibility
                    ),
                    contentDescription = if (visible) hideLabel else showLabel
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() })
    )
}