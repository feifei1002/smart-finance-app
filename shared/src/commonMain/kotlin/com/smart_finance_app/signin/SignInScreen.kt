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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smart_finance_app.AppErrorMessage
import com.smart_finance_app.AppPageHeader
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.visibility
import smart_finance_app.shared.generated.resources.visibility_off
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource

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

    val showPasswordLabel = appStringResource(StringKey.REGISTER_SHOW_PASSWORD)
    val hidePasswordLabel = appStringResource(StringKey.REGISTER_HIDE_PASSWORD)

    val valid = email.trim().contains("@") && password.isNotBlank()

    val submitForm: () -> Unit = {
        if (valid && !isLoading) {
            focusManager.clearFocus()
            onSignIn(SignInForm(email = email.trim(), password = password))
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppPageHeader(
                title = appStringResource(StringKey.SIGNIN_TITLE),
                subtitle = appStringResource(StringKey.SIGNIN_SUBTITLE)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(appStringResource(StringKey.SIGNIN_EMAIL)) },
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
                label = { Text(appStringResource(StringKey.SIGNIN_PASSWORD)) },
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
                    Text(
                        text = appStringResource(StringKey.SIGNIN_FORGOT_PASSWORD),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            errorMessage?.let { AppErrorMessage(it) }

            Button(
                enabled = valid && !isLoading,
                onClick = submitForm,
                modifier = Modifier.fillMaxWidth().focusRequester(buttonFocus)
            ) {
                Text(
                    text = if (isLoading) appStringResource(StringKey.SIGNIN_BUTTON_LOADING)
                    else appStringResource(StringKey.SIGNIN_BUTTON),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(appStringResource(StringKey.SIGNIN_NO_ACCOUNT))
                TextButton(
                    onClick = onCreateAccount,
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text(
                        text = appStringResource(StringKey.SIGNIN_CREATE_ACCOUNT),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}