package com.example.emergencysos

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Runs a deterministic, offline demonstration of the SOS orchestration flow. */
class EmergencyCoordinator(private val onChange: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var event: EmergencyEvent? = null
    private var lastMessage = "Ready. Choose a demo scenario to start."
    private var alertsFinished = false
    private var locationResolved = false
    private var trackingRunnable: Runnable? = null

    fun snapshot() = DemoSnapshot(event, lastMessage)

    fun startSos(contactNames: List<String>, provideLocation: Boolean) {
        val current = event
        if (current != null && current.state != EmergencyState.CLOSED) {
            current.activity.add(timestamp() + " Duplicate SOS ignored; the current session remains active.")
            lastMessage = "An SOS session is already active."
            notifyChanged()
            return
        }

        val contacts = contactNames.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
        if (contacts.isEmpty()) {
            lastMessage = "Enter at least one demo contact before starting."
            notifyChanged()
            return
        }

        handler.removeCallbacksAndMessages(null)
        val newEvent = EmergencyEvent(
            alerts = contacts.mapTo(mutableListOf()) { AlertAttempt(it) }
        )
        event = newEvent
        alertsFinished = false
        locationResolved = false
        addActivity(newEvent, "Emergency event created (${newEvent.id.take(8)}).")
        lastMessage = "Emergency event created. Preparing the alert workflow."
        notifyChanged()

        handler.postDelayed({
            if (!isCurrent(newEvent)) return@postDelayed
            newEvent.state = EmergencyState.ALERTING
            addActivity(newEvent, "Alert workflow started without waiting for location.")
            lastMessage = "SOS started in demo mode. No messages or calls are sent."
            notifyChanged()

            newEvent.alerts.forEachIndexed { index, attempt ->
                handler.postDelayed({
                    if (!isCurrent(newEvent)) return@postDelayed
                    attempt.status = AlertStatus.SIMULATED_SUBMITTED
                    addActivity(newEvent, "Demo alert submitted for ${attempt.contact}; real delivery is not attempted.")
                    if (newEvent.alerts.all { it.status != AlertStatus.PENDING }) {
                        alertsFinished = true
                        completeWorkflowIfReady(newEvent)
                    }
                    notifyChanged()
                }, ALERT_STEP_DELAY_MS + index * ALERT_STEP_DELAY_MS)
            }

            handler.postDelayed({
                if (!isCurrent(newEvent)) return@postDelayed
                locationResolved = true
                if (provideLocation) {
                    newEvent.locationStatus = LocationStatus.AVAILABLE
                    newEvent.location = DemoLocation(
                        latitude = DEMO_LATITUDE,
                        longitude = DEMO_LONGITUDE,
                        accuracyMeters = 35,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                    addActivity(newEvent, "Sample location acquired (±35 m). This is demonstration data, not GPS.")
                } else {
                    newEvent.locationStatus = LocationStatus.UNAVAILABLE
                    addActivity(newEvent, "Location unavailable; alert flow continues without coordinates.")
                }
                completeWorkflowIfReady(newEvent)
                notifyChanged()
            }, LOCATION_DELAY_MS)
        }, INITIATED_DISPLAY_MS)
    }

    fun stopSos() {
        val current = event
        if (current == null || current.state == EmergencyState.CLOSED) {
            lastMessage = "There is no active emergency session to end."
            notifyChanged()
            return
        }
        handler.removeCallbacksAndMessages(null)
        trackingRunnable = null
        current.state = EmergencyState.CLOSED
        addActivity(current, "Session closed; simulated location updates stopped.")
        lastMessage = "Session ${current.id.take(8)} closed."
        notifyChanged()
    }

    fun close() {
        handler.removeCallbacksAndMessages(null)
        trackingRunnable = null
    }

    private fun completeWorkflowIfReady(current: EmergencyEvent) {
        if (!isCurrent(current) || !alertsFinished || !locationResolved) return
        if (current.state == EmergencyState.ALERTING) {
            current.state = EmergencyState.ACTIVE
            addActivity(current, "Session active. Alert and location results are shown separately.")
            lastMessage = "Session active (${current.id.take(8)})."
            if (current.locationStatus == LocationStatus.AVAILABLE) startDemoTracking(current)
        }
    }

    private fun startDemoTracking(current: EmergencyEvent) {
        val runnable = object : Runnable {
            override fun run() {
                if (!isCurrent(current) || current.state != EmergencyState.ACTIVE ||
                    current.locationStatus != LocationStatus.AVAILABLE
                ) return

                current.trackingUpdates++
                current.location = DemoLocation(
                    latitude = DEMO_LATITUDE + current.trackingUpdates * 0.0001,
                    longitude = DEMO_LONGITUDE + current.trackingUpdates * 0.0001,
                    accuracyMeters = 35,
                    updatedAtMillis = System.currentTimeMillis()
                )
                addActivity(current, "Sample live-location update #${current.trackingUpdates}.")
                notifyChanged()
                handler.postDelayed(this, TRACKING_INTERVAL_MS)
            }
        }
        trackingRunnable = runnable
        handler.postDelayed(runnable, TRACKING_INTERVAL_MS)
    }

    private fun addActivity(current: EmergencyEvent, message: String) {
        current.activity.add("${timestamp()} $message")
        if (current.activity.size > MAX_ACTIVITY_ITEMS) {
            current.activity.removeAt(0)
        }
    }

    private fun isCurrent(candidate: EmergencyEvent) =
        event?.id == candidate.id && candidate.state != EmergencyState.CLOSED

    private fun timestamp(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private fun notifyChanged() = onChange()

    companion object {
        private const val INITIATED_DISPLAY_MS = 400L
        private const val ALERT_STEP_DELAY_MS = 500L
        private const val LOCATION_DELAY_MS = 1_200L
        private const val TRACKING_INTERVAL_MS = 4_000L
        private const val MAX_ACTIVITY_ITEMS = 40
        private const val DEMO_LATITUDE = 28.6139
        private const val DEMO_LONGITUDE = 77.2090
    }
}
