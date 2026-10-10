package com.smart_finance_app.server

import com.google.gson.Gson
import com.google.gson.JsonObject
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Properties

object GmailApiMailer {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val jsonType = "application/json".toMediaType()

    private val clientId = env("GMAIL_CLIENT_ID")
    private val clientSecret = env("GMAIL_CLIENT_SECRET")
    private val refreshToken = env("GMAIL_REFRESH_TOKEN")
    private val senderEmail = env("GMAIL_SENDER_EMAIL")

    fun send(to: String, subject: String, body: String) {
        val accessToken = fetchAccessToken()
        val rawMessage = createRawMessage(to, subject, body)

        val payload = JsonObject().apply {
            addProperty("raw", rawMessage)
        }

        val request = Request.Builder()
            .url("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(gson.toJson(payload).toRequestBody(jsonType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Gmail API send failed: ${response.code} ${response.body?.string()}")
            }
        }
    }

    private fun fetchAccessToken(): String {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build()

        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Gmail token refresh failed: ${response.code} $responseBody")
            }

            return gson.fromJson(responseBody, JsonObject::class.java)
                .get("access_token")
                .asString
        }
    }

    private fun createRawMessage(to: String, subject: String, body: String): String {
        val session = Session.getDefaultInstance(Properties())
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(senderEmail))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            setSubject(subject)
            setText(body)
        }

        val buffer = ByteArrayOutputStream()
        message.writeTo(buffer)

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(buffer.toByteArray())
    }

    private fun env(name: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: error("Missing environment variable: $name")
}