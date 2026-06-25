package com.smart_finance_app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform