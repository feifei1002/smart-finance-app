package com.smart_finance_app.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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

data class SignInForm(val email: String, val password: String)

@Composable
fun SignInScreen(isLoading: Boolean, errorMessage: String?, onSignIn: (SignInForm) -> Unit, onCreateAccount: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val buttonFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val valid = email.trim().contains("@") && password.isNotBlank()

    val submitForm: () -> Unit = {
        if (valid && !isLoading) {
            focusManager.clearFocus()
            onSignIn(
                SignInForm(email = email.trim(), password = password)
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp)
                .onPreviewKeyEvent { event ->
                    if(event.type == KeyEventType.KeyDown &&
                        event.key == Key.Tab
                        ) {
                        focusManager.moveFocus(
                            if(event.isShiftPressed) {
                                FocusDirection.Previous
                            } else {
                                FocusDirection.Next
                            }
                        )
                        true
                    } else {
                        false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "Please sign in to access your financial dashboard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(emailFocus)
                    .focusProperties{ next = passwordFocus },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus()}
                )
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }
                    ) {
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
                    .focusProperties{
                            previous = emailFocus
                            next = buttonFocus
                    }.onPreviewKeyEvent {event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.Enter
                            ) {
                            submitForm()
                            true
                        } else {
                            false
                        }
                    },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submitForm() })
            )

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                enabled = valid && !isLoading,
                onClick = submitForm,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(buttonFocus)
//                    .focusProperties { previous = passwordFocus }
            ) {
                Text(if (isLoading) "Signing in..." else "Sign in")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Don't have an account?")

                TextButton(
                    onClick = onCreateAccount,
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text("Create an account")
                }
            }
        }
    }

}