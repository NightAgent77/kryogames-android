package com.kryogames.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

internal fun shouldDriveFileApp(foregroundPackage: String): Boolean {
    val pkg = foregroundPackage.lowercase()
    return pkg == "com.android.documentsui" || pkg == "com.google.android.documentsui" || pkg.contains("documentsui")
}

/**
 * While the system file app is open, turns the stick and A/B into the D-pad and Back
 * that file app already understands. Joystick events are only captured during that visit.
 */
class FilePickerControls : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var pickerInFront = false
    private var watchingStick = false
    private var heldDirection: Int? = null
    private val repeater = object : Runnable {
        override fun run() {
            heldDirection?.let {
                sendDirection(it)
                handler.postDelayed(this, 155L)
            }
        }
    }

    override fun onServiceConnected() {
        val info = serviceInfo ?: return
        val flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        if (info.flags != flags) {
            info.flags = flags
            serviceInfo = info
        }
    }

    override fun onDestroy() {
        heldDirection = null
        handler.removeCallbacks(repeater)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        setPickerInFront(shouldDriveFileApp(event.packageName?.toString().orEmpty()))
    }

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!pickerInFront) return false
        val confirm = event.keyCode == KeyEvent.KEYCODE_BUTTON_A || event.keyCode == KeyEvent.KEYCODE_BUTTON_START
        val back = event.keyCode == KeyEvent.KEYCODE_BUTTON_B
        if (!confirm && !back) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (confirm) confirmFocused() else performGlobalAction(GLOBAL_ACTION_BACK)
        }
        return true
    }

    override fun onMotionEvent(event: MotionEvent) {
        if (!pickerInFront || !event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE) return
        fun axis(code: Int): Float {
            val value = event.getAxisValue(code)
            val deadZone = maxOf(0.55f, event.device?.getMotionRange(code, event.source)?.flat ?: 0f)
            return if (abs(value) > deadZone) value else 0f
        }
        val x = axis(MotionEvent.AXIS_X)
        val y = axis(MotionEvent.AXIS_Y)
        val direction = when {
            x == 0f && y == 0f -> null
            abs(x) > abs(y) -> if (x < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            else -> if (y < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
        }
        if (direction == heldDirection) return
        handler.removeCallbacks(repeater)
        heldDirection = direction
        if (direction != null) {
            sendDirection(direction)
            handler.postDelayed(repeater, 300L)
        }
    }

    private fun setPickerInFront(inFront: Boolean) {
        pickerInFront = inFront
        if (!inFront) {
            heldDirection = null
            handler.removeCallbacks(repeater)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || watchingStick == inFront) return
        val info = serviceInfo ?: return
        info.motionEventSources = if (inFront) InputDevice.SOURCE_JOYSTICK else 0
        serviceInfo = info
        watchingStick = inFront
    }

    private fun confirmFocused() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            performGlobalAction(GLOBAL_ACTION_DPAD_CENTER)
            return
        }
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        focused?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun sendDirection(code: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val action = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> GLOBAL_ACTION_DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> GLOBAL_ACTION_DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> GLOBAL_ACTION_DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> GLOBAL_ACTION_DPAD_RIGHT
            else -> return
        }
        performGlobalAction(action)
    }
}
