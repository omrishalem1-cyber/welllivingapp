package com.wellliving.attention

import android.content.Context
import com.wellliving.trainer.Activity
import com.wellliving.trainer.DEFAULT_ACTIVITIES
import com.wellliving.trainer.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Data(val activities: List<Activity>, val sessions: List<Session>)

/** All user data, kept on the device in one JSON file. */
object Store {
    private lateinit var file: File
    private val state = MutableStateFlow(Data(emptyList(), emptyList()))
    val data: StateFlow<Data> = state

    fun init(context: Context) {
        file = File(context.filesDir, "attention.json")
        state.value = if (file.exists()) read(file.readText()) else
            Data(DEFAULT_ACTIVITIES.map { Activity(UUID.randomUUID().toString(), it) }, emptyList()).also(::write)
    }

    fun addActivity(name: String) = update { it.copy(activities = it.activities + Activity(UUID.randomUUID().toString(), name.trim())) }

    fun archiveActivity(id: String) = update { data ->
        data.copy(activities = data.activities.map { if (it.id == id) it.copy(archived = true) else it })
    }

    fun addSession(session: Session) = update { it.copy(sessions = it.sessions + session) }

    fun rateSession(id: String, rating: Int) = update { data ->
        data.copy(sessions = data.sessions.map { if (it.id == id) it.copy(focusRating = rating) else it })
    }

    @Synchronized
    private fun update(change: (Data) -> Data) {
        val next = change(state.value)
        write(next)
        state.value = next
    }

    private fun write(data: Data) {
        val json = JSONObject()
            .put("activities", JSONArray(data.activities.map {
                JSONObject().put("id", it.id).put("name", it.name).put("archived", it.archived)
            }))
            .put("sessions", JSONArray(data.sessions.map {
                JSONObject()
                    .put("id", it.id).put("activityId", it.activityId).put("startMs", it.startMs)
                    .put("targetMin", it.targetMin).put("actualSec", it.actualSec)
                    .put("completed", it.completed).put("distractions", it.distractions)
                    .put("focusRating", it.focusRating ?: JSONObject.NULL)
            }))
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(file)
    }

    private fun read(text: String): Data {
        val json = JSONObject(text)
        val activities = json.getJSONArray("activities").objects().map {
            Activity(it.getString("id"), it.getString("name"), it.optBoolean("archived"))
        }
        val sessions = json.getJSONArray("sessions").objects().map {
            Session(
                id = it.getString("id"),
                activityId = it.getString("activityId"),
                startMs = it.getLong("startMs"),
                targetMin = it.getInt("targetMin"),
                actualSec = it.getInt("actualSec"),
                completed = it.getBoolean("completed"),
                distractions = it.getInt("distractions"),
                focusRating = if (it.isNull("focusRating")) null else it.getInt("focusRating"),
            )
        }
        return Data(activities, sessions)
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
}
