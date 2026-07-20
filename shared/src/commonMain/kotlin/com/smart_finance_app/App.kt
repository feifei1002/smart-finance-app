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
import com.smart_finance_app.signin.SignInApi
import com.smart_finance_app.signin.SignInResult
import com.smart_finance_app.signin.SignInScreen
import kotlinx.coroutines.launch

// Tracks which screen is currently shown
private enum class Screen {
    Registration,
    SignIn,
    Consent,
    Main
}

@Composable
fun App(apiBaseUrl: String) {
    MaterialTheme {
        val registrationApi = remember(apiBaseUrl) { RegistrationApi(apiBaseUrl) }

        val signInApi = remember(apiBaseUrl) { SignInApi(apiBaseUrl) }

        val consentApi = remember(apiBaseUrl) { ConsentApi(apiBaseUrl) }

        DisposableEffect(signInApi) {
            onDispose { signInApi.close() }
        }

        DisposableEffect(consentApi) {
            onDispose { consentApi.close() }
        }

        val scope = rememberCoroutineScope()

        var screen by remember { mutableStateOf(Screen.Registration) }
        var session by remember { mutableStateOf<AuthSession?>(null) }
        var registrationLoading by remember { mutableStateOf(false) }
        var registrationError by remember { mutableStateOf<String?>(null) }
        var signInLoading by remember { mutableStateOf(false) }
        var signInError by remember { mutableStateOf<String?>(null) }
        var consentError by remember { mutableStateOf<String?>(null) }

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