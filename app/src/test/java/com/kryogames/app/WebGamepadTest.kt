package com.kryogames.app

import android.view.KeyCharacterMap
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class WebGamepadTest {
    private val idle =
        """{"b":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],"a":[0.0000,0.0000,0.0000,0.0000]}"""

    @Test fun faceAndDpadLandOnStandardButtons() {
        val pad = WebGamepadState()
        assertEquals(idle, pad.snapshot)
        pad.onKeyCode(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_DOWN, deviceId = 5)
        pad.onKeyCode(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, deviceId = 5)
        assertEquals(
            """{"b":[1,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0],"a":[0.0000,0.0000,0.0000,0.0000]}""",
            pad.snapshot,
        )
        pad.onKeyCode(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_UP, deviceId = 5)
        assertEquals(
            """{"b":[0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0],"a":[0.0000,0.0000,0.0000,0.0000]}""",
            pad.snapshot,
        )
    }

    @Test fun virtualStickTapsAreNotButtons() {
        val pad = WebGamepadState()
        pad.onKeyCode(
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.ACTION_DOWN,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
        )
        assertEquals(idle, pad.snapshot)
    }

    @Test fun stickUsesDeadzoneAndHatBecomesDpad() {
        val pad = WebGamepadState()
        pad.onAxes(0.2f, -0.1f, 0f, 0f, 0f, 0f)
        assertEquals(idle, pad.snapshot)
        pad.onAxes(0.8f, -1f, 0.4f, 0f, 0f, 1f)
        assertEquals(
            """{"b":[0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0],"a":[0.8000,-1.0000,0.4000,0.0000]}""",
            pad.snapshot,
        )
        assertEquals(0f, deflected(0.15f, 0.2f))
        assertEquals(0.3f, deflected(0.3f, 0.2f))
    }

    @Test fun pauseReleasesHeldInput() {
        val pad = WebGamepadState()
        pad.onKeyCode(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.ACTION_DOWN, deviceId = 5)
        pad.onAxes(1f, 0f, 0f, 0f, -1f, 0f)
        pad.clear()
        assertEquals(idle, pad.snapshot)
    }
}
