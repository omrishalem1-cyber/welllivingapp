package com.wellliving.attention

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ActiveSession(
    val activityId: String,
    val startMs: Long,
    val targetMin: Int,
    val distractions: Int,
) {
    val endMs get() = startMs + targetMin * 60_000L
}

/** The running session, persisted so it survives the app being closed. */
object ActiveSessionState {
    private lateinit var prefs: SharedPreferences
    private val state = MutableStateFlow<ActiveSession?>(null)
    val current: StateFlow<ActiveSession?> = state

    /** Id of the session that just ended and still awaits a focus rating. */
    val pendingRating = MutableStateFlow<String?>(null)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("active_session", Context.MODE_PRIVATE)
        state.value = prefs.getString("activityId", null)?.let {
            ActiveSession(it, prefs.getLong("startMs", 0), prefs.getInt("targetMin", 0), prefs.getInt("distractions", 0))
        }
        pendingRating.value = prefs.getString("pendingRating", null)
    }

    fun set(session: ActiveSession?) {
        prefs.edit().apply {
            if (session == null) {
                remove("activityId")
            } else {
                putString("activityId", session.activityId)
                putLong("startMs", session.startMs)
                putInt("targetMin", session.targetMin)
                putInt("distractions", session.distractions)
            }
        }.apply()
        state.value = session
    }

    fun setPendingRating(id: String?) {
        prefs.edit().putString("pendingRating", id).apply()
        pendingRating.value = id
    }
}
