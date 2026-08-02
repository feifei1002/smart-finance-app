package com.smart_finance_app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.smart_finance_app.consent.ConsentApi
import com.smart_finance_app.consent.ReadOnlyConsentScreen
import com.smart_finance_app.navigation.MainNavigation
import com.smart_finance_app.registration.RegistrationApi
import com.smart_finance_app.registration.RegistrationResult
import com.smart_finance_app.registration.RegistrationScreen
import com.smart_finance_app.signin.AuthSession
import com.smart_finance_app.consent.ConsentResult
import com.smart_finance_app.signin.ForgotPasswordScreen
import com.smart_finance_app.signin.PasswordResetApi
import com.smart_finance_app.signin.PasswordResetConfirmResult
import com.smart_finance_app.signin.PasswordResetRequestResult
import com.smart_finance_app.signin.PasswordResetValidateResult
import com.smart_finance_app.signin.ResetPasswordScreen
import com.smart_finance_app.signin.SignInApi
import com.smart_finance_app.signin.SignInResult
import com.smart_finance_app.signin.SignInScreen
import kotlinx.coroutines.launch

// Tracks which screen is currently shown
private enum class Screen {
    Registration,
    SignIn,
    ForgotPassword,
    ResetPassword,
    Consent,
    Main
}

@Composable
fun App(apiBaseUrl: String, isPasswordResetRoute: Boolean = false, passwordResetToken: String? = null) {
    MaterialTheme {
        val registrationApi = remember(apiBaseUrl) { RegistrationApi(apiBaseUrl) }

        val signInApi = remember(apiBaseUrl) { SignInApi(apiBaseUrl) }

        val passwordResetApi = remember(apiBaseUrl) { PasswordResetApi(apiBaseUrl) }

        val consentApi = remember(apiBaseUrl) { ConsentApi(apiBaseUrl) }

        DisposableEffect(signInApi) {
            onDispose { signInApi.close() }
        }

        DisposableEffect(passwordResetApi) {
            onDispose { passwordResetApi.close() }
        }

        DisposableEffect(consentApi) {
            onDispose { consentApi.close() }
        }

        val scope = rememberCoroutineScope()

        var screen by remember {
            mutableStateOf(
                if (isPasswordResetRoute) {
                    Screen.ResetPassword
                } else {
                    Screen.Registration
                }
            )
        }

        var session by remember { mutableStateOf<AuthSession?>(null) }
        var registrationLoading by remember { mutableStateOf(false) }
        var registrationError by remember { mutableStateOf<String?>(null) }
        var signInLoading by remember { mutableStateOf(false) }
        var signInError by remember { mutableStateOf<String?>(null) }
        var consentError by remember { mutableStateOf<String?>(null) }
        var forgotPasswordLoading by remember { mutableStateOf(false) }
        var forgotPasswordSuccess by remember { mutableStateOf<String?>(null) }
        var forgotPasswordError by remember { mutableStateOf<String?>(null) }
        var resetPasswordLoading by remember { mutableStateOf(false) }
        var resetPasswordError by remember { mutableStateOf<String?>(null) }
        var resetPasswordSuccess by remember { mutableStateOf<String?>(null) }
        var resetPasswordTokenInvalid by remember(passwordResetToken) {
            mutableStateOf(passwordResetToken.isNullOrBlank())
        }

        LaunchedEffect(screen, passwordResetToken) {
            if (screen == Screen.ResetPassword && !passwordResetToken.isNullOrBlank()) {
                when (passwordResetApi.validateResetToken(passwordResetToken)) {
                    is PasswordResetValidateResult.Success -> {
                        resetPasswordTokenInvalid = false
                    }

                    is PasswordResetValidateResult.Failure -> {
                        resetPasswordTokenInvalid = true
                    }
                }
            }
        }

        when (screen) {
            Screen.Registration -> {
                RegistrationScreen(
                    isLoading = registrationLoading,
                    errorMessage = registrationError,
                    onRegister = { form ->
                        scope.launch {
                            registrationLoading = true
                            registrationError = null
                            try {
                                when (val result = registrationApi.register(form)) {
                                    is RegistrationResult.Success -> {
                                        session = result.session
                                        screen = Screen.Consent
                                    }
                                    is RegistrationResult.Failure -> registrationError = result.message
                                }
                            } finally {
                                registrationLoading = false
                            }
                        }
                    },
                    onSignIn = {
                        signInError= null
                        screen = Screen.SignIn
                    }
                )
            }
            Screen.SignIn -> {
                SignInScreen(
                    isLoading = signInLoading,
                    errorMessage = signInError,
                    onSignIn = { form ->
                        scope.launch {
                            signInLoading = true
                            signInError = null
                            try {
                                when (val result = signInApi.signIn(form)) {
                                    is SignInResult.Success -> {
                                        session = result.session
                                        screen = if (result.session.consentAccepted) {
                                            Screen.Main
                                        } else {
                                            Screen.Consent
                                        }
                                    }
                                    is SignInResult.Failure -> signInError = result.message
                                }
                            } finally {
                                signInLoading = false
                            }
                        }
                    },
                    onCreateAccount = {
                        registrationError = null
                        screen = Screen.Registration
                    },
                    onForgotPassword = {
                        forgotPasswordError = null
                        forgotPasswordSuccess = null
                        screen = Screen.ForgotPassword
                    }
                )
            }

            Screen.ForgotPassword -> {
                ForgotPasswordScreen(
                    isLoading = forgotPasswordLoading,
                    errorMessage = forgotPasswordError,
                    successMessage = forgotPasswordSuccess,
                    onSubmit = { email ->
                        scope.launch {
                            forgotPasswordLoading = true
                            forgotPasswordError = null
                            forgotPasswordSuccess = null

                            try {
                                when (val result = passwordResetApi.requestReset(email)) {
                                    PasswordResetRequestResult.Success -> {
                                        forgotPasswordSuccess =
                                            "If an account exists for this email, a password reset link has been sent."
                                    }

                                    is PasswordResetRequestResult.Failure -> {
                                        forgotPasswordError = result.message
                                    }
                                }
                            } finally {
                                forgotPasswordLoading = false
                            }
                        }
                    },
                    onBackToSignIn = {
                        forgotPasswordError = null
                        forgotPasswordSuccess = null
                        screen = Screen.SignIn
                    }
                )
            }
            
            Screen.ResetPassword -> {
                ResetPasswordScreen(
                    isLoading = resetPasswordLoading,
                    errorMessage = resetPasswordError,
                    successMessage = resetPasswordSuccess,
                    tokenInvalid = resetPasswordTokenInvalid,
                    onSubmit = { newPassword ->

                        if(passwordResetToken.isNullOrBlank()) {
                            resetPasswordTokenInvalid = true
                            return@ResetPasswordScreen
                        }

                        scope.launch {
                            resetPasswordLoading = true
                            resetPasswordError = null

                            when (
                                val result = passwordResetApi.confirmReset(
                                    token = passwordResetToken,
                                    newPassword = newPassword
                                )
                            ) {
                                is PasswordResetConfirmResult.Success -> {
                                    resetPasswordSuccess =
                                        "Your password has been updated successfully."
                                }

                                is PasswordResetConfirmResult.Failure -> {
                                    if (
                                        result.message.contains("expired", ignoreCase = true) ||
                                        result.message.contains("invalid", ignoreCase = true)
                                    ) {
                                        resetPasswordTokenInvalid = true
                                    } else {
                                        resetPasswordError = result.message
                                    }
                                }
                            }
                            resetPasswordLoading = false
                        }
                    },
                    onBackToSignIn = {
                        screen = Screen.SignIn
                    }
                )
            }

            Screen.Consent -> {
                ReadOnlyConsentScreen(
                    errorMessage = consentError,
                    onContinue = {
                        val currentSession = session
                        if (currentSession == null) {
                            consentError = "Your session expired. Please sign in again."
                            screen = Screen.SignIn
                            return@ReadOnlyConsentScreen
                        }

                        scope.launch {
                            consentError = null
                            when (val result = consentApi.acceptConsent(currentSession.token)) {
                                ConsentResult.Success -> {
                                    screen = Screen.Main
                                }

                                is ConsentResult.Failure -> {
                                    consentError = result.message
                                }
                            }
                        }
                    },
                    onCancel = {
                        screen = Screen.Registration
                    }
                )
            }
            Screen.Main -> {
                val resolvedName: String = session?.email?.substringBefore("@") ?: ""
                MainNavigation(
                    apiBaseUrl = apiBaseUrl,
                    authToken = session?.token.orEmpty(),
                    userName = resolvedName,
                    onSignOut = {
                        session = null
                        signInError = null
                        registrationError = null
                        screen = Screen.Registration
                    }
                )
            }
        }
    }
}

@Preview
@Composable
private fun RegistrationScreenPreview() {
    MaterialTheme {
        RegistrationScreen(
            isLoading = false,
            errorMessage = null,
            onRegister = {},
            onSignIn = {}
        )
    }
}


/* These are purely for checking how the UI is displayed,
 as it might be difficult to check these pages on device emulator
 */
@Preview
@Composable
private fun ForgotPasswordScreenPreview() {
    MaterialTheme {
        ForgotPasswordScreen(
            isLoading = false,
            errorMessage = null,
            successMessage = null,
            onSubmit = {},
            onBackToSignIn = {}
        )
    }
}

@Preview
@Composable
private fun ResetPasswordExpiredPreview() {
    MaterialTheme {
        ResetPasswordScreen(
            isLoading = false,
            errorMessage = null,
            successMessage = null,
            tokenInvalid = true,
            onSubmit = {},
            onBackToSignIn = {}
        )
    }
}

@Preview
@Composable
private fun ResetPasswordSuccessPreview() {
    MaterialTheme {
        ResetPasswordScreen(
            isLoading = false,
            errorMessage = null,
            successMessage = "Your password has been updated successfully.",
            tokenInvalid = false,
            onSubmit = {},
            onBackToSignIn = {}
        )
    }
}