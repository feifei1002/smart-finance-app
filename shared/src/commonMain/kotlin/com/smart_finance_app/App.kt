package com.smart_finance_app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.smart_finance_app.consent.ReadOnlyConsentScreen
import com.smart_finance_app.navigation.MainNavigation
import com.smart_finance_app.registration.RegistrationApi
import com.smart_finance_app.registration.RegistrationResult
import com.smart_finance_app.registration.RegistrationScreen
import kotlinx.coroutines.launch

// Tracks which screen is currently shown
private enum class Screen {
    Registration,
    Consent,
    Main
}

@Composable
fun App(apiBaseUrl: String) {
    MaterialTheme {
        val api = remember(apiBaseUrl) { RegistrationApi(apiBaseUrl) }
        val scope = rememberCoroutineScope()

        var screen by remember { mutableStateOf(Screen.Registration) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        when (screen) {
            Screen.Registration -> {
                RegistrationScreen(
                    isLoading = loading,
                    errorMessage = error,
                    onRegister = { form ->
                        scope.launch {
                            loading = true
                            error = null
                            try {
                                when (val result = api.register(form)) {
                                    is RegistrationResult.Success -> screen = Screen.Consent
                                    is RegistrationResult.Failure -> error = result.message
                                }
                            } finally {
                                loading = false
                            }
                        }
                    },
                    onSignIn = {
                        // TODO: navigate to sign-in screen
                    }
                )
            }
            Screen.Consent -> {
                ReadOnlyConsentScreen(
                    onContinue = { screen = Screen.Main },
                    onCancel = { screen = Screen.Registration }
                )
            }
            Screen.Main -> {
                // TODO: replace with real Dashboard screen
                MainNavigation()
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