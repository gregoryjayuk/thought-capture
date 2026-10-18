package com.greggaynor.thoughtcapture

import android.content.Context

object CaptureState {
    private const val PREFS = "capture_state"
    private const val KEY_LISTENING = "is_listening"

    fun isListening(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LISTENING, false)
    }

    fun setListening(context: Context, listening: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LISTENING, listening)
            .apply()
    }
}
