package com.smart_finance_app.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

object Database {
    private lateinit var hikari: HikariDataSource

    val dataSource: DataSource
        get() = hikari

    fun connect() {
        val config = HikariConfig().apply {
            jdbcUrl = requireEnvironment("DB_JDBC_URL")
            username = requireEnvironment("DB_USER")
            password = requireEnvironment("DB_PASSWORD")

            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 5
            minimumIdle = 0
            connectionTimeout = 10_000
            idleTimeout = 60_000
            isAutoCommit = false
        }

        hikari = HikariDataSource(config)

        hikari.connection.use { connection ->
            connection.prepareStatement("SELECT 1").use { statement ->
                statement.executeQuery().use { result ->
                    check(result.next() && result.getInt(1) == 1)
                }
            }
        }
    }

    fun migrate() {
        dataSource.connection.use { connection ->
            try {
                connection.createStatement().use { stmt ->
                    // Add language column to users if it doesn't exist yet.
                    // DEFAULT 'en' ensures all existing users get English automatically.
                    stmt.execute(
                        """
                        ALTER TABLE users
                        ADD COLUMN IF NOT EXISTS language TEXT NOT NULL DEFAULT 'en'
                            CHECK (language IN ('en', 'es', 'fr', 'nl', 'de', 'it', 'pl', 'zh-TW'))
                        """.trimIndent()
                    )
                }
                connection.commit()
                println("✅ Database migration complete")
            } catch (e: Exception) {
                connection.rollback()
                println("❌ Database migration failed: ${e.message}")
                throw e
            }
        }
    }

    fun close() {
        if (::hikari.isInitialized) {
            hikari.close()
        }
    }

    private fun requireEnvironment(name: String): String =
        System.getenv(name)
            ?: error("Missing environment variable: $name")
}