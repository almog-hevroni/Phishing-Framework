package com.example.phishingframework.activities

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView
import android.util.Log
import com.example.phishingframework.R

class CaughtActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i("CaughtActivity", "נפלת בפח - המסך מוצג")

        window.setBackgroundDrawable(ColorDrawable(Color.argb(200, 128, 128, 128)))

        setContentView(R.layout.caught_activity)

        val messageText = findViewById<TextView>(R.id.caught_message)

        // Create blinking animation
        val blinkAnimation = AlphaAnimation(0.0f, 1.0f).apply {
            duration = 500 // חצי שנייה
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }

        messageText.startAnimation(blinkAnimation)

        handler.postDelayed({
            Log.i("CaughtActivity", "סוגר את האפליקציה וחוזר למסך הבית")

            messageText.clearAnimation()

            // Return to home screen
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)

            // Close all Activities of the application
            finishAffinity()

            // Exit the process (after returning to home screen)
            handler.postDelayed({
                System.exit(0)
            }, 500)

        }, 3000)
    }

    override fun onBackPressed() {
        // Prevent closing by pressing Back
        // User must see the message
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("CaughtActivity", "CaughtActivity נסגר")
    }
}