package com.rusty.lookout

import android.service.dreams.DreamService

/** Lookout as a screensaver: same display, pages rotate on their own. */
class LookoutDream : DreamService() {

    private lateinit var view: LookoutView

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        view = LookoutView(this, screensaver = true)
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        view.start()
    }

    override fun onDreamingStopped() {
        view.shutdown()
        super.onDreamingStopped()
    }
}
