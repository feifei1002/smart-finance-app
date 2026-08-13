package com.smart_finance_app.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.appearance
import smart_finance_app.shared.generated.resources.chevron_right
import smart_finance_app.shared.generated.resources.credit_card
import smart_finance_app.shared.generated.resources.crown
import smart_finance_app.shared.generated.resources.currency
import smart_finance_app.shared.generated.resources.dark_mode
import smart_finance_app.shared.generated.resources.language
import smart_finance_app.shared.generated.resources.light_mode
import smart_finance_app.shared.generated.resources.logout
import smart_finance_app.shared.generated.resources.person
import com.smart_finance_app.payments.PlanScreen

private enum class SettingsPanel {
    Main,
    EditProfile,
    SubscriptionPayments,
    SubscriptionPlan
}

@Composable
fun SettingsScreen(userName: String, userEmail: String, onSignOut: () -> Unit) {
    var panel by remember { mutableStateOf(SettingsPanel.Main) }
    var selectedLanguage by remember { mutableStateOf("English") }
    var selectedCurrency by remember { mutableStateOf("GBP") }
    var selectedAppearance by remember { mutableStateOf("Light") }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    if (showLanguageDialog) {
        SettingOptionDialog(
            title = "Language",
            options = listOf("English", "Mandarin"),
            selectedOption = selectedLanguage,
            onSelected = {
                selectedLanguage = it
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showCurrencyDialog) {
        SettingOptionDialog(
            title = "Preferred currency",
            options = listOf("GBP", "USD", "EUR", "CAD", "TWD"),
            selectedOption = selectedCurrency,
            onSelected = {
                selectedCurrency = it
                showCurrencyDialog = false
            },
            onDismiss = { showCurrencyDialog = false }
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out") },
            text = { Text(text = "Do you want to sign out?") },
            confirmButton = {
                Button(onClick = onSignOut) {
                    Text("Sign out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    when (panel) {
        SettingsPanel.Main -> {
            SettingsMainContent(
                userName = userName,
                userEmail = userEmail,
                selectedLanguage = selectedLanguage,
                selectedCurrency = selectedCurrency,
                selectedAppearance = selectedAppearance,
                onAppearanceSelected = { selectedAppearance = it },
                onUpdateProfile = { panel = SettingsPanel.EditProfile },
                onSubscriptionPaymentsClick = { panel = SettingsPanel.SubscriptionPayments },
                onManageSubscription = { panel = SettingsPanel.SubscriptionPlan },
                onLanguageClick = { showLanguageDialog = true },
                onCurrencyClick = { showCurrencyDialog = true },
                onSignOutClick = { showSignOutDialog = true }
            )
        }

        SettingsPanel.EditProfile -> {
            PlaceholderSettingsSubScreen(
                title = "Update profile",
                description = "Profile editing screen coming soon.",
                onBack = { panel = SettingsPanel.Main }
            )
        }

        SettingsPanel.SubscriptionPayments -> {
            PlaceholderSettingsSubScreen(
                title = "Subscription & Payments",
                description = "Payment methods and billing history coming soon.",
                onBack = { panel = SettingsPanel.Main }
            )
        }

        SettingsPanel.SubscriptionPlan -> {
            PlanScreen(
                onBack = { panel = SettingsPanel.Main }
            )
        }
    }
}

@Composable
private fun SettingsMainContent(
    userName: String,
    userEmail: String,
    selectedLanguage: String,
    selectedCurrency: String,
    selectedAppearance: String,
    onAppearanceSelected: (String) -> Unit,
    onUpdateProfile: () -> Unit,
    onSubscriptionPaymentsClick: () -> Unit,
    onManageSubscription: () -> Unit,
    onLanguageClick: () -> Unit,
    onCurrencyClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 24.dp else 40.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (compact) 560.dp else 900.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                if (compact) {
                    Text(
                        text = "Settings",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Column {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Manage your account, preferences and subscription.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SettingsCard {
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            ProfileHeader(userName, userEmail)
                            SettingsActionRow(
                                icon = Res.drawable.person,
                                title = "Update profile",
                                value = null,
                                onClick = onUpdateProfile
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ProfileHeader(userName, userEmail)

                            OutlinedButton(
                                onClick = onUpdateProfile,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.person),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Update profile")
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    SettingsGroup {
                        SettingsActionRow(
                            icon = Res.drawable.credit_card,
                            title = "Subscription & Payments",
                            value = null,
                            onClick = onSubscriptionPaymentsClick
                        )

                        SettingsDivider()

                        SettingsActionRow(
                            icon = Res.drawable.language,
                            title = "Language",
                            value = selectedLanguage,
                            onClick = onLanguageClick
                        )

                        SettingsDivider()

                        SettingsActionRow(
                            icon = Res.drawable.currency,
                            title = "Preferred currency",
                            value = selectedCurrency,
                            onClick = onCurrencyClick
                        )

                        SettingsDivider()

                        AppearanceRow(
                            selectedAppearance = selectedAppearance,
                            onAppearanceSelected = onAppearanceSelected
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = onManageSubscription,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.crown),
                            contentDescription = "Subscription",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))

                        Text("Manage subscription")
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = onSignOutClick,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.logout),
                            contentDescription = "Sign out",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))

                        Text(
                            text = "Sign out",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(userName: String, userEmail: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = userName.initials(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Column {
            Text(
                text = userName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = userEmail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsActionRow(
    icon: DrawableResource,
    title: String,
    value: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )

        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )

        value?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Icon(
            painter = painterResource(Res.drawable.chevron_right),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AppearanceRow(
    selectedAppearance: String,
    onAppearanceSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(Res.drawable.appearance),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )

        Text(
            text = "Appearance",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppearanceIconButton(
                icon = Res.drawable.light_mode,
                selected = selectedAppearance == "Light",
                contentDescription = "Light mode",
                onClick = { onAppearanceSelected("Light") }
            )

            AppearanceIconButton(
                icon = Res.drawable.dark_mode,
                selected = selectedAppearance == "Dark",
                contentDescription = "Dark mode",
                onClick = { onAppearanceSelected("Dark") }
            )
        }

        Icon(
            painter = painterResource(Res.drawable.chevron_right),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AppearanceIconButton(
    icon: DrawableResource,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(width = 36.dp, height = 30.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun SettingsDivider() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    ) {}
}

@Composable
private fun SettingOptionDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selectedOption,
                            onClick = { onSelected(option) }
                        )
                        Text(option)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun PlaceholderSettingsSubScreen(title: String, description: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            TextButton(onClick = onBack) {
                Text("Back")
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = description,
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun String.initials(): String {
    return trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "U" }
}