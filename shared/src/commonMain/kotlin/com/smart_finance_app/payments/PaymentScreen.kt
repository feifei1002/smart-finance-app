package com.smart_finance_app.payments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smart_finance_app.AppErrorMessage
import com.smart_finance_app.AppPageHeader
import com.smart_finance_app.AppScreenContainer
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import smart_finance_app.shared.generated.resources.Res
import smart_finance_app.shared.generated.resources.american_express
import smart_finance_app.shared.generated.resources.credit_card
import smart_finance_app.shared.generated.resources.jcb
import smart_finance_app.shared.generated.resources.mastercard
import smart_finance_app.shared.generated.resources.star
import smart_finance_app.shared.generated.resources.unionpay
import smart_finance_app.shared.generated.resources.visa
import com.smart_finance_app.StringKey
import com.smart_finance_app.appStringResource

@Composable
fun PaymentScreen(
    paymentDetails: PaymentDetailsResponse?,
    isLoading: Boolean,
    errorMessage: String?,
    invoices: List<BillingInvoiceResponse>,
    invoicesLoading: Boolean,
    invoicesError: String?,
    billingAddress: BillingAddressResponse?,
    billingAddressLoading: Boolean,
    billingAddressError: String?,
    fullName: String,
    email: String,
    isOpeningPortal: Boolean,
    onChangePaymentCard: () -> Unit,
    onViewPlans: () -> Unit,
    onBack: () -> Unit
) {
    val status = paymentDetails?.subscriptionStatus ?: "free"
    val isPaidPlan = status.equals("basic", ignoreCase = true) ||
            status.equals("pro", ignoreCase = true)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp

        AppScreenContainer(
            compact = compact,
            maxWidth = if (compact) 560.dp else 760.dp
        ) {
            AppPageHeader(
                title = appStringResource(StringKey.PAYMENT_TITLE),
                subtitle = appStringResource(StringKey.PAYMENT_SUBTITLE),
                onBack = onBack,
                compact = compact
            )

            if (isLoading) {
                LoadingPaymentCard()
            } else {
                PaymentDetailsCard(
                    paymentDetails = paymentDetails,
                    isPaidPlan = isPaidPlan,
                    isOpeningPortal = isOpeningPortal,
                    onChangePaymentCard = onChangePaymentCard,
                    onViewPlans = onViewPlans
                )
                BillingHistorySection(
                    invoices = invoices,
                    isLoading = invoicesLoading,
                    errorMessage = invoicesError
                )
                BillingInformationSection(
                    billingInformation = billingAddress,
                    fallbackFullName = fullName,
                    fallbackEmail = email,
                    isLoading = billingAddressLoading,
                    errorMessage = billingAddressError,
                    onUpdateBillingInformation = onChangePaymentCard
                )
                CancelPlanSection(
                    isPaidPlan = isPaidPlan,
                    isOpeningPortal = isOpeningPortal,
                    onCancelPlan = onChangePaymentCard
                )
            }

            errorMessage?.let {
                AppErrorMessage(it)
            }
        }
    }
}

@Composable
private fun LoadingPaymentCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp))
            Text(appStringResource(StringKey.PAYMENT_LOADING))
        }
    }
}

