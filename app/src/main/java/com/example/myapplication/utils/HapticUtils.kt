package com.example.myapplication.utils

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

fun performHaptic(view: View, type: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        when (type) {
            "light" -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            "success" -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            "warning" -> view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            else -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    } else {
        @Suppress("DEPRECATION")
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}
