package com.smart_finance_app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val selectedTheme = ThemeController.currentTheme

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
                        selected = selectedTheme == AppTheme.PastelBlue,
                        swatchColor = Color(0xFF4D74B8),
                        onClick = {
                            ThemeController.setTheme(AppTheme.PastelBlue)
                            scope.launch {
                                userPreferencesApi.updateTheme(authToken, AppTheme.PastelBlue.colour)
                            }
                        }
                    )

                    SettingsDivider()

                    ThemeOptionRow(
                        theme = AppTheme.PastelPurple,
                        selected = selectedTheme == AppTheme.PastelPurple,
                        swatchColor = Color(0xFF6D55AD),
                        onClick = {
                            ThemeController.setTheme(AppTheme.PastelPurple)
                            scope.launch {
                                userPreferencesApi.updateTheme(
                                    authToken,
                                    AppTheme.PastelPurple.colour
                                )
                            }
                        }
                    )

                    SettingsDivider()

                    ThemeOptionRow(
                        theme = AppTheme.PastelGreen,
                        selected = selectedTheme == AppTheme.PastelGreen,
                        swatchColor = Color(0xFF4D8B6A),
                        onClick = {
                            ThemeController.setTheme(AppTheme.PastelGreen)
                            scope.launch {
                                userPreferencesApi.updateTheme(authToken, AppTheme.PastelGreen.colour)
                            }
                        }
                    )
                }

            }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    theme: AppTheme,
    selected: Boolean,
    swatchColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(swatchColor)
        )

        Text(
            text = appStringResource(theme.titleKey),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )

        RadioButton(
            selected = selected,
            onClick = onClick
        )
    }
}