@Composable
private fun PaymentDetailsCard(
    paymentDetails: PaymentDetailsResponse?,
    isPaidPlan: Boolean,
    isOpeningPortal: Boolean,
    onChangePaymentCard: () -> Unit,
    onViewPlans: () -> Unit
) {
    val status = paymentDetails?.subscriptionStatus ?: "free"
    val card = paymentDetails?.card

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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val starCount = if (isPaidPlan) 2 else 1

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = appStringResource(StringKey.PAYMENT_CURRENT_PLAN),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                PlanStars(starCount)
                Text(
                    text = status.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider()

            if (!isPaidPlan) {
                NoCardContent()
            } else if (card == null) {
                Text(
                    text = appStringResource(StringKey.PAYMENT_NO_CARD_FOUND),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SavedCardContent(card)
            }

            if (isPaidPlan) {
                Button(
                    enabled = isPaidPlan && !isOpeningPortal,
                    onClick = onChangePaymentCard,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isOpeningPortal) appStringResource(StringKey.PAYMENT_UPDATING_BUTTON)
                        else appStringResource(StringKey.PAYMENT_UPDATE_BUTTON),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onViewPlans,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = appStringResource(StringKey.PAYMENT_CHANGE_PLAN),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                        )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = appStringResource(StringKey.PAYMENT_SUBSCRIBE_PROMPT),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onViewPlans,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            appStringResource(StringKey.PAYMENT_VIEW_PLANS),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoCardContent() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = appStringResource(StringKey.PAYMENT_NO_CARD_TITLE),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = appStringResource(StringKey.PAYMENT_NO_CARD_BODY),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SavedCardContent(card: PaymentCardResponse) {
    val endingLabel  = appStringResource(StringKey.PAYMENT_CARD_ENDING)
    val expiresLabel = appStringResource(StringKey.PAYMENT_CARD_EXPIRES)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(cardBrandDrawable(card.brand)),
            contentDescription = "${card.brand} card logo",
            modifier = Modifier.size(width = 56.dp, height = 34.dp).clip(RoundedCornerShape(8.dp))
        )
        Column {
            Text(
                text = "${cardBrandLabel(card.brand)} $endingLabel ${card.last4}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$expiresLabel ${card.expMonth.toString().padStart(2, '0')}/${card.expYear}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlanStars(starCount: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(starCount) {
            Icon(
                painter = painterResource(Res.drawable.star),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun BillingHistorySection(
    invoices: List<BillingInvoiceResponse>,
    isLoading: Boolean,
    errorMessage: String?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = appStringResource(StringKey.PAYMENT_HISTORY_TITLE),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                errorMessage != null -> AppErrorMessage(errorMessage)
                invoices.isEmpty() -> Text(
                    text = appStringResource(StringKey.PAYMENT_HISTORY_EMPTY),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> invoices.forEach { InvoiceRow(it) }
            }
        }
    }
}

@Composable
private fun BillingInformationSection(
    billingInformation: BillingAddressResponse?,
    fallbackFullName: String,
    fallbackEmail: String,
    isLoading: Boolean,
    errorMessage: String?,
    onUpdateBillingInformation: () -> Unit
) {
    val notProvided = appStringResource(StringKey.PAYMENT_BILLING_NOT_PROVIDED)
    val address = listOfNotNull(
        billingInformation?.line1,
        billingInformation?.line2,
        billingInformation?.city,
        billingInformation?.state,
        billingInformation?.postalCode,
        billingInformation?.country
    ).joinToString(", ")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = appStringResource(StringKey.PAYMENT_BILLING_INFO_TITLE),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                errorMessage != null -> AppErrorMessage(errorMessage)
                else -> {
                    BillingInfoRow(
                        label = appStringResource(StringKey.PAYMENT_BILLING_NAME),
                        value = billingInformation?.name ?: fallbackFullName.ifBlank { notProvided }
                    )
                    BillingInfoRow(
                        label = appStringResource(StringKey.PAYMENT_BILLING_EMAIL),
                        value = billingInformation?.email ?: fallbackEmail.ifBlank { notProvided }
                    )
                    BillingInfoRow(
                        label = appStringResource(StringKey.PAYMENT_BILLING_ADDRESS),
                        value = address.ifBlank { notProvided }
                    )
                }
            }
            OutlinedButton(
                onClick = onUpdateBillingInformation,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    appStringResource(StringKey.PAYMENT_BILLING_UPDATE),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BillingInfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun InvoiceRow(invoice: BillingInvoiceResponse) {
    val fallbackLabel  = appStringResource(StringKey.PAYMENT_INVOICE_FALLBACK)
    val unknownStatus  = appStringResource(StringKey.PAYMENT_INVOICE_UNKNOWN_STATUS)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = invoice.number ?: fallbackLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = invoice.createdAt.take(10),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${invoice.currency} ${invoice.amountPaid}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = invoice.status ?: unknownStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CancelPlanSection(
    isPaidPlan: Boolean,
    isOpeningPortal: Boolean,
    onCancelPlan: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isPaidPlan) appStringResource(StringKey.PAYMENT_CANCEL_PAID_PROMPT)
                else appStringResource(StringKey.PAYMENT_CANCEL_FREE_PROMPT),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                enabled = isPaidPlan && !isOpeningPortal,
                onClick = onCancelPlan,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isOpeningPortal) appStringResource(StringKey.PAYMENT_CANCEL_OPENING)
                    else appStringResource(StringKey.PAYMENT_CANCEL_BUTTON),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun cardBrandDrawable(brand: String): DrawableResource {
    return when (brand.lowercase()) {
        "visa"                        -> Res.drawable.visa
        "mastercard"                  -> Res.drawable.mastercard
        "amex", "american express"    -> Res.drawable.american_express
        "jcb"                         -> Res.drawable.jcb
        "unionpay"                    -> Res.drawable.unionpay
        else                          -> Res.drawable.credit_card
    }
}

private fun cardBrandLabel(brand: String): String {
    return when (brand.lowercase()) {
        "visa"                        -> "VISA"
        "mastercard"                  -> "Mastercard"
        "amex", "american express"    -> "American Express"
        "discover"                    -> "Discover"
        "jcb"                         -> "JCB"
        "diners", "diners club"       -> "Diners Club"
        "unionpay", "union pay"       -> "UnionPay"
        else -> brand.split(" ", "-", "_")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }
}