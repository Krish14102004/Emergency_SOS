package com.example.emergencysos

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var contactsInput: EditText
    private lateinit var stateText: TextView
    private lateinit var eventText: TextView
    private lateinit var locationText: TextView
    private lateinit var messageText: TextView
    private lateinit var alertsList: LinearLayout
    private lateinit var activityList: LinearLayout
    private lateinit var historyList: LinearLayout
    private lateinit var coordinator: EmergencyCoordinator
    private var permissionRequestPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        coordinator = EmergencyCoordinator(this) { runOnUiThread { render() } }
        render()
    }

    override fun onStart() {
        super.onStart()
        if (::coordinator.isInitialized) {
            coordinator.resumeForeground(hasLocationPermission())
        }
    }

    override fun onStop() {
        if (::coordinator.isInitialized) coordinator.pauseForeground()
        super.onStop()
    }

    private fun buildUi() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(COLOR_BACKGROUND)
        }
        content.addView(
            text("Emergency SOS", 27f, COLOR_TEXT, true),
            matchParams()
        )
        content.addView(
            text("Your safety flow, clearly in one place.", 14f, COLOR_MUTED),
            marginParams(top = 3, bottom = 16)
        )

        content.addView(createNotice())
        content.addView(createSessionCard(), marginParams(top = 14))
        content.addView(createContactsCard(), marginParams(top = 14))
        content.addView(createAlertsCard(), marginParams(top = 14))
        content.addView(createHistoryCard(), marginParams(top = 14))
        content.addView(createTimelineCard(), marginParams(top = 14))

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content)
        }
        setContentView(scrollView)
    }

    private fun createNotice(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = roundedBackground(COLOR_RED_TINT, COLOR_RED_TINT, 14)

        addView(
            text("DEMO", 11f, COLOR_RED, true).apply {
                gravity = Gravity.CENTER
                background = roundedBackground(Color.WHITE, Color.WHITE, 8)
                setPadding(dp(8), dp(5), dp(8), dp(5))
            }
        )
        addView(
            text("GPS is real. Alerts are simulated; no SMS or calls are sent.", 13f, COLOR_RED_DARK)
                .apply { setPadding(dp(10), 0, 0, 0) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
    }

    private fun createSessionCard(): View {
        val card = createCard()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            sectionTitle("Current session"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        stateText = text("IDLE", 11f, COLOR_MUTED, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(6), dp(11), dp(6))
            background = roundedBackground(COLOR_NEUTRAL_TINT, COLOR_NEUTRAL_TINT, 20)
        }
        header.addView(stateText)
        card.addView(header)

        eventText = text("No emergency event yet", 13f, COLOR_MUTED)
        card.addView(eventText, marginParams(top = 8, bottom = 10))
        locationText = text("Location will appear here when an SOS starts.", 14f, COLOR_TEXT)
        locationText.setPadding(dp(12), dp(12), dp(12), dp(12))
        locationText.background = roundedBackground(COLOR_SURFACE_ALT, COLOR_SURFACE_ALT, 12)
        card.addView(locationText)
        messageText = text("", 13f, COLOR_MUTED)
        card.addView(messageText, marginParams(top = 8))

        card.addView(
            actionButton(
                label = "START SOS · USE PHONE LOCATION",
                fill = COLOR_RED,
                textColor = Color.WHITE,
                onClick = { requestLocationAndStart() }
            ),
            marginParams(top = 14)
        )
        card.addView(
            actionButton(
                label = "CONTINUE WITHOUT LOCATION",
                fill = COLOR_GREEN_TINT,
                textColor = COLOR_GREEN_DARK,
                onClick = { startSos(hasLocationPermission = false) }
            ),
            marginParams(top = 8)
        )
        card.addView(
            actionButton(
                label = "END SESSION",
                fill = COLOR_SURFACE_ALT,
                textColor = COLOR_TEXT,
                onClick = { coordinator.stopSos() }
            ),
            marginParams(top = 8)
        )
        return card
    }

    private fun createContactsCard(): View {
        val card = createCard()
        card.addView(sectionTitle("Demo contacts"))
        card.addView(
            text("Names only · no messages are sent", 12f, COLOR_MUTED),
            marginParams(top = 3, bottom = 8)
        )
        contactsInput = EditText(this).apply {
            setText("Asha (Demo), Ravi (Demo)")
            hint = "Contact A, Contact B"
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP
            textSize = 14f
            setTextColor(COLOR_TEXT)
            setHintTextColor(COLOR_MUTED)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(Color.WHITE, COLOR_BORDER, 12, dp(1))
        }
        card.addView(contactsInput, matchParams())
        return card
    }

    private fun createAlertsCard(): View {
        val card = createCard()
        card.addView(sectionTitle("Alert simulation"))
        card.addView(
            text("Each contact has its own demo status.", 12f, COLOR_MUTED),
            marginParams(top = 3, bottom = 8)
        )
        alertsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(alertsList)
        return card
    }

    private fun createHistoryCard(): View {
        val card = createCard()
        card.addView(sectionTitle("Saved sessions"))
        card.addView(
            text("Stored on this phone", 12f, COLOR_MUTED),
            marginParams(top = 3, bottom = 8)
        )
        historyList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(historyList)
        return card
    }

    private fun createTimelineCard(): View {
        val card = createCard()
        card.addView(sectionTitle("Activity"))
        card.addView(
            text("Latest steps in the current session", 12f, COLOR_MUTED),
            marginParams(top = 3, bottom = 8)
        )
        activityList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(activityList)
        return card
    }

    private fun requestLocationAndStart() {
        if (hasLocationPermission()) {
            startSos(hasLocationPermission = true)
            return
        }
        permissionRequestPending = true
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST
        )
    }

    private fun startSos(hasLocationPermission: Boolean) {
        val contacts = contactsInput.text.toString().split(",", ";", "\n")
        coordinator.startSos(contacts, hasLocationPermission)
    }

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Deprecated("Deprecated in Android; retained for this small activity-based prototype.")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST && permissionRequestPending) {
            permissionRequestPending = false
            startSos(hasLocationPermission())
        }
    }

    private fun render() {
        val snapshot = coordinator.snapshot()
        val event = snapshot.event
        val state = event?.state ?: EmergencyState.IDLE
        stateText.text = when (state) {
            EmergencyState.IDLE -> "READY"
            EmergencyState.INITIATED -> "STARTING"
            EmergencyState.ALERTING -> "ALERTING"
            EmergencyState.ACTIVE -> "ACTIVE"
            EmergencyState.CLOSED -> "CLOSED"
        }
        val (pillColor, pillBackground) = when (state) {
            EmergencyState.ACTIVE -> COLOR_GREEN_DARK to COLOR_GREEN_TINT
            EmergencyState.ALERTING, EmergencyState.INITIATED -> COLOR_RED_DARK to COLOR_RED_TINT
            EmergencyState.CLOSED -> COLOR_MUTED to COLOR_NEUTRAL_TINT
            EmergencyState.IDLE -> COLOR_MUTED to COLOR_NEUTRAL_TINT
        }
        stateText.setTextColor(pillColor)
        stateText.background = roundedBackground(pillBackground, pillBackground, 20)

        eventText.text = event?.let {
            "ID ${it.id.take(8)}  ·  ${formatTime(it.createdAtMillis)}"
        } ?: "Start an SOS to create a session."
        val trackingCount = event?.trackingUpdates ?: 0
        locationText.text = when (event?.locationStatus) {
            LocationStatus.PENDING -> "Getting your current location… (up to 5 seconds)"
            LocationStatus.UNAVAILABLE -> "Location unavailable. The session continues without coordinates."
            LocationStatus.AVAILABLE -> event.location?.let {
                val ageSeconds = ((System.currentTimeMillis() - it.updatedAtMillis) / 1_000L).coerceAtLeast(0L)
                val quality = if (ageSeconds > 30L || it.accuracyMeters > 50f) "Lower confidence · " else ""
                "${quality}GPS  ${"%.5f".format(it.latitude)}, ${"%.5f".format(it.longitude)}\n±${it.accuracyMeters.toInt()} m  ·  Updated ${ageSeconds}s ago  ·  $trackingCount updates"
            } ?: "GPS location available"
            null -> "Location will appear here when an SOS starts."
        }
        locationText.setTextColor(
            when (event?.locationStatus) {
                LocationStatus.AVAILABLE -> COLOR_GREEN_DARK
                LocationStatus.UNAVAILABLE -> COLOR_RED_DARK
                else -> COLOR_TEXT
            }
        )
        locationText.background = roundedBackground(
            when (event?.locationStatus) {
                LocationStatus.AVAILABLE -> COLOR_GREEN_TINT
                LocationStatus.UNAVAILABLE -> COLOR_RED_TINT
                else -> COLOR_SURFACE_ALT
            },
            when (event?.locationStatus) {
                LocationStatus.AVAILABLE -> COLOR_GREEN_TINT
                LocationStatus.UNAVAILABLE -> COLOR_RED_TINT
                else -> COLOR_SURFACE_ALT
            },
            12
        )
        messageText.text = snapshot.lastMessage

        alertsList.removeAllViews()
        event?.alerts?.forEach { attempt ->
            val status = when (attempt.status) {
                AlertStatus.PENDING -> "PENDING"
                AlertStatus.SIMULATED_SUBMITTED -> "NOT SENT · DEMO"
            }
            alertsList.addView(
                labeledRow(attempt.contact, status, COLOR_RED_DARK, COLOR_RED_TINT)
            )
        }
        if (event?.alerts.isNullOrEmpty()) {
            alertsList.addView(text("Alerts will be listed when a session starts.", 13f, COLOR_MUTED))
        }

        historyList.removeAllViews()
        snapshot.history.asReversed().forEachIndexed { index, saved ->
            if (index > 0) historyList.addView(separator(), marginParams(top = 6, bottom = 6))
            historyList.addView(
                labeledRow(
                    "ID ${saved.id.take(8)}  ·  ${formatTime(saved.createdAtMillis)}",
                    saved.state.name,
                    if (saved.state == EmergencyState.CLOSED) COLOR_MUTED else COLOR_GREEN_DARK,
                    if (saved.state == EmergencyState.CLOSED) COLOR_NEUTRAL_TINT else COLOR_GREEN_TINT
                )
            )
        }
        if (snapshot.history.isEmpty()) {
            historyList.addView(text("Your saved sessions will appear here.", 13f, COLOR_MUTED))
        }

        activityList.removeAllViews()
        val activity = event?.activity.orEmpty()
        if (activity.isEmpty()) {
            activityList.addView(text("Session activity will appear here.", 13f, COLOR_MUTED))
        } else {
            activity.asReversed().forEachIndexed { index, entry ->
                if (index > 0) activityList.addView(separator(), marginParams(top = 5, bottom = 5))
                activityList.addView(text(entry, 12f, COLOR_TEXT))
            }
        }
    }

    private fun labeledRow(
        label: String,
        badge: String,
        badgeColor: Int,
        badgeBackground: Int
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(5), 0, dp(5))
        addView(
            text(label, 13f, COLOR_TEXT, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        addView(
            text(badge, 10f, badgeColor, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(5), dp(8), dp(5))
                background = roundedBackground(badgeBackground, badgeBackground, 16)
            }
        )
    }

    private fun separator() = View(this).apply {
        setBackgroundColor(COLOR_BORDER)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(1)
        )
    }

    private fun createCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(16))
        background = roundedBackground(Color.WHITE, COLOR_BORDER, 18, dp(1))
        elevation = dp(1).toFloat()
    }

    private fun actionButton(
        label: String,
        fill: Int,
        textColor: Int,
        onClick: () -> Unit
    ) = Button(this).apply {
        text = label
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        setTextColor(textColor)
        minHeight = dp(48)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = roundedBackground(fill, fill, 13)
        setOnClickListener { onClick() }
    }

    private fun roundedBackground(
        fill: Int,
        stroke: Int,
        radiusDp: Int,
        strokeWidth: Int = 0
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeWidth > 0) setStroke(strokeWidth, stroke)
    }

    override fun onDestroy() {
        if (::coordinator.isInitialized) coordinator.close()
        super.onDestroy()
    }

    private fun sectionTitle(value: String) = text(value, 16f, COLOR_TEXT, true)

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun matchParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun marginParams(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun formatTime(timeMillis: Long) =
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timeMillis))

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 101
        private val COLOR_BACKGROUND = Color.rgb(246, 248, 247)
        private val COLOR_SURFACE_ALT = Color.rgb(245, 247, 246)
        private val COLOR_BORDER = Color.rgb(230, 235, 232)
        private val COLOR_TEXT = Color.rgb(31, 41, 36)
        private val COLOR_MUTED = Color.rgb(107, 119, 112)
        private val COLOR_GREEN_DARK = Color.rgb(20, 101, 61)
        private val COLOR_GREEN_TINT = Color.rgb(231, 245, 236)
        private val COLOR_RED = Color.rgb(194, 54, 54)
        private val COLOR_RED_DARK = Color.rgb(153, 39, 39)
        private val COLOR_RED_TINT = Color.rgb(253, 237, 235)
        private val COLOR_NEUTRAL_TINT = Color.rgb(239, 242, 240)
    }
}
