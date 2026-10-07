package com.kryogames.app

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import java.util.Locale
import kotlin.math.abs

/**
 * Handheld pads reach the activity as keys and stick axes. Android WebView
 * often never surfaces that hardware through the Gamepad API, so pages that
 * only listen for a browser gamepad see nothing. This state is published to
 * the page as one standard gamepad.
 */
internal class WebGamepadState {
    private val keyButtons = FloatArray(BUTTONS)
    private val hatButtons = FloatArray(BUTTONS)
    private val axes = FloatArray(4)
    private val held = HashSet<Int>()

    @Volatile
    var snapshot: String = ""
        private set

    init {
        publish()
    }

    fun onKey(event: KeyEvent) = onKeyCode(event.keyCode, event.action, event.deviceId)

    internal fun onKeyCode(keyCode: Int, action: Int, deviceId: Int) {
        // Stick deflection is also replayed as a virtual d-pad tap. Those keys
        // must not become a second button press on top of the stick axes.
        if (deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD) return
        if (buttonIndex(keyCode) == null) return
        when (action) {
            KeyEvent.ACTION_DOWN -> held.add(keyCode)
            KeyEvent.ACTION_UP -> held.remove(keyCode)
            else -> return
        }
        keyButtons.fill(0f)
        for (code in held) {
            val index = buttonIndex(code) ?: continue
            keyButtons[index] = 1f
        }
        publish()
    }

    fun onMotion(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_MOVE) return
        val fromPad = event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_DPAD)
        if (!fromPad) return
        fun axis(code: Int): Float {
            val value = event.getAxisValue(code)
            val flat = event.device?.getMotionRange(code, event.source)?.flat ?: 0f
            return deflected(value, flat)
        }
        fun wider(primary: Float, secondary: Float) = if (abs(primary) >= abs(secondary)) primary else secondary
        onAxes(
            axis(MotionEvent.AXIS_X),
            axis(MotionEvent.AXIS_Y),
            wider(axis(MotionEvent.AXIS_Z), axis(MotionEvent.AXIS_RX)),
            wider(axis(MotionEvent.AXIS_RZ), axis(MotionEvent.AXIS_RY)),
            event.getAxisValue(MotionEvent.AXIS_HAT_X),
            event.getAxisValue(MotionEvent.AXIS_HAT_Y),
            alreadyDeflected = true,
        )
    }

    fun onAxes(
        x: Float,
        y: Float,
        rightX: Float,
        rightY: Float,
        hatX: Float,
        hatY: Float,
        alreadyDeflected: Boolean = false,
    ) {
        axes[0] = if (alreadyDeflected) x else deflected(x, 0f)
        axes[1] = if (alreadyDeflected) y else deflected(y, 0f)
        axes[2] = if (alreadyDeflected) rightX else deflected(rightX, 0f)
        axes[3] = if (alreadyDeflected) rightY else deflected(rightY, 0f)
        hatButtons.fill(0f)
        if (hatY < -0.5f) hatButtons[12] = 1f
        if (hatY > 0.5f) hatButtons[13] = 1f
        if (hatX < -0.5f) hatButtons[14] = 1f
        if (hatX > 0.5f) hatButtons[15] = 1f
        publish()
    }

    fun clear() {
        held.clear()
        keyButtons.fill(0f)
        hatButtons.fill(0f)
        axes.fill(0f)
        publish()
    }

    private fun publish() {
        snapshot = buildString {
            append("{\"b\":[")
            for (i in 0 until BUTTONS) {
                if (i > 0) append(',')
                append(if (maxOf(keyButtons[i], hatButtons[i]) > 0f) '1' else '0')
            }
            append("],\"a\":[")
            for (i in axes.indices) {
                if (i > 0) append(',')
                append(String.format(Locale.US, "%.4f", axes[i]))
            }
            append("]}")
        }
    }
}

