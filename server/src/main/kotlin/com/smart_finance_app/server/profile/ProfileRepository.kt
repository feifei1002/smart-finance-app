package com.smart_finance_app.server.profile

import com.smart_finance_app.server.Database
import java.util.UUID
import kotlin.use

/**
 * Used by the "auth-jwt" validate block so tokens belonging to a deleted user are
 * rejected on every authenticated route, even before the JWT itself expires.
 * Primary-key lookup, so it's cheap to run per request.
 */
internal fun userExists(userId: UUID): Boolean =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT 1 FROM users WHERE id = ?").use { statement ->
            statement.setObject(1, userId)
            statement.executeQuery().use { it.next() }
        }
    }

internal fun getProfile(userId: UUID): ProfileResponse? =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT full_name, email
                FROM users
                WHERE id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)

            statement.executeQuery().use { result ->
                if (!result.next()) {
                    null
                } else {
                    ProfileResponse(
                        fullName = result.getString("full_name"),
                        email = result.getString("email")
                    )
                }
            }
        }
    }

internal fun getProfileWithPasswordHash(userId: UUID): ProfileWithPasswordHash? =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT full_name, email, password_hash
                FROM users
                WHERE id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)

            statement.executeQuery().use { result ->
                if (!result.next()) {
                    null
                } else {
                    ProfileWithPasswordHash(
                        fullName = result.getString("full_name"),
                        email = result.getString("email"),
                        passwordHash = result.getString("password_hash")
                    )
                }
            }
        }
    }

internal fun updateProfile(userId: UUID, fullName: String, email: String): ProfileResponse =
    Database.dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                    UPDATE users
                    SET full_name = ?, email = ?
                    WHERE id = ?
                    RETURNING full_name, email
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, fullName)
                statement.setString(2, email)
                statement.setObject(3, userId)

                statement.executeQuery().use { result ->
                    check(result.next()) { "User not found" }

                    val profile = ProfileResponse(
                        fullName = result.getString("full_name"),
                        email = result.getString("email")
                    )

                    connection.commit()
                    profile
                }
            }
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }

internal fun getPasswordHash(userId: UUID): String? =
    Database.dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT password_hash FROM users WHERE id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, userId)

            statement.executeQuery().use { result ->
                if (result.next()) result.getString("password_hash") else null
            }
        }
    }
