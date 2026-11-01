package com.example.emergencysos

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Coordinates locally stored sessions, foreground location, and user-reviewed SMS drafts. */
class EmergencyCoordinator(
    context: Context,
    private val onChange: () -> Unit
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val locationTracker = LocationTracker(appContext)
    private val sessionStore = SessionStore(appContext)
    private val smsDispatcher = SmsAlertDispatcher(appContext)
    private val history = mutableListOf<EmergencyEvent>()
    private var activeEvent: EmergencyEvent? = null
    private var lastMessage = "Ready. Start an SOS to create a saved session."
    private var alertsFinished = false
    private var locationResolved = false
    private var trackingPaused = false
    private var isForeground = false
    private var hasLocationPermission = false
    private var historyLoadError: String? = null
    private var locationTimeout: Runnable? = null
    init {
        val saved = sessionStore.load()
        saved.onSuccess { events ->
            history.addAll(events)
            activeEvent = history.lastOrNull { it.state != EmergencyState.CLOSED }
            if (activeEvent != null) {
                lastMessage = "Restored session ${activeEvent?.id?.take(8)} from local history."
            }
        }.onFailure { error ->
            historyLoadError = error.message ?: "invalid saved data"
            lastMessage = "Saved history could not be loaded: ${error.message ?: "invalid saved data"}"
        }
    }

    fun snapshot() = DemoSnapshot(activeEvent, history.toList(), lastMessage)

    fun startSos(
        phoneNumbers: List<String>,
        hasLocationPermission: Boolean,
        locationPermissionRequested: Boolean = hasLocationPermission
    ) {
        historyLoadError?.let {
            lastMessage = "A new session was not started because saved history could not be read: $it"
            notifyChanged()
            return
        }
        val current = activeEvent
        if (current != null && current.state != EmergencyState.CLOSED) {
            addActivity(current, "Duplicate SOS ignored; the current session remains active.")
            lastMessage = "An SOS session is already active."
            persist()
            return
        }

        val validatedContacts = PhoneNumberValidator.normalizeAll(phoneNumbers)
        if (validatedContacts.isFailure) {
            lastMessage = validatedContacts.exceptionOrNull()?.message ?: "Check the phone numbers and try again."
            notifyChanged()
            return
        }
        handler.removeCallbacksAndMessages(null)
        val event = EmergencyEvent(
            alerts = validatedContacts.getOrThrow().mapTo(mutableListOf()) {
                AlertAttempt(it, status = AlertStatus.READY_TO_COMPOSE)
            }
        )
        history.add(event)
        if (history.size > MAX_SAVED_EVENTS) history.removeAt(0)
        activeEvent = event
        alertsFinished = true
        locationResolved = !hasLocationPermission
        trackingPaused = false
        this.hasLocationPermission = hasLocationPermission
        addActivity(event, "Emergency event created (${event.id.take(8)}).")
        event.state = EmergencyState.ALERTING
        addActivity(event, "SMS drafts are ready. Open each draft and tap Send in the SMS app.")
        addActivity(event, "This app cannot verify whether a draft was sent or delivered.")
        lastMessage = "SOS started. Review and send each prepared message in your SMS app."
        persist()

        if (hasLocationPermission) {
            requestInitialLocation(event)
        } else {
            event.locationStatus = LocationStatus.UNAVAILABLE
            val locationMessage = if (locationPermissionRequested) {
                "Location permission was denied; the session continues without coordinates."
            } else {
                "SOS started without requesting location; messages remain available in the SMS app."
            }
            addActivity(event, locationMessage)
            completeWorkflowIfReady(event)
            persist()
        }
    }

    fun stopSos() {
        val event = activeEvent
        if (event == null || event.state == EmergencyState.CLOSED) {
            lastMessage = "There is no active emergency session to end."
            notifyChanged()
            return
        }
        locationTimeout?.let(handler::removeCallbacks)
        locationTimeout = null
        locationTracker.stop()
        event.state = EmergencyState.CLOSED
        if (event.locationStatus == LocationStatus.PENDING) {
            event.locationStatus = LocationStatus.UNAVAILABLE
        }
        addActivity(event, "Session closed; location updates stopped.")
        lastMessage = "Session ${event.id.take(8)} closed and saved."
        persist()
    }

    fun resumeForeground(hasLocationPermission: Boolean) {
        isForeground = true
        this.hasLocationPermission = hasLocationPermission
        val event = activeEvent ?: return
        if (event.state == EmergencyState.CLOSED) return

        if (event.state != EmergencyState.ACTIVE) {
            if (event.state == EmergencyState.ALERTING) {
                locationTimeout?.let(handler::removeCallbacks)
                locationTracker.stop()
                locationTimeout = null
                alertsFinished = true
                locationResolved = true
                if (event.locationStatus == LocationStatus.PENDING) {
                    event.locationStatus = LocationStatus.UNAVAILABLE
                }
                event.state = EmergencyState.ACTIVE
                addActivity(event, "Session restored. Draft status cannot confirm whether a message was sent.")
                persist()
            }
        }
        if (!hasLocationPermission) {
            if (event.locationStatus != LocationStatus.UNAVAILABLE) {
                event.locationStatus = LocationStatus.UNAVAILABLE
                addActivity(event, "Location permission unavailable; foreground tracking is stopped.")
                persist()
            }
            locationTracker.stop()
            return
        }

        if (trackingPaused) {
            addActivity(event, "Foreground location tracking resumed.")
            trackingPaused = false
        }
        startLocationUpdates(event)
    }

    fun pauseForeground() {
        isForeground = false
        locationTracker.stop()
        locationTimeout?.let(handler::removeCallbacks)
        locationTimeout = null
        val event = activeEvent
        if (event != null && event.state != EmergencyState.CLOSED) {
            if (!locationResolved) {
                locationResolved = true
                event.locationStatus = LocationStatus.UNAVAILABLE
                addActivity(event, "Location request ended when the app left the foreground.")
                completeWorkflowIfReady(event)
            }
            if (!trackingPaused) {
                trackingPaused = true
                addActivity(event, "Foreground location tracking paused while the app is not visible.")
            }
            persist()
        }
    }

    fun close() {
        handler.removeCallbacksAndMessages(null)
        locationTimeout = null
        locationTracker.stop()
    }

    fun openSmsComposer(alertId: String) {
        val event = activeEvent
        if (event == null || event.state == EmergencyState.CLOSED) {
            lastMessage = "Start an SOS session before opening an SMS draft."
            notifyChanged()
            return
        }
        val attempt = event.alerts.firstOrNull { it.alertId == alertId }
        if (attempt == null) {
            lastMessage = "The selected SMS recipient was not found."
            notifyChanged()
            return
        }
        if (attempt.status != AlertStatus.READY_TO_COMPOSE &&
            attempt.status != AlertStatus.COMPOSER_OPENED &&
            attempt.status != AlertStatus.COMPOSER_FAILED
        ) {
            lastMessage = "This SMS draft cannot be opened in its current state."
            notifyChanged()
            return
        }

        smsDispatcher.openComposer(attempt.contact, buildAlertMessage(event))
            .onSuccess {
                attempt.status = AlertStatus.COMPOSER_OPENED
                attempt.statusMessage = "Composer opened; sending and delivery are not confirmed."
                addActivity(event, "Opened an SMS draft for ${attempt.contact}; the user must tap Send.")
                lastMessage = "SMS app opened for ${attempt.contact}. Review the draft and tap Send there."
                persist()
            }
            .onFailure { error ->
                attempt.status = AlertStatus.COMPOSER_FAILED
                attempt.statusMessage = error.message ?: "Could not open the SMS app."
                addActivity(event, "Could not open an SMS draft for ${attempt.contact}: ${attempt.statusMessage}")
                lastMessage = attempt.statusMessage ?: "Could not open the SMS app."
                persist()
            }
    }

    private fun buildAlertMessage(event: EmergencyEvent) =
        "EMERGENCY SOS. Please contact me now. Event ${event.id.take(8)}. " +
            "GPS is not in this SMS. Call local emergency services if urgent."

    private fun requestInitialLocation(event: EmergencyEvent) {
        val timeout = Runnable {
            if (!isCurrent(event) || locationResolved) return@Runnable
            locationTracker.stopCurrentLocationRequest()
            locationResolved = true
            event.locationStatus = LocationStatus.UNAVAILABLE
            addActivity(event, "No usable current location arrived within five seconds; alerts continue.")
            completeWorkflowIfReady(event)
            persist()
        }
        locationTimeout = timeout
        handler.postDelayed(timeout, LOCATION_TIMEOUT_MS)

        locationTracker.getCurrentLocation(
            onLocation = { location ->
                if (!isCurrent(event) || locationResolved) return@getCurrentLocation
                handler.removeCallbacks(timeout)
                locationTimeout = null
                locationResolved = true
                if (location != null && isUsable(location)) {
                    acceptLocation(event, location, isInitialFix = true)
                    addActivity(event, locationQualityMessage(location))
                } else {
                    event.locationStatus = LocationStatus.UNAVAILABLE
                    addActivity(event, "No fresh, accurate location fix was available; alerts continue without coordinates.")
                }
                completeWorkflowIfReady(event)
                persist()
            },
            onFailure = { reason ->
                if (!isCurrent(event) || locationResolved) return@getCurrentLocation
                handler.removeCallbacks(timeout)
                locationTimeout = null
                locationResolved = true
                event.locationStatus = LocationStatus.UNAVAILABLE
                addActivity(event, "Location unavailable: $reason Alerts continue without coordinates.")
                completeWorkflowIfReady(event)
                persist()
            }
        )
    }

    private fun completeWorkflowIfReady(event: EmergencyEvent) {
        if (!isCurrent(event) || !alertsFinished || !locationResolved) return
        if (event.state == EmergencyState.ALERTING) {
            event.state = EmergencyState.ACTIVE
            addActivity(event, "Session active. Alert attempts and location status are shown separately.")
            lastMessage = "Session active (${event.id.take(8)}). Review and send each SMS draft manually."
            startLocationUpdates(event)
        }
    }

    private fun startLocationUpdates(event: EmergencyEvent) {
        if (!isForeground || !hasLocationPermission ||
            !isCurrent(event) || event.state != EmergencyState.ACTIVE
        ) return
        locationTracker.startUpdates(
            onLocation = { location ->
                if (!isForeground || !isCurrent(event) || event.state != EmergencyState.ACTIVE) {
                    return@startUpdates
                }
                if (location.updatedAtMillis <= event.lastLocationTimestampMillis || !isUsable(location)) {
                    return@startUpdates
                }
                val firstLiveFix = event.locationStatus != LocationStatus.AVAILABLE
                acceptLocation(event, location, isInitialFix = firstLiveFix)
                if (firstLiveFix) {
                    addActivity(event, "A fresh location update is now available.")
                }
                persist()
            },
            onFailure = { reason ->
                if (!isCurrent(event)) return@startUpdates
                lastMessage = "Location tracking could not start: $reason"
                addActivity(event, "Location tracking error: $reason")
                persist()
            }
        )
    }

    private fun acceptLocation(event: EmergencyEvent, location: LocationSnapshot, isInitialFix: Boolean) {
        event.location = location
        event.locationStatus = LocationStatus.AVAILABLE
        event.lastLocationTimestampMillis = location.updatedAtMillis
        if (isInitialFix || event.state == EmergencyState.ACTIVE) {
            event.trackingUpdates++
        }
    }

    private fun isUsable(location: LocationSnapshot): Boolean {
        val age = System.currentTimeMillis() - location.updatedAtMillis
        return location.latitude.isFinite() &&
            location.longitude.isFinite() &&
            location.latitude in -90.0..90.0 &&
            location.longitude in -180.0..180.0 &&
            location.accuracyMeters.isFinite() &&
            location.accuracyMeters >= 0f &&
            age in 0..MAX_LOCATION_AGE_MS &&
            location.accuracyMeters <= MAX_ACCEPTED_ACCURACY_METERS
    }

    private fun locationQualityMessage(location: LocationSnapshot): String {
        val age = (System.currentTimeMillis() - location.updatedAtMillis) / 1_000L
        return if (age <= PREFERRED_LOCATION_AGE_SECONDS &&
            location.accuracyMeters <= PREFERRED_ACCURACY_METERS
        ) {
            "Current location received (±${location.accuracyMeters.toInt()} m, ${age}s old)."
        } else {
            "Lower-confidence location received (±${location.accuracyMeters.toInt()} m, ${age}s old)."
        }
    }

    private fun addActivity(event: EmergencyEvent, message: String) {
        event.activity.add("${timestamp()} $message")
        if (event.activity.size > MAX_ACTIVITY_ITEMS) {
            event.activity.removeAt(0)
        }
    }

    private fun persist() {
        val result = sessionStore.save(history)
        result.onFailure { error ->
            lastMessage = "Session is not saved locally: ${error.message ?: "storage error"}"
        }
        notifyChanged()
    }

    private fun isCurrent(candidate: EmergencyEvent) =
        activeEvent?.id == candidate.id && candidate.state != EmergencyState.CLOSED

    private fun timestamp(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private fun notifyChanged() = onChange()

    companion object {
        private const val LOCATION_TIMEOUT_MS = 5_000L
        private const val MAX_LOCATION_AGE_MS = 60_000L
        private const val PREFERRED_LOCATION_AGE_SECONDS = 30L
        private const val MAX_ACCEPTED_ACCURACY_METERS = 100f
        private const val PREFERRED_ACCURACY_METERS = 50f
        private const val MAX_ACTIVITY_ITEMS = 40
        private const val MAX_SAVED_EVENTS = 20
    }
}
