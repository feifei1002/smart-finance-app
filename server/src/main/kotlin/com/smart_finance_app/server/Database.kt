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

    fun close() {
        if (::hikari.isInitialized) {
            hikari.close()
        }
    }

    private fun requireEnvironment(name: String): String =
        System.getenv(name)
            ?: error("Missing environment variable: $name")
}