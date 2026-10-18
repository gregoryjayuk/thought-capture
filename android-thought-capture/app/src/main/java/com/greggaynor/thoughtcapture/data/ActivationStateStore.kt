package com.greggaynor.thoughtcapture.data

import android.content.Context
import com.greggaynor.thoughtcapture.model.ItemState

class ActivationStateStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun state(itemId: String, defaultValue: ItemState = ItemState.ACTIVE): ItemState {
        val stored = prefs.all[itemId] ?: return defaultValue
        val resolved = when (stored) {
            is String -> runCatching { ItemState.valueOf(stored) }.getOrNull()
            is Boolean -> if (stored) ItemState.ACTIVE else ItemState.INACTIVE
            else -> null
        }
        if (resolved != null && stored !is String) {
            setState(itemId, resolved)
        }
        return resolved ?: defaultValue
    }

    fun setState(itemId: String, state: ItemState) {
        prefs.edit().putString(itemId, state.name).apply()
    }

    private companion object {
        const val PREFS_NAME = "activation_state_store"
    }
}
