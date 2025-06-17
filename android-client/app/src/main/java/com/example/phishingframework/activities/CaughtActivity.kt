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

        // רקע אפור כהה עם שקיפות
        window.setBackgroundDrawable(ColorDrawable(Color.argb(200, 128, 128, 128)))

        // קישור ל־layout שמציג את ההודעה
        setContentView(R.layout.caught_activity)

        val messageText = findViewById<TextView>(R.id.caught_message)

        // יצירת אנימציית הבהוב
        val blinkAnimation = AlphaAnimation(0.0f, 1.0f).apply {
            duration = 500 // חצי שנייה
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }

        // התחלת האנימציה
        messageText.startAnimation(blinkAnimation)

        // אחרי 3 שניות - סגירת האפליקציה וחזרה למסך הבית
        handler.postDelayed({
            Log.i("CaughtActivity", "סוגר את האפליקציה וחוזר למסך הבית")

            // עצירת האנימציה
            messageText.clearAnimation()

            // חזרה למסך הבית
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)

            // סגירת כל ה-Activities של האפליקציה
            finishAffinity()

            // יציאה מהתהליך (לאחר חזרה למסך הבית)
            handler.postDelayed({
                System.exit(0)
            }, 500) // חצי שנייה נוספת לוודא שמסך הבית נטען

        }, 3000) // 3 שניות
    }

    override fun onBackPressed() {
        // מונעים סגירה בלחיצה על Back
        // המשתמש חייב לראות את ההודעה
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("CaughtActivity", "CaughtActivity נסגר")
    }
}