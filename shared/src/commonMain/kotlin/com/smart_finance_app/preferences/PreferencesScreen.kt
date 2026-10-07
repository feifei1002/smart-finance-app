package com.smart_finance_app.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smart_finance_app.AppErrorMessage
import com.smart_finance_app.AppStrings
import com.smart_finance_app.LocaleController
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource
import com.smart_finance_app.currency.CurrencyController
import com.smart_finance_app.settings.UserPreferencesApi
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import com.smart_finance_app.currency.getCurrencySymbol
import com.smart_finance_app.settings.UpdatePreferencesResult
import com.smart_finance_app.theme.AppTheme
import com.smart_finance_app.theme.ThemeController


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    authToken: String,
    userPreferencesApi: UserPreferencesApi,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var selectedLanguageCode by remember { mutableStateOf(LocaleController.currentLanguageCode) }
    var selectedCurrency     by remember { mutableStateOf(CurrencyController.currentCurrency) }
    var selectedTheme        by remember { mutableStateOf(ThemeController.currentTheme) }
    var isSaving             by remember { mutableStateOf(false) }
    var errorMessage         by remember { mutableStateOf<String?>(null) }
    var languageExpanded     by remember { mutableStateOf(false) }
    var currencyExpanded     by remember { mutableStateOf(false) }
    var themeExpanded        by remember { mutableStateOf(false) }

    val selectedLanguage = LocaleController.supportedLanguages
        .find { it.code == selectedLanguageCode }

    val initialLanguageCode = remember { LocaleController.currentLanguageCode }
    val initialCurrency     = remember { CurrencyController.currentCurrency }
    val initialTheme        = remember { ThemeController.currentTheme }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start  = if (compact) 24.dp else 40.dp,
                    end    = if (compact) 24.dp else 40.dp,
                    top    = if (compact) 48.dp else 40.dp,
                    bottom = if (compact) 24.dp else 40.dp
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (compact) 520.dp else 600.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Header ────────────────────────────────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text       = appStringResource(StringKey.PREFERENCES_TITLE),
                        style      = if (compact) MaterialTheme.typography.headlineSmall
                        else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign  = TextAlign.Center
                    )
                    Text(
                        text      = appStringResource(StringKey.PREFERENCES_SUBTITLE),
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // ── Language dropdown ─────────────────────────────────────────
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text       = appStringResource(StringKey.PREFERENCES_LANGUAGE_LABEL),
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    ExposedDropdownMenuBox(
                        expanded        = languageExpanded,
                        onExpandedChange = { languageExpanded = it }
                    ) {
                        OutlinedTextField(
                            value         = selectedLanguage?.displayName ?: "",
                            onValueChange = {},
                            readOnly      = true,
                            modifier      = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon  = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = languageExpanded
                                )
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                )
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded        = languageExpanded,
                            onDismissRequest = { languageExpanded = false }
                        ) {
                            LocaleController.supportedLanguages.forEach { language ->
                                val isSelected = language.code == selectedLanguageCode
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(
                                                        color = if (isSelected)
                                                            MaterialTheme.colorScheme.primary
                                                        else
                                                            MaterialTheme.colorScheme.outlineVariant,
                                                        shape = CircleShape
                                                    )
                                            )
                                            Text(
                                                text       = language.displayName,
                                                fontWeight = if (isSelected) FontWeight.SemiBold
                                                else FontWeight.Normal
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedLanguageCode = language.code
                                        languageExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Currency dropdown ─────────────────────────────────────────
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text       = appStringResource(StringKey.PREFERENCES_CURRENCY_LABEL),
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    ExposedDropdownMenuBox(
                        expanded         = currencyExpanded,
                        onExpandedChange = { currencyExpanded = it }
                    ) {
                        OutlinedTextField(
                            value         = "$selectedCurrency  ${getCurrencySymbol(selectedCurrency)}",
                            onValueChange = {},
                            readOnly      = true,
                            modifier      = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon  = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = currencyExpanded
                                )
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                )
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded         = currencyExpanded,
                            onDismissRequest = { currencyExpanded = false }
                        ) {
                            CurrencyController.supportedCurrencies.forEach { code ->
                                val isSelected = code == selectedCurrency
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(
                                                        color = if (isSelected)
                                                            MaterialTheme.colorScheme.primary
                                                        else
                                                            MaterialTheme.colorScheme.outlineVariant,
                                                        shape = CircleShape
                                                    )
                                            )
                                            Text(
                                                text = "$code  ${getCurrencySymbol(code)}",
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedCurrency = code
                                        currencyExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Theme dropdown ───────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = appStringResource(StringKey.SETTINGS_APPEARANCE),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    ExposedDropdownMenuBox(
                        expanded = themeExpanded,
                        onExpandedChange = { themeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = appStringResource(selectedTheme.titleKey),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = themeExpanded
                                )
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                )
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )

                        ExposedDropdownMenu(
                            expanded = themeExpanded,
                            onDismissRequest = { themeExpanded = false }
                        ) {
                            AppTheme.entries.forEach { theme ->
                                val isSelected = theme == selectedTheme

                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(
                                                        color = if (isSelected)
                                                            MaterialTheme.colorScheme.primary
                                                        else
                                                            MaterialTheme.colorScheme.outlineVariant,
                                                        shape = CircleShape
                                                    )
                                            )

                                            Text(
                                                text = appStringResource(theme.titleKey),
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedTheme = theme
                                        ThemeController.setTheme(theme)
                                        themeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Action buttons ────────────────────────────────────────────
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    errorMessage?.let {
                        AppErrorMessage(it)
                    }

                    Button(
                        enabled  = !isSaving,
                        onClick = {
                            scope.launch {
                                isSaving     = true
                                errorMessage = null

                                try {
                                    when (val result = userPreferencesApi.updatePreferences(
                                        authToken,
                                        selectedLanguageCode,
                                        selectedCurrency,
                                        selectedTheme.colour
                                    )) {
                                        UpdatePreferencesResult.Success -> {
                                            LocaleController.setLanguage(selectedLanguageCode)
                                            CurrencyController.setCurrency(selectedCurrency)
                                            ThemeController.setTheme(selectedTheme)
                                            onContinue()
                                        }

                                        is UpdatePreferencesResult.Failure -> {
                                            ThemeController.setTheme(initialTheme)

                                            errorMessage = AppStrings.get(
                                                selectedLanguageCode,
                                                result.message
                                            )
                                        }
                                    }
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text     = appStringResource(StringKey.PREFERENCES_CONTINUE),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    TextButton(
                        onClick = {
                            LocaleController.setLanguage(initialLanguageCode)
                            CurrencyController.setCurrency(initialCurrency)
                            ThemeController.setTheme(initialTheme)
                            onSkip()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text     = appStringResource(StringKey.PREFERENCES_SKIP),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}