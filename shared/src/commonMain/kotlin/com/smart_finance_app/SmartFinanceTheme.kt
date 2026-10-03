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
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFD4C3F0),
            onSecondaryContainer = Color(0xFF261047),

            tertiary = Color(0xFF8E5A79),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFD8EA),
            onTertiaryContainer = Color(0xFF381123),

            background = Color(0xFFFFF8FF),
            onBackground = Color(0xFF1F1A24),

            surface = Color(0xFFFFF8FF),
            surfaceVariant = Color(0xFFE8E0EA),
            onSurface = Color(0xFF1F1A24),
            onSurfaceVariant = Color(0xFF51495B),

            outline = Color(0xFF7D7484),
            outlineVariant = Color(0xFFCDC4D0),

            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),

            inverseSurface = Color(0xFF342F39),
            inverseOnSurface = Color(0xFFF7EFF8),
            inversePrimary = Color(0xFFD4BBFF),

            surfaceTint = Color(0xFF6D55AD),
            scrim = Color(0xFF000000)
        )

        AppTheme.PastelBlue -> lightColorScheme(
            primary = Color(0xFF4D74B8),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDCE8FF),
            onPrimaryContainer = Color(0xFF102A4D),

            secondary = Color(0xFF617895),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFC5D8F7),
            onSecondaryContainer = Color(0xFF102A4D),

            tertiary = Color(0xFF5D6F8F),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFDDE6FF),
            onTertiaryContainer = Color(0xFF17284A),

            background = Color(0xFFF8FBFF),
            onBackground = Color(0xFF181C24),

            surface = Color(0xFFF8FBFF),
            surfaceVariant = Color(0xFFDDE5F0),
            onSurface = Color(0xFF181C24),
            onSurfaceVariant = Color(0xFF46505E),

            outline = Color(0xFF737C89),
            outlineVariant = Color(0xFFC2CAD6),

            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),

            inverseSurface = Color(0xFF2D3139),
            inverseOnSurface = Color(0xFFEFF2FA),
            inversePrimary = Color(0xFFB6CEFF),

            surfaceTint = Color(0xFF4D74B8),
            scrim = Color(0xFF000000)
        )

        AppTheme.PastelGreen -> lightColorScheme(
            primary = Color(0xFF4D8B6A),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD9F1E3),
            onPrimaryContainer = Color(0xFF0D3520),

            secondary = Color(0xFF668171),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFBFE5CF),
            onSecondaryContainer = Color(0xFF0D3520),

            tertiary = Color(0xFF5F7F8A),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFD7EEF3),
            onTertiaryContainer = Color(0xFF0A3038),

            background = Color(0xFFF8FFF9),
            onBackground = Color(0xFF18211B),

            surface = Color(0xFFF8FFF9),
            surfaceVariant = Color(0xFFDDE9E0),
            onSurface = Color(0xFF18211B),
            onSurfaceVariant = Color(0xFF465248),

            outline = Color(0xFF748077),
            outlineVariant = Color(0xFFC3CFC6),

            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),

            inverseSurface = Color(0xFF2D332E),
            inverseOnSurface = Color(0xFFEFF7EF),
            inversePrimary = Color(0xFF9BD4AF),

            surfaceTint = Color(0xFF4D8B6A),
            scrim = Color(0xFF000000)
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = appTypography,
        content = content
    )
}