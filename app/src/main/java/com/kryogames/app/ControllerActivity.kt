package com.kryogames.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.abs

enum class ControllerAction { OPTIONS, SEARCH, PREVIOUS_TAB, NEXT_TAB, MENU }

/** A/Enter activate the ACTUALLY focused Compose control, not a remembered game. */
open class ControllerActivity : ComponentActivity() {
    val controllerActions = MutableSharedFlow<ControllerAction>(
        extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    protected open val useLibraryShortcuts = true
    private val handler = Handler(Looper.getMainLooper())
    private var heldDirection: Int? = null
    private val repeater = object : Runnable {
        override fun run() {
            heldDirection?.let {
                sendDirection(it)
                handler.postDelayed(this, 155L)
            }
        }
    }

    private fun shortcut(keyCode: Int): ControllerAction? = if (useLibraryShortcuts) when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_X -> ControllerAction.OPTIONS
            KeyEvent.KEYCODE_BUTTON_Y -> ControllerAction.SEARCH
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_PAGE_UP -> ControllerAction.PREVIOUS_TAB
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_PAGE_DOWN -> ControllerAction.NEXT_TAB
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_F1 -> ControllerAction.MENU
            else -> null
        } else null
    private fun activateFocusedControl(event: KeyEvent) {
        val mapped = KeyEvent(event.downTime, event.eventTime, event.action,
            KeyEvent.KEYCODE_ENTER, event.repeatCount, event.metaState,
            event.deviceId, event.scanCode, event.flags, InputDevice.SOURCE_KEYBOARD)
        window.superDispatchKeyEvent(mapped)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_A) { activateFocusedControl(event); return true }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) return true
        shortcut(keyCode)?.let { action ->
            if (event.repeatCount == 0) controllerActions.tryEmit(action)
            return true
        }
        // Compose handles physical D-pad, keyboard arrows, Tab and Enter normally.
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_A) { activateFocusedControl(event); return true }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) { onBackPressedDispatcher.onBackPressed(); return true }
        if (shortcut(keyCode) != null) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE)
            return super.dispatchGenericMotionEvent(event)
        fun axis(code: Int): Float {
            val value = event.getAxisValue(code)
            val deadZone = maxOf(0.55f, event.device?.getMotionRange(code, event.source)?.flat ?: 0f)
            return if (abs(value) > deadZone) value else 0f
        }
        val x = listOf(axis(MotionEvent.AXIS_X), axis(MotionEvent.AXIS_HAT_X)).maxBy { abs(it) }
        val y = listOf(axis(MotionEvent.AXIS_Y), axis(MotionEvent.AXIS_HAT_Y)).maxBy { abs(it) }
        val direction = when {
            x == 0f && y == 0f -> null
            abs(x) > abs(y) -> if (x < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            else -> if (y < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
        }
        if (direction != heldDirection) {
            handler.removeCallbacks(repeater)
            heldDirection = direction
            if (direction != null) {
                sendDirection(direction)
                handler.postDelayed(repeater, 300L)
            }
        }
        return true
    }

    private fun sendDirection(code: Int) {
        val now = SystemClock.uptimeMillis()
        window.superDispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        window.superDispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    override fun onPause() {
        heldDirection = null
        handler.removeCallbacks(repeater)
        super.onPause()
    }
}
