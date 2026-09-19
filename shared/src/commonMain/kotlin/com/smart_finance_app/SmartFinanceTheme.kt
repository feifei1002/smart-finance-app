package com.smart_finance_app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

    MaterialTheme(
        typography = appTypography,
        content = content
    )
}