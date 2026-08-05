package com.example.myapplication.localplayer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Полноэкранный режим плеера: прячем статус-бар и навигацию на всё время, пока экран плеера жив.
 * Свайп от края показывает их временно (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE) и они сами уезжают.
 *
 * Вызывать из экрана с видео, а не из Activity: список эпизодов и PiP должны остаться обычными,
 * поэтому режим включается/снимается ровно на время показа кадра ([enabled]).
 */
@Composable
fun ImmersivePlayerWindow(enabled: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            if (enabled) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
