package com.example.phishingframework.utils

import android.widget.EditText

object ValidationUtils {
    fun validateAndMarkErrors(
        idField: EditText,
        passField: EditText,
        codeField: EditText
    ): Boolean {
        var allOk = true

        if (!Constants.ID_REGEX.matches(idField.text)) {
            idField.error = "ת״ז חייבת להכיל 9 ספרות"
            allOk = false
        }

        if (passField.text.length < 6) {
            passField.error = "סיסמה חייבת להכיל לפחות 6 תווים"
            allOk = false
        }

        if (!Constants.CODE_REGEX.matches(codeField.text)) {
            codeField.error = "קוד: 2 אותיות גדולות + 4 ספרות (AA1234)"
            allOk = false
        }

        return allOk
    }
}