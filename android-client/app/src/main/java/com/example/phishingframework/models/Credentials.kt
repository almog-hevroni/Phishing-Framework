package com.example.phishingframework.models

data class Credentials(
    val id: String,
    val password: String,
    val code: String = ""
)