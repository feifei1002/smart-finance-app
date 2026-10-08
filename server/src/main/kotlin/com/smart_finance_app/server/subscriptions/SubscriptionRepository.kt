package com.smart_finance_app.server.subscriptions

import com.smart_finance_app.server.Database
import java.util.UUID

internal fun getSubscriptionUser(userId: UUID): SubscriptionUser? =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT full_name, email, stripe_customer_id
                FROM users WHERE id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                if (!result.next()) null else SubscriptionUser(
                    name = result.getString("full_name"),
                    email = result.getString("email"),
                    stripeCustomerId = result.getString("stripe_customer_id")
                )
            }
        }
    }

internal fun getSubscriptionStatus(userId: UUID): String =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
        "SELECT subscription_status FROM users WHERE id = ?"
        ).use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { result ->
                if (result.next()) result.getString("subscription_status") else "free"
            }
        }
    }

internal fun saveStripeCustomerId(userId: UUID, customerId: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    UPDATE users SET stripe_customer_id = ? WHERE id = ?
                """.trimIndent()
            ).use {
                it.setString(1, customerId)
                it.setObject(2, userId)
                it.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }
}

internal fun markSubscriptionBasic(userId: UUID, customerId: String?, subscriptionId: String?) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    UPDATE users
                    SET subscription_status = 'basic',
                        stripe_customer_id = COALESCE(?, stripe_customer_id),
                        stripe_subscription_id = ?,
                        subscription_updated_at = now()
                    WHERE id = ?
                """.trimIndent()
            ).use {
                it.setString(1, customerId)
                it.setString(2, subscriptionId)
                it.setObject(3, userId)
                it.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }
}

internal fun markSubscriptionFreeBySubscriptionId(subscriptionId: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    UPDATE users SET subscription_status = 'free', subscription_updated_at = now()
                    WHERE stripe_subscription_id = ?
                """.trimIndent()
            ).use {
                it.setString(1, subscriptionId)
                it.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }
}

internal fun markSubscriptionFreeByCustomerId(customerId: String) {
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    UPDATE users
                    SET subscription_status = 'free',
                        subscription_updated_at = now()
                    WHERE stripe_customer_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, customerId)
                statement.executeUpdate()
            }

            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}
internal fun getStripeCustomerId(userId: UUID): String? {
    return Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT stripe_customer_id
                FROM users
                WHERE id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)

            statement.executeQuery().use { result ->
                if (result.next()) {
                    result.getString("stripe_customer_id")
                } else {
                    null
                }
            }
        }
    }
}
