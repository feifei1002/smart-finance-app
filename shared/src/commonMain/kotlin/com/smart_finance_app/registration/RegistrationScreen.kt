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

data class RegistrationForm(
    val fullName: String,
    val email: String,
    val password: String
)

private fun Modifier.tabTo(
    next: FocusRequester,
    previous: FocusRequester? = null
): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) {
        if (event.isShiftPressed && previous != null) {
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
fun RegistrationScreen(
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

    val nameFocus         = remember { FocusRequester() }
    val emailFocus        = remember { FocusRequester() }
    val passwordFocus     = remember { FocusRequester() }
    val confirmationFocus = remember { FocusRequester() }
    val buttonFocus       = remember { FocusRequester() }
    val focusManager      = LocalFocusManager.current

    val showPasswordLabel = appStringResource(StringKey.REGISTER_SHOW_PASSWORD)
    val hidePasswordLabel = appStringResource(StringKey.REGISTER_HIDE_PASSWORD)

    val valid = fullName.isNotBlank() &&
            email.contains("@") &&
            password.length >= 8 &&
            password == confirmation

    val submitForm = {
        if (valid && !isLoading) {
            focusManager.clearFocus()
            onRegister(
                RegistrationForm(
                    fullName = fullName,
                    email    = email,
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppPageHeader(
                title = appStringResource(StringKey.REGISTER_TITLE),
                subtitle = null
            )

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text(appStringResource(StringKey.REGISTER_FULL_NAME)) },
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
                label = { Text(appStringResource(StringKey.REGISTER_EMAIL)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(emailFocus)
                    .tabTo(next = passwordFocus, previous = nameFocus),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                singleLine = true
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(appStringResource(StringKey.REGISTER_PASSWORD)) },
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
                    .tabTo(previous = emailFocus, next = confirmationFocus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { confirmationFocus.requestFocus() })
            )

            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = { Text(appStringResource(StringKey.REGISTER_CONFIRM_PASSWORD)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(confirmationFocus)
                    .tabTo(previous = passwordFocus, next = buttonFocus)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            submitForm()
                            true
                        } else {
                            false
                        }
                    },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitForm() }),
                singleLine = true
            )

            errorMessage?.let { AppErrorMessage(it) }

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
                Text(
                    text = if (isLoading) {
                        appStringResource(StringKey.REGISTER_BUTTON_LOADING)
                    } else {
                        appStringResource(StringKey.REGISTER_BUTTON)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = appStringResource(StringKey.REGISTER_ALREADY_HAVE_ACCOUNT),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(
                    onClick = onSignIn,
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text(appStringResource(StringKey.REGISTER_SIGN_IN))
                }
            }
        }
    }
}