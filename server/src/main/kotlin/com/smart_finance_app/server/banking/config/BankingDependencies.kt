package com.smart_finance_app.server.banking.config

import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory

internal val bankingHttpClient = OkHttpClient()
internal val bankingLogger = LoggerFactory.getLogger("Banking")
internal val bankingGson = Gson()