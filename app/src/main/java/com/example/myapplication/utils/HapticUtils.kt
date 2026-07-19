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
            // Лёгкий «щелчок деления» — для тиков слайдера (пересечение целого значения).
            "tick" -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            // Тяжёлый отклик — синхронизирован с визуальным сквошем (releaseBounce).
            "heavy" -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            else -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    } else {
        @Suppress("DEPRECATION")
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}
