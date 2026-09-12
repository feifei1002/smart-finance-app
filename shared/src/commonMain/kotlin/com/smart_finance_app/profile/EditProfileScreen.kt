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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.lock

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

    val emailIsValid = email.trim().matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
    val hasChanges = fullName.trim() != userName || email.trim() != userEmail
    val canSave = hasChanges && fullName.trim().isNotBlank() && emailIsValid && !isSaving

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 24.dp else 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (compact) 560.dp else 760.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Edit Profile",
                        style = if (compact) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Edit your profile here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = {
                            fullName = it
                            errorMessage = null
                            successMessage = null
                        },
                        label = { Text("Full name") },
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
                        label = { Text("Email address") },
                        singleLine = true,
                        isError = email.isNotBlank() && !emailIsValid,
                        supportingText = {
                            if (email.isNotBlank() && !emailIsValid) {
                                Text("Please enter a valid email address")
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
                            contentDescription = "Update password",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))

                        Text("Update password")
                    }

                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }

                    successMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary)
                    }

                    Button(
                        enabled = canSave,
                        onClick = {
                            scope.launch {
                                isSaving = true
                                errorMessage = null
                                successMessage = null

                                when (
                                    val result = profileApi.updateProfile(
                                        token = authToken,
                                        fullName = fullName.trim(),
                                        email = email.trim()
                                    )
                                ) {
                                    is UpdateProfileResult.Success -> {
                                        onProfileUpdated(
                                            result.profile.fullName,
                                            result.profile.email
                                        )
                                        successMessage = "Profile updated successfully"
                                        onBack()
                                    }

                                    is UpdateProfileResult.Failure -> {
                                        errorMessage = result.message
                                    }
                                }

                                isSaving = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSaving) "Saving..." else "Save changes")
                    }

                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}