package com.example.phishingframework

import android.app.Activity
import android.os.Bundle

class CaughtActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // קישור ל־layout שמציג את ההודעה
        setContentView(R.layout.caught_activity)
    }
}
