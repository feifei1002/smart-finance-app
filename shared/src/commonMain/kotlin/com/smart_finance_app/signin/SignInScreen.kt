package com.smart_finance_app.signin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
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
import org.jetbrains.compose.resources.stringResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.visibility
import smart_finance_app.shared.generated.resources.visibility_off
import smart_finance_app.shared.generated.resources.signin_title
import smart_finance_app.shared.generated.resources.signin_subtitle
import smart_finance_app.shared.generated.resources.signin_email
import smart_finance_app.shared.generated.resources.signin_password
import smart_finance_app.shared.generated.resources.signin_forgot_password
import smart_finance_app.shared.generated.resources.signin_button
import smart_finance_app.shared.generated.resources.signin_button_loading
import smart_finance_app.shared.generated.resources.signin_no_account
import smart_finance_app.shared.generated.resources.signin_create_account
import smart_finance_app.shared.generated.resources.register_show_password
import smart_finance_app.shared.generated.resources.register_hide_password

data class SignInForm(val email: String, val password: String)

@Composable
fun SignInScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSignIn: (SignInForm) -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val emailFocus    = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val buttonFocus   = remember { FocusRequester() }
    val focusManager  = LocalFocusManager.current

    val showPasswordLabel = stringResource(Res.string.register_show_password)
    val hidePasswordLabel = stringResource(Res.string.register_hide_password)

    val valid = email.trim().contains("@") && password.isNotBlank()

    val submitForm: () -> Unit = {
        if (valid && !isLoading) {
            focusManager.clearFocus()
            onSignIn(SignInForm(email = email.trim(), password = password))
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) {
                        focusManager.moveFocus(
                            if (event.isShiftPressed) FocusDirection.Previous
                            else FocusDirection.Next
                        )
                        true
                    } else false
                },
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(Res.string.signin_title),
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = stringResource(Res.string.signin_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(Res.string.signin_email)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(emailFocus)
                    .focusProperties { next = passwordFocus },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() })
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(Res.string.signin_password)) },
                singleLine = true,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocus)
                    .focusProperties {
                        previous = emailFocus
                        next = buttonFocus
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            submitForm()
                            true
                        } else false
                    },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submitForm() })
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onForgotPassword,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(stringResource(Res.string.signin_forgot_password))
                }
            }

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
                modifier = Modifier.fillMaxWidth().focusRequester(buttonFocus)
            ) {
                Text(
                    if (isLoading) stringResource(Res.string.signin_button_loading)
                    else stringResource(Res.string.signin_button)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(Res.string.signin_no_account))
                TextButton(
                    onClick = onCreateAccount,
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text(stringResource(Res.string.signin_create_account))
                }
            }
        }
    }
}