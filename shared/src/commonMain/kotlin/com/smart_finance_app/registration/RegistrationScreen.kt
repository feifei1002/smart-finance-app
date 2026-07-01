package com.smart_finance_app.registration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.visibility
import smart_finance_app.shared.generated.resources.visibility_off

data class RegistrationForm (
    val fullName: String,
    val email: String,
    val password: String
)

private fun Modifier.tabTo(
    next: FocusRequester,
    previous: FocusRequester? = null
): Modifier = onPreviewKeyEvent { event ->
    if(event.type == KeyEventType.KeyDown && event.key == Key.Tab) {
        if(event.isShiftPressed && previous != null) {
            previous.requestFocus()
        } else {
            next.requestFocus()
        }
        true
    } else {
        false
    }
}

@Composable
fun RegistrationScreen (
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRegister: (RegistrationForm) -> Unit,
    onSignIn: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val nameFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val confirmationFocus = remember { FocusRequester() }
    val buttonFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val valid = fullName.isNotBlank() && email.contains("@") && password.length >= 8 && password == confirmation
    val submitForm = {
        if (valid && !isLoading) {
            focusManager.clearFocus()
            onRegister(
                RegistrationForm(
                    fullName = fullName,
                    email = email,
                    password = password
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Create new account", style = MaterialTheme.typography.headlineMedium)

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocus)
                    .tabTo(emailFocus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() })
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(emailFocus)
                    .tabTo(next = passwordFocus, previous = nameFocus),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                singleLine = true
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation =
                    if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            painter = painterResource(
                                if (showPassword) {
                                    Res.drawable.visibility_off
                                } else {
                                    Res.drawable.visibility
                                }
                            ),
                            contentDescription = if (showPassword) {
                                "Hide password"
                            } else {
                                "Show password"
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocus)
                    .tabTo(previous = emailFocus, next = confirmationFocus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { confirmationFocus.requestFocus() })
            )

            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = { Text("Confirm password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(confirmationFocus)
                    .tabTo(previous = passwordFocus, next = buttonFocus)
                    .onPreviewKeyEvent { event ->
                        if(event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            submitForm()
                            true
                        } else {
                            false
                        }
                    },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { submitForm() }
                ),
                singleLine = true
            )

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                enabled = valid && !isLoading,
                onClick = submitForm,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(buttonFocus)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            submitForm()
                            true
                        } else {
                            false
                        }
                    }
            ) {
                Text(if (isLoading) "Creating account..." else "Create account")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Already have an account?",
                    style = MaterialTheme.typography.bodyMedium
                )

                TextButton(
                    onClick = onSignIn,
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text("Sign in")
                }
            }
        }
    }
}