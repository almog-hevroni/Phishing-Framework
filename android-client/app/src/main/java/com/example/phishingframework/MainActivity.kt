package com.example.phishingframework

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var overlayRequestLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // רישום ה-ActivityResult לטיפול בתוצאת הבקשה להרשאת Overlay
        overlayRequestLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // אחרי שהמשתמש חזר מההגדרות, בודקים שוב
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    "אין הרשאה להציג מעל אפליקציות אחרות",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // אם אין הרשאה למערכת להציג Overlay → נשלח את המשתמש למסך ההגדרות
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayRequestLauncher.launch(intent)
        }

        // אופציונלי: להפנות גם להפעלת AccessibilityService
        // val accIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        // startActivity(accIntent)
    }
}
