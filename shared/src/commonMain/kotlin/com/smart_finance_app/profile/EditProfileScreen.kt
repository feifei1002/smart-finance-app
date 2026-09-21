package com.smart_finance_app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.smart_finance_app.AppErrorMessage
import com.smart_finance_app.AppPageHeader
import com.smart_finance_app.AppScreenContainer
import com.smart_finance_app.AppStrings
import com.smart_finance_app.AppSuccessMessage
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
fun EditProfileScreen(
    userName: String,
    userEmail: String,
    authToken: String,
    profileApi: ProfileApi,
    onProfileUpdated: (String, String) -> Unit,
    onUpdatePassword: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var fullName by remember(userName) { mutableStateOf(userName) }
    var email by remember(userEmail) { mutableStateOf(userEmail) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var emailPassword by remember { mutableStateOf("") }
    var showEmailPassword by remember { mutableStateOf(false) }
    var showEmailPasswordDialog by remember { mutableStateOf(false) }
    var emailPasswordError by remember { mutableStateOf<String?>(null) }

    val emailIsValid = email.trim().matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
    val hasChanges = fullName.trim() != userName || email.trim() != userEmail
    val emailChanged = email.trim().lowercase() != userEmail.trim().lowercase()
    val canSave = hasChanges && fullName.trim().isNotBlank() && emailIsValid && !isSaving

    val successText = appStringResource(StringKey.EDIT_PROFILE_SUCCESS)

    fun saveProfile(currentPassword: String?, isEmailPasswordDialog: Boolean = false) {
        scope.launch {
            isSaving = true
            errorMessage = null
            successMessage = null
            if (isEmailPasswordDialog) emailPasswordError = null

            try {
                when (val result = profileApi.updateProfile(
                    token = authToken,
                    fullName = fullName.trim(),
                    email = email.trim(),
                    currentPassword = currentPassword
                )) {
                    is UpdateProfileResult.Success -> {
                        onProfileUpdated(result.profile.fullName, result.profile.email)
                        showEmailPasswordDialog = false
                        emailPassword = ""
                        emailPasswordError = null
                        successMessage = successText
                        onBack()
                    }
                    is UpdateProfileResult.Failure -> {
                        val localizedMessage = AppStrings.get(
                            LocaleController.currentLanguageCode,
                            result.message
                        )
                        if (isEmailPasswordDialog) emailPasswordError = localizedMessage
                        else errorMessage = localizedMessage
                    }
                }
            } finally {
                isSaving = false
            }
        }
    }

    if (showEmailPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSaving) {
                    showEmailPasswordDialog = false
                    emailPassword = ""
                    emailPasswordError = null
                }
            },
            title = { Text(appStringResource(StringKey.EDIT_PROFILE_CONFIRM_PASSWORD_TITLE)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = appStringResource(StringKey.EDIT_PROFILE_CONFIRM_PASSWORD_BODY),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = emailPassword,
                        onValueChange = { emailPassword = it; emailPasswordError = null },
                        label = { Text(appStringResource(StringKey.EDIT_PROFILE_CURRENT_PASSWORD)) },
                        singleLine = true,
                        visualTransformation = if (showEmailPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showEmailPassword = !showEmailPassword }) {
                                Icon(
                                    painter = painterResource(
                                        if (showEmailPassword) Res.drawable.visibility_off
                                        else Res.drawable.visibility
                                    ),
                                    contentDescription = if (showEmailPassword)
                                        appStringResource(StringKey.REGISTER_HIDE_PASSWORD)
                                    else
                                        appStringResource(StringKey.REGISTER_SHOW_PASSWORD)
                                )
                            }
                        }
                    )
                    emailPasswordError?.let { AppErrorMessage(it) }
                }
            },
            confirmButton = {
                Button(
                    enabled = emailPassword.isNotBlank() && !isSaving,
                    onClick = { saveProfile(currentPassword = emailPassword, isEmailPasswordDialog = true) }
                ) {
                    Text(appStringResource(StringKey.EDIT_PROFILE_CONFIRM))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSaving,
                    onClick = {
                        showEmailPasswordDialog = false
                        emailPassword = ""
                        emailPasswordError = null
                    }
                ) {
                    Text(appStringResource(StringKey.EDIT_PROFILE_CANCEL))
                }
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp

        AppScreenContainer(
            compact = compact,
            maxWidth = if (compact) 560.dp else 760.dp
        ) {
            AppPageHeader(
                title = appStringResource(StringKey.EDIT_PROFILE_TITLE),
                subtitle = appStringResource(StringKey.EDIT_PROFILE_SUBTITLE),
                onBack = onBack
            )

            OutlinedTextField(
                value = fullName,
                onValueChange = {
                    fullName = it
                    errorMessage = null
                    successMessage = null
                },
                label = { Text(appStringResource(StringKey.EDIT_PROFILE_FULL_NAME)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    errorMessage = null
                    successMessage = null
                },
                label = { Text(appStringResource(StringKey.EDIT_PROFILE_EMAIL)) },
                singleLine = true,
                isError = email.isNotBlank() && !emailIsValid,
                supportingText = {
                    if (email.isNotBlank() && !emailIsValid) {
                        Text(appStringResource(StringKey.EDIT_PROFILE_EMAIL_INVALID))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = onUpdatePassword,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.lock),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(appStringResource(StringKey.EDIT_PROFILE_UPDATE_PASSWORD))
            }

            errorMessage?.let { AppErrorMessage(it) }

            successMessage?.let { AppSuccessMessage(it) }

            Button(
                enabled = canSave,
                onClick = {
                    if (emailChanged) showEmailPasswordDialog = true
                    else saveProfile(currentPassword = null)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isSaving) appStringResource(StringKey.EDIT_PROFILE_SAVING)
                    else appStringResource(StringKey.EDIT_PROFILE_SAVE)
                )
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(appStringResource(StringKey.EDIT_PROFILE_CANCEL))
            }
        }
    }
}