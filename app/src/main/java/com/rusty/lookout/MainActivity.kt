package com.rusty.lookout

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager

/**
 * Lookout as an app: left/right flips pages and pauses rotation for 5 minutes;
 * OK pins the current page (remembered across restarts) until pressed again;
 * up/down on the Live page picks the Lichess TV game type.
 */
class MainActivity : Activity() {

    private lateinit var view: LookoutView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // It's a wall display: keep the screen on while it's open.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = LookoutView(this, screensaver = false)
        setContentView(view)
    }

    override fun onResume() {
        super.onResume()
        view.start()
    }

    override fun onPause() {
        view.stop()
        super.onPause()
    }

    override fun onDestroy() {
        view.shutdown()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { view.step(1); true }
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { view.step(-1); true }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { view.togglePin(); true }
        KeyEvent.KEYCODE_DPAD_UP -> view.changeTvChannel(-1) || super.onKeyDown(keyCode, event)
        KeyEvent.KEYCODE_DPAD_DOWN -> view.changeTvChannel(1) || super.onKeyDown(keyCode, event)
        else -> super.onKeyDown(keyCode, event)
    }
}
