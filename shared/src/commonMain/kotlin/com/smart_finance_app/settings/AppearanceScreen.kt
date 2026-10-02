package com.smart_finance_app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smart_finance_app.*
import com.smart_finance_app.theme.AppTheme
import com.smart_finance_app.theme.ThemeController
import kotlinx.coroutines.launch

@Composable
internal fun AppearanceScreen(
    authToken: String,
    userPreferencesApi: UserPreferencesApi,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var savingTheme by remember { mutableStateOf(false) }

    fun selectTheme(theme: AppTheme) {
        if (savingTheme || ThemeController.currentTheme == theme) return

        val previousTheme = ThemeController.currentTheme
        ThemeController.setTheme(theme)
        savingTheme = true

        scope.launch {
            when (userPreferencesApi.updateTheme(authToken, theme.colour)) {
                UpdateThemeResult.Success -> Unit

                is UpdateThemeResult.Failure -> {
                    ThemeController.setTheme(previousTheme)
                    snackbarHostState.showSnackbar(
                        AppStrings.get(
                            LocaleController.currentLanguageCode,
                            StringKey.SETTINGS_THEME_SAVE_FAILED
                        )
                    )
                }
            }

            savingTheme = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxWidth < 700.dp

            AppScreenContainer(
                compact = compact,
                maxWidth = if (compact) 560.dp else 900.dp
            ) {
                AppPageHeader(
                    title = appStringResource(StringKey.SETTINGS_APPEARANCE_TITLE),
                    subtitle = appStringResource(StringKey.SETTINGS_CHOOSE_THEME),
                    onBack = onBack,
                    compact = compact
                )


                Spacer(Modifier.height(16.dp))

                SettingsCard {
                    SettingsGroup {
                        ThemeOptionRow(
                            theme = AppTheme.PastelBlue,
                            selected = ThemeController.currentTheme == AppTheme.PastelBlue,
                            enabled = !savingTheme,
                            onClick = { selectTheme(AppTheme.PastelBlue) }
                        )

                        SettingsDivider()

                        ThemeOptionRow(
                            theme = AppTheme.PastelPurple,
                            selected = ThemeController.currentTheme == AppTheme.PastelPurple,
                            enabled = !savingTheme,
                            onClick = { selectTheme(AppTheme.PastelPurple) }
                        )

                        SettingsDivider()

                        ThemeOptionRow(
                            theme = AppTheme.PastelGreen,
                            selected = ThemeController.currentTheme == AppTheme.PastelGreen,
                            enabled = !savingTheme,
                            onClick = { selectTheme(AppTheme.PastelGreen) }
                        )
                    }

                }
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    theme: AppTheme,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(theme.swatchColor, CircleShape)
        )

        Text(
            text = appStringResource(theme.titleKey),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )

        RadioButton(
            selected = selected,
            enabled = enabled,
            onClick = onClick
        )
    }
}