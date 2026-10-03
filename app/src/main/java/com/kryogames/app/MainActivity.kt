package com.kryogames.app

import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ControllerActivity() {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // The library and in-app folder browser are controller-only.
        if (ev.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) return true
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { KryoTheme { StarterApp(controllerActions) } }
    }
}
