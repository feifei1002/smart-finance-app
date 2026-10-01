package com.smart_finance_app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.smart_finance_app.theme.AppTheme
import com.smart_finance_app.theme.ThemeController
import org.jetbrains.compose.resources.Font
import smart_finance_app.shared.generated.resources.NotoSansTC_Bold
import smart_finance_app.shared.generated.resources.NotoSansTC_Medium
import smart_finance_app.shared.generated.resources.NotoSansTC_Regular
import smart_finance_app.shared.generated.resources.NotoSans_Bold
import smart_finance_app.shared.generated.resources.NotoSans_Medium
import smart_finance_app.shared.generated.resources.NotoSans_Regular
import smart_finance_app.shared.generated.resources.Res

@Composable
fun SmartFinanceTheme(content: @Composable () -> Unit) {
    val appFontFamily = FontFamily(
        Font(Res.font.NotoSansTC_Regular, weight = FontWeight.Normal),
        Font(Res.font.NotoSansTC_Medium, weight = FontWeight.Medium),
        Font(Res.font.NotoSansTC_Bold, weight = FontWeight.Bold),
        Font(Res.font.NotoSans_Regular, weight = FontWeight.Normal),
        Font(Res.font.NotoSans_Medium, weight = FontWeight.Medium),
        Font(Res.font.NotoSans_Bold, weight = FontWeight.Bold)
    )

    val baseTypography = MaterialTheme.typography

    val appTypography = Typography(
        displayLarge = baseTypography.displayLarge.copy(fontFamily = appFontFamily),
        displayMedium = baseTypography.displayMedium.copy(fontFamily = appFontFamily),
        displaySmall = baseTypography.displaySmall.copy(fontFamily = appFontFamily),
        headlineLarge = baseTypography.headlineLarge.copy(fontFamily = appFontFamily),
        headlineMedium = baseTypography.headlineMedium.copy(fontFamily = appFontFamily),
        headlineSmall = baseTypography.headlineSmall.copy(fontFamily = appFontFamily),
        titleLarge = baseTypography.titleLarge.copy(fontFamily = appFontFamily),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = appFontFamily),
        titleSmall = baseTypography.titleSmall.copy(fontFamily = appFontFamily),
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = appFontFamily),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = appFontFamily),
        bodySmall = baseTypography.bodySmall.copy(fontFamily = appFontFamily),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = appFontFamily),
        labelMedium = baseTypography.labelMedium.copy(fontFamily = appFontFamily),
        labelSmall = baseTypography.labelSmall.copy(fontFamily = appFontFamily)
    )

    val colors = when (ThemeController.currentTheme) {
        AppTheme.PastelPurple -> lightColorScheme(
            primary = Color(0xFF6D55AD),
            primaryContainer = Color(0xFFE8DDF8),
            onPrimaryContainer = Color(0xFF261047),
            secondary = Color(0xFF7C699B),
            surface = Color(0xFFFFF8FF),
            surfaceVariant = Color(0xFFE8E0EA),
            outlineVariant = Color(0xFFCDC4D0)
        )

        AppTheme.PastelBlue -> lightColorScheme(
            primary = Color(0xFF4D74B8),
            primaryContainer = Color(0xFFDCE8FF),
            onPrimaryContainer = Color(0xFF102A4D),
            secondary = Color(0xFF617895),
            surface = Color(0xFFF8FBFF),
            surfaceVariant = Color(0xFFDDE5F0),
            outlineVariant = Color(0xFFC2CAD6)
        )

        AppTheme.PastelGreen -> lightColorScheme(
            primary = Color(0xFF4D8B6A),
            primaryContainer = Color(0xFFD9F1E3),
            onPrimaryContainer = Color(0xFF0D3520),
            secondary = Color(0xFF668171),
            surface = Color(0xFFF8FFF9),
            surfaceVariant = Color(0xFFDDE9E0),
            outlineVariant = Color(0xFFC3CFC6)
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = appTypography,
        content = content
    )
}