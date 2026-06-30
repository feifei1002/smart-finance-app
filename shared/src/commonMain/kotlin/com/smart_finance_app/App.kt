package com.smart_finance_app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.smart_finance_app.registration.RegistrationApi
import com.smart_finance_app.registration.RegistrationResult
import com.smart_finance_app.registration.RegistrationScreen
import kotlinx.coroutines.launch

@Composable
fun App(apiBaseUrl: String) {
    MaterialTheme {
        val api = remember(apiBaseUrl) { RegistrationApi(apiBaseUrl) }
        val scope = rememberCoroutineScope()

        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var registered by remember { mutableStateOf(false) }

        if(registered) {
            Text("Account created successfully")
        } else {
            RegistrationScreen(
                isLoading = loading,
                errorMessage = error,
                onRegister = { form ->
                    scope.launch {
                        loading = true
                        error = null
                        when (val result = api.register(form)) {
                            is RegistrationResult.Success -> registered = true
                            is RegistrationResult.Failure -> error = result.message
                        }
                        loading = false
                    }
                }
            )
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
            onRegister = {}
        )
    }
}