internal fun buttonIndex(keyCode: Int): Int? = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> 0
    KeyEvent.KEYCODE_BUTTON_B -> 1
    KeyEvent.KEYCODE_BUTTON_X -> 2
    KeyEvent.KEYCODE_BUTTON_Y -> 3
    KeyEvent.KEYCODE_BUTTON_L1 -> 4
    KeyEvent.KEYCODE_BUTTON_R1 -> 5
    KeyEvent.KEYCODE_BUTTON_L2 -> 6
    KeyEvent.KEYCODE_BUTTON_R2 -> 7
    KeyEvent.KEYCODE_BUTTON_SELECT -> 8
    KeyEvent.KEYCODE_BUTTON_START -> 9
    KeyEvent.KEYCODE_BUTTON_THUMBL -> 10
    KeyEvent.KEYCODE_BUTTON_THUMBR -> 11
    KeyEvent.KEYCODE_DPAD_UP -> 12
    KeyEvent.KEYCODE_DPAD_DOWN -> 13
    KeyEvent.KEYCODE_DPAD_LEFT -> 14
    KeyEvent.KEYCODE_DPAD_RIGHT -> 15
    else -> null
}

internal fun deflected(value: Float, flat: Float): Float {
    val dead = if (flat in 0.05f..0.35f) flat else 0.25f
    return if (abs(value) > dead) value else 0f
}

class WebGamepadBridge {
    private val state = WebGamepadState()

    @JavascriptInterface
    fun snapshot(): String = state.snapshot

    fun onKey(event: KeyEvent) = state.onKey(event)
    fun onMotion(event: MotionEvent) = state.onMotion(event)
    fun clear() = state.clear()
}

private const val BUTTONS = 17

/** Installed in the web game page so q5play's contro follows [WebGamepadBridge]. */
internal const val WEB_GAMEPAD_BOOT = """
(function () {
  if (window.__kryoPad) return;
  window.__kryoPad = true;
  var pad = {
    id: "KryoGames (Vendor: 0001 Product: 0001)",
    index: 0,
    connected: true,
    mapping: "standard",
    timestamp: 0,
    axes: [0, 0, 0, 0],
    buttons: []
  };
  for (var i = 0; i < 17; i++) pad.buttons.push({ pressed: false, touched: false, value: 0 });
  var nativePads = navigator.getGamepads && navigator.getGamepads.bind(navigator);
  function merged() {
    var list = nativePads ? nativePads() : [];
    for (var i = 0; i < list.length; i++) if (list[i]) return list;
    return [pad, null, null, null];
  }
  try {
    Object.defineProperty(navigator, "getGamepads", { configurable: true, value: merged });
  } catch (e) {
    try { navigator.getGamepads = merged; } catch (e2) {}
  }
  function apply(text) {
    var state = JSON.parse(text);
    var b = state.b;
    var a = state.a;
    for (var i = 0; i < b.length && i < pad.buttons.length; i++) {
      var on = b[i] > 0;
      pad.buttons[i].pressed = on;
      pad.buttons[i].touched = on;
      pad.buttons[i].value = on ? 1 : 0;
    }
    for (var n = 0; n < a.length && n < pad.axes.length; n++) pad.axes[n] = a[n];
    pad.timestamp = performance.now();
  }
  function loosen() {
    var existing = window.contro;
    if (!existing) return;
    try {
      Object.defineProperty(window, "contro", {
        value: existing,
        configurable: true,
        writable: true,
        enumerable: true
      });
    } catch (e) {}
  }
  function tick() {
    try {
      if (window.KryoPad) apply(window.KryoPad.snapshot());
    } catch (e) {}
    var current = window.contro;
    var linked = current && current.isMock === false && current.gamepad === pad;
    if (!linked) {
      loosen();
      try {
        var ev = new Event("gamepadconnected");
        Object.defineProperty(ev, "gamepad", { value: pad });
        window.dispatchEvent(ev);
      } catch (e) {}
      var real = window.contros && window.contros[0];
      if (real && real.isMock === false && window.contro !== real) {
        try {
          Object.defineProperty(window, "contro", {
            value: real,
            configurable: true,
            writable: true,
            enumerable: true
          });
        } catch (e) {}
      }
    }
    requestAnimationFrame(tick);
  }
  tick();
})();
"""
