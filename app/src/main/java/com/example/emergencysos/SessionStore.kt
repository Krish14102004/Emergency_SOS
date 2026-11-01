package com.example.emergencysos

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/** Stores a bounded session history on this device. */
class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): Result<MutableList<EmergencyEvent>> = synchronized(STORE_LOCK) {
        try {
        val json = preferences.getString(KEY_EVENTS, null)
        val events = if (json == null) mutableListOf() else decode(JSONArray(json))
        Result.success(events)
        } catch (error: JSONException) {
            Log.e(TAG, "Saved emergency history could not be decoded.", error)
            Result.failure(error)
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "Saved emergency history contains an unknown state.", error)
            Result.failure(error)
        }
    }

    fun save(events: List<EmergencyEvent>): Result<Unit> = synchronized(STORE_LOCK) {
        try {
            val encoded = encode(events.takeLast(MAX_SAVED_EVENTS)).toString()
            if (!preferences.edit().putString(KEY_EVENTS, encoded).commit()) {
                throw IllegalStateException("Android could not save emergency history.")
            }
            Result.success(Unit)
        } catch (error: JSONException) {
            Log.e(TAG, "Emergency history could not be encoded.", error)
            Result.failure(error)
        } catch (error: IllegalStateException) {
            Log.e(TAG, "Emergency history could not be saved.", error)
            Result.failure(error)
        }
    }

    fun updateAlertStatus(
        eventId: String,
        alertId: String,
        status: AlertStatus,
        message: String?
    ): Result<Unit> = synchronized(STORE_LOCK) {
        val loaded = load()
        val events = loaded.getOrElse { return@synchronized Result.failure(it) }
        val event = events.firstOrNull { it.id == eventId }
            ?: return@synchronized Result.failure(IllegalStateException("Emergency session was not found."))
        val alert = event.alerts.firstOrNull { it.alertId == alertId }
            ?: return@synchronized Result.failure(IllegalStateException("SMS alert attempt was not found."))

        if (!alert.status.canTransitionTo(status)) return@synchronized Result.success(Unit)

        alert.status = status
        alert.statusMessage = message
        event.activity.add("${timestamp()} SMS to ${alert.contact}: ${status.name}${message?.let { " — $it" } ?: ""}")
        if (event.activity.size > MAX_ACTIVITY_ITEMS) event.activity.removeAt(0)
        save(events)
    }

    private fun encode(events: List<EmergencyEvent>) = JSONArray().apply {
        events.forEach { event ->
            put(JSONObject().apply {
                put("id", event.id)
                put("createdAtMillis", event.createdAtMillis)
                put("state", event.state.name)
                put("locationStatus", event.locationStatus.name)
                put("trackingUpdates", event.trackingUpdates)
                put("lastLocationTimestampMillis", event.lastLocationTimestampMillis)
                put("location", event.location?.let(::encodeLocation) ?: JSONObject.NULL)
                put("alerts", JSONArray().apply {
                    event.alerts.forEach { alert ->
                        put(JSONObject().apply {
                            put("contact", alert.contact)
                            put("alertId", alert.alertId)
                            put("status", alert.status.name)
                            put("statusMessage", alert.statusMessage ?: JSONObject.NULL)
                        })
                    }
                })
                put("activity", JSONArray().apply {
                    event.activity.forEach { put(it) }
                })
            })
        }
    }

    private fun decode(array: JSONArray) = MutableList(array.length()) { index ->
        val item = array.getJSONObject(index)
        val alertsJson = item.getJSONArray("alerts")
        val activityJson = item.getJSONArray("activity")
        EmergencyEvent(
            id = item.getString("id"),
            createdAtMillis = item.getLong("createdAtMillis"),
            state = EmergencyState.valueOf(item.getString("state")),
            locationStatus = LocationStatus.valueOf(item.getString("locationStatus")),
            location = if (item.isNull("location")) null else decodeLocation(item.getJSONObject("location")),
            alerts = MutableList(alertsJson.length()) { alertIndex ->
                val alert = alertsJson.getJSONObject(alertIndex)
                AlertAttempt(
                    contact = alert.getString("contact"),
                    alertId = alert.optString("alertId").takeIf { it.isNotBlank() }
                        ?: UUID.randomUUID().toString(),
                    status = AlertStatus.valueOf(alert.getString("status")),
                    statusMessage = if (alert.isNull("statusMessage")) null
                        else alert.optString("statusMessage").takeIf { it.isNotBlank() }
                )
            },
            activity = MutableList(activityJson.length()) { activityIndex ->
                activityJson.getString(activityIndex)
            },
            trackingUpdates = item.getInt("trackingUpdates"),
            lastLocationTimestampMillis = item.getLong("lastLocationTimestampMillis")
        )
    }

    private fun encodeLocation(location: LocationSnapshot) = JSONObject().apply {
        put("latitude", location.latitude)
        put("longitude", location.longitude)
        put("accuracyMeters", location.accuracyMeters.toDouble())
        put("updatedAtMillis", location.updatedAtMillis)
    }

    private fun decodeLocation(value: JSONObject) = LocationSnapshot(
        latitude = value.getDouble("latitude"),
        longitude = value.getDouble("longitude"),
        accuracyMeters = value.getDouble("accuracyMeters").toFloat(),
        updatedAtMillis = value.getLong("updatedAtMillis")
    )

    companion object {
        private val STORE_LOCK = Any()
        private const val PREFERENCES_NAME = "emergency_session_history"
        private const val KEY_EVENTS = "events"
        private const val MAX_SAVED_EVENTS = 20
        private const val MAX_ACTIVITY_ITEMS = 40
        private const val TAG = "EmergencySessionStore"

        private fun timestamp(): String =
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())

    }
}
