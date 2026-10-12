package com.smart_finance_app.server.banking.config

import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

//internal val bankingHttpClient = OkHttpClient()

internal val bankingHttpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .callTimeout(25, TimeUnit.SECONDS)
    .build()
internal val bankingLogger = LoggerFactory.getLogger("Banking")
internal val bankingGson = Gson()