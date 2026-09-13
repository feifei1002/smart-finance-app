package com.smart_finance_app.payments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
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

    // Resolve all strings at the top so they're ready for PlanCard params
    val freeTitleStr       = appStringResource(StringKey.PLAN_FREE_TITLE)
    val freePriceStr       = appStringResource(StringKey.PLAN_FREE_PRICE)
    val freeDetailTopStr   = appStringResource(StringKey.PLAN_FREE_PRICE_DETAIL_TOP)
    val freeDetailBotStr   = appStringResource(StringKey.PLAN_FREE_PRICE_DETAIL_BOTTOM)
    val freeButtonStr      = if (isFreePlan) appStringResource(StringKey.PLAN_FREE_BUTTON_CURRENT)
    else appStringResource(StringKey.PLAN_FREE_BUTTON)
    val basicTitleStr      = appStringResource(StringKey.PLAN_BASIC_TITLE)
    val basicSubtitleStr   = appStringResource(StringKey.PLAN_BASIC_SUBTITLE)
    val basicPriceStr      = appStringResource(StringKey.PLAN_BASIC_PRICE)
    val basicDetailTopStr  = appStringResource(StringKey.PLAN_BASIC_PRICE_DETAIL_TOP)
    val basicDetailBotStr  = appStringResource(StringKey.PLAN_BASIC_PRICE_DETAIL_BOTTOM)
    val basicButtonStr     = if (isPaidPlan) appStringResource(StringKey.PLAN_BASIC_BUTTON_CURRENT)
    else appStringResource(StringKey.PLAN_BASIC_BUTTON)

    // Feature lists — these are marketing copy specific to the Free/Basic plans.
    // They are intentionally kept as plain strings here since they describe fixed
    // product tiers and are not user-interface navigation labels.
    // You can move them to strings.xml in a future iteration if needed.
    val freeFeatures = listOf(
        "Up to two linked accounts",
        "Access to full visualisation charts in the Home Page",
        "Access to most of the UK and EU banks"
    )
    val basicFeatures = listOf(
        "Unlimited linked accounts",
        "Access to full visualisation charts in the Home Page",
        "Access to most of the UK and EU banks"
    )

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(onClick = onBack) {
                        Text(appStringResource(StringKey.PLAN_BACK))
                    }
                }

                Text(
                    text = appStringResource(StringKey.PLAN_TITLE),
                    modifier = Modifier.fillMaxWidth(),
                    style = if (compact) MaterialTheme.typography.headlineSmall
                    else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                PlanCard(
                    title          = freeTitleStr,
                    subtitle       = "",
                    price          = freePriceStr,
                    priceDetailTop = freeDetailTopStr,
                    priceDetailBottom = freeDetailBotStr,
                    buttonText     = freeButtonStr,
                    enabled        = false,
                    starCount      = 1,
                    features       = freeFeatures
                )

                PlanCard(
                    title          = basicTitleStr,
                    subtitle       = basicSubtitleStr,
                    price          = basicPriceStr,
                    priceDetailTop = basicDetailTopStr,
                    priceDetailBottom = basicDetailBotStr,
                    buttonText     = basicButtonStr,
                    enabled        = !isPaidPlan && !isLoading,
                    onClick        = onSubscribeToBasic,
                    starCount      = 2,
                    features       = basicFeatures
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

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
    features: List<String>
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

                OutlinedButton(
                    enabled = enabled,
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(text = buttonText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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
                features.forEach { feature -> FeatureRow(text = feature) }
            }
        }
    }
}

@Composable
fun FeatureRow(text: String) {
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
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp
        )
    }
}