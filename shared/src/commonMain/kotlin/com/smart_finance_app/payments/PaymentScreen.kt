package com.smart_finance_app.payments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 24.dp else 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (compact) 560.dp else 760.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Payments & Billing",
                        style = if (compact) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "View your plan and manage your payment card.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error
                    )
                }
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
            Text("Loading payment details...")
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
                    text = "Current plan",
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
                    text = "No payment card was found for this subscription.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SavedCardContent(card)
            }

            if (isPaidPlan) {
                Button(
                    enabled = isPaidPlan && !isOpeningPortal,
                    onClick = onChangePaymentCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (isOpeningPortal) {
                            "Opening payment settings..."
                        } else {
                            "Update payment"
                        }
                    )
                }
                
                OutlinedButton(
                    onClick = onViewPlans,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Change plan")
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Subscribe to a paid plan to add a payment card.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = onViewPlans,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("View plan options")
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
            text = "No payment card",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "You are currently on the Free plan, so no payment card is being used.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SavedCardContent(card: PaymentCardResponse) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(cardBrandDrawable(card.brand)),
            contentDescription = "${card.brand} card logo",
            modifier = Modifier
                .size(width = 56.dp, height = 34.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Column {
            Text(
                text = "${ cardBrandLabel(card.brand)} ending in ${card.last4}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Expires ${card.expMonth.toString().padStart(2, '0')}/${card.expYear}",
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

private fun cardBrandDrawable(brand: String): DrawableResource {
    return when (brand.lowercase()) {
        "visa" -> Res.drawable.visa
        "mastercard" -> Res.drawable.mastercard
        "amex", "american express" -> Res.drawable.american_express
        "jcb" -> Res.drawable.jcb
        "unionpay" -> Res.drawable.unionpay
        else -> Res.drawable.credit_card
    }
}
private fun cardBrandLabel(brand: String): String {
    return when (brand.lowercase()) {
        "visa" -> "VISA"
        "mastercard" -> "Mastercard"
        "amex", "american express" -> "American Express"
        "discover" -> "Discover"
        "jcb" -> "JCB"
        "diners", "diners club" -> "Diners Club"
        "unionpay", "union pay" -> "UnionPay"
        else -> brand
            .split(" ", "-", "_")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { char -> char.uppercase() }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Transaction history",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }

                errorMessage != null -> {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                invoices.isEmpty() -> {
                    Text(
                        text = "No billing transactions available yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    invoices.forEach { invoice ->
                        InvoiceRow(invoice)
                    }
                }
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
                text = "Billing information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))

                errorMessage != null -> Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error
                )

                else -> {
                    BillingInfoRow(
                        label = "Name",
                        value = billingInformation?.name ?: fallbackFullName.ifBlank { "Not provided" }
                    )

                    BillingInfoRow(
                        label = "Email",
                        value = billingInformation?.email ?: fallbackEmail.ifBlank { "Not provided" }
                    )

                    BillingInfoRow(
                        label = "Address",
                        value = address.ifBlank { "Not provided" }
                    )
                }
            }

            OutlinedButton(
                onClick = onUpdateBillingInformation,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Update billing information")
            }
        }
    }
}

@Composable
private fun BillingInfoRow(label: String, value: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = invoice.number ?: "Invoice",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = invoice.createdAt.take(10),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "${invoice.currency} ${invoice.amountPaid}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = invoice.status ?: "Unknown",
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
                text = if (isPaidPlan) {
                    "You can cancel your paid plan through the secure billing portal."
                } else {
                    "You are currently on the free plan."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                enabled = isPaidPlan && !isOpeningPortal,
                onClick = onCancelPlan,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    if (isOpeningPortal) {
                        "Opening billing portal..."
                    } else {
                        "Cancel plan"
                    }
                )
            }
        }
    }
}