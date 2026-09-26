package com.smart_finance_app.payments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smart_finance_app.AppErrorMessage
import com.smart_finance_app.AppPageHeader
import com.smart_finance_app.AppScreenContainer
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.check
import smart_finance_app.shared.generated.resources.star
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource

@Composable
fun PlanScreen(
    subscriptionStatus: String,
    isLoading: Boolean,
    errorMessage: String?,
    onSubscribeToBasic: () -> Unit,
    onBack: () -> Unit
) {
    val isPaidPlan = subscriptionStatus.equals("pro", ignoreCase = true) ||
            subscriptionStatus.equals("basic", ignoreCase = true)
    val isFreePlan = !isPaidPlan

    val freeTitleStr       = appStringResource(StringKey.PLAN_FREE_TITLE)
    val freePriceStr       = appStringResource(StringKey.PLAN_FREE_PRICE)
    val freeDetailTopStr   = appStringResource(StringKey.PLAN_FREE_PRICE_DETAIL_TOP)
    val freeDetailBotStr   = appStringResource(StringKey.PLAN_FREE_PRICE_DETAIL_BOTTOM)
    val freeButtonStr      = appStringResource(StringKey.PLAN_FREE_BUTTON_CURRENT)
    val basicTitleStr      = appStringResource(StringKey.PLAN_BASIC_TITLE)
    val basicSubtitleStr   = appStringResource(StringKey.PLAN_BASIC_SUBTITLE)
    val basicPriceStr      = appStringResource(StringKey.PLAN_BASIC_PRICE)
    val basicDetailTopStr  = appStringResource(StringKey.PLAN_BASIC_PRICE_DETAIL_TOP)
    val basicDetailBotStr  = appStringResource(StringKey.PLAN_BASIC_PRICE_DETAIL_BOTTOM)
    // Basic is always disabled for MVP — show "Coming Soon"
    val basicButtonStr     = appStringResource(StringKey.PLAN_BASIC_BUTTON_COMING_SOON)
    val comingSoonBadge    = appStringResource(StringKey.PLAN_COMING_SOON_BADGE)

    val freeFeatures = listOf(
        appStringResource(StringKey.PLAN_FREE_FEATURE_1),
        appStringResource(StringKey.PLAN_FREE_FEATURE_2),
        appStringResource(StringKey.PLAN_FREE_FEATURE_3),
        appStringResource(StringKey.PLAN_FREE_FEATURE_4),
    )

    // Features 9 and 10 carry a "coming soon" badge; the rest are straightforward
    val basicFeatures = listOf(
        PlanFeature(appStringResource(StringKey.PLAN_BASIC_FEATURE_1)),
        PlanFeature(appStringResource(StringKey.PLAN_BASIC_FEATURE_2)),
        PlanFeature(appStringResource(StringKey.PLAN_BASIC_FEATURE_3)),
        PlanFeature(appStringResource(StringKey.PLAN_BASIC_FEATURE_4)),
        PlanFeature(appStringResource(StringKey.PLAN_BASIC_FEATURE_5)),
        PlanFeature(appStringResource(StringKey.PLAN_BASIC_FEATURE_6)),
        PlanFeature(appStringResource(StringKey.PLAN_BASIC_FEATURE_7)),
        PlanFeature(appStringResource(StringKey.PLAN_BASIC_FEATURE_8)),
        PlanFeature(appStringResource(StringKey.PLAN_BASIC_FEATURE_9),  comingSoon = true),
        PlanFeature(appStringResource(StringKey.PLAN_BASIC_FEATURE_10), comingSoon = true),
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp

        AppScreenContainer(
            compact = compact,
            maxWidth = if (compact) 560.dp else 900.dp
        ) {
            AppPageHeader(
                title = appStringResource(StringKey.PLAN_TITLE),
                onBack = onBack,
                compact = compact
            )

            errorMessage?.let {
                AppErrorMessage(it)
            }

            PlanCard(
                title             = freeTitleStr,
                subtitle          = "",
                price             = freePriceStr,
                priceDetailTop    = freeDetailTopStr,
                priceDetailBottom = freeDetailBotStr,
                buttonText        = freeButtonStr,
                enabled           = false,
                isCurrentPlan     = true,
                starCount         = 1,
                features          = freeFeatures.map { PlanFeature(it) }
            )

            PlanCard(
                title             = basicTitleStr,
                subtitle          = basicSubtitleStr,
                price             = basicPriceStr,
                priceDetailTop    = basicDetailTopStr,
                priceDetailBottom = basicDetailBotStr,
                buttonText        = basicButtonStr,
                enabled           = false,           // Always disabled for MVP
                starCount         = 2,
                features          = basicFeatures,
                comingSoonBadge   = comingSoonBadge
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Holds a plan feature string plus whether it should show a "coming soon" badge. */
data class PlanFeature(val text: String, val comingSoon: Boolean = false)

@Composable
fun PlanCard(
    title: String,
    subtitle: String,
    price: String,
    priceDetailTop: String? = null,
    priceDetailBottom: String? = null,
    buttonText: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    starCount: Int = 1,
    features: List<PlanFeature>,
    comingSoonBadge: String = "",
    isCurrentPlan: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(starCount) {
                        Icon(
                            painter = painterResource(Res.drawable.star),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = price,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (priceDetailTop != null || priceDetailBottom != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (priceDetailTop != null) {
                                Text(
                                    text = priceDetailTop,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (priceDetailBottom != null) {
                                Text(
                                    text = priceDetailBottom,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (isCurrentPlan) {
                    Button(
                        onClick  = {},
                        enabled  = false,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape    = RoundedCornerShape(8.dp),
                        colors   = ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            disabledContentColor   = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(text = buttonText, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    OutlinedButton(
                        enabled  = enabled,
                        onClick  = onClick,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape    = RoundedCornerShape(8.dp),
                        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(text = buttonText, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                features.forEach { feature ->
                    FeatureRow(
                        text = feature.text,
                        comingSoon = feature.comingSoon,
                        comingSoonBadge = comingSoonBadge
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureRow(
    text: String,
    comingSoon: Boolean = false,
    comingSoonBadge: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(Res.drawable.check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp).padding(top = 2.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (comingSoon)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
            if (comingSoon && comingSoonBadge.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = comingSoonBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}