package com.example.emergencysos

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
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
    private lateinit var coordinator: EmergencyCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        coordinator = EmergencyCoordinator { runOnUiThread { render() } }
        render()
    }

    private fun buildUi() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(20), dp(28))
            setBackgroundColor(Color.rgb(247, 248, 250))
        }
        content.addView(text("EMERGENCY SOS", 26f, Color.rgb(31, 41, 55), true))
        content.addView(text("Algorithm flow prototype", 15f, Color.rgb(100, 116, 139)))
        content.addView(
            text(
                "DEMO MODE · No SMS, calls, network requests, or real GPS are used.",
                14f,
                Color.rgb(127, 29, 29),
                true
            ).apply {
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(Color.rgb(254, 226, 226))
            },
            marginParams(top = 16, bottom = 16)
        )

        content.addView(sectionTitle("Emergency contacts"))
        content.addView(text("Demo names, separated by commas or new lines", 13f, Color.GRAY))
        contactsInput = EditText(this).apply {
            setText("Asha (Demo), Ravi (Demo)")
            hint = "Contact A, Contact B"
            minLines = 2
            gravity = Gravity.TOP
        }
        content.addView(contactsInput, marginParams(top = 6, bottom = 12))

        val startAvailable = Button(this).apply {
            text = "START · LOCATION AVAILABLE"
            setOnClickListener { startDemo(provideLocation = true) }
        }
        val startUnavailable = Button(this).apply {
            text = "START · NO LOCATION"
            setOnClickListener { startDemo(provideLocation = false) }
        }
        content.addView(startAvailable, matchParams())
        content.addView(startUnavailable, marginParams(top = 4, bottom = 4))
        content.addView(Button(this).apply {
            text = "END EMERGENCY SESSION"
            setOnClickListener { coordinator.stopSos() }
        }, marginParams(bottom = 14))

        content.addView(sectionTitle("Session"))
        stateText = text("IDLE", 18f, Color.rgb(31, 41, 55), true)
        eventText = text("No active event", 14f, Color.DKGRAY)
        locationText = text("Location: not started", 14f, Color.DKGRAY)
        messageText = text("", 14f, Color.rgb(71, 85, 105))
        listOf(stateText, eventText, locationText, messageText).forEach {
            content.addView(it, marginParams(top = 4, bottom = 4))
        }

        content.addView(sectionTitle("Per-contact alert status"), marginParams(top = 18, bottom = 4))
        alertsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(alertsList)

        content.addView(sectionTitle("Activity timeline"), marginParams(top = 18, bottom = 4))
        activityList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(activityList)

        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun startDemo(provideLocation: Boolean) {
        val contacts = contactsInput.text.toString().split(",", ";", "\n")
        coordinator.startSos(contacts, provideLocation)
    }

    private fun render() {
        val snapshot = coordinator.snapshot()
        val event = snapshot.event
        stateText.text = event?.state?.name ?: EmergencyState.IDLE.name
        stateText.setTextColor(
            when (event?.state) {
                EmergencyState.ACTIVE -> Color.rgb(21, 128, 61)
                EmergencyState.ALERTING, EmergencyState.INITIATED -> Color.rgb(180, 83, 9)
                EmergencyState.CLOSED -> Color.rgb(71, 85, 105)
                else -> Color.rgb(31, 41, 55)
            }
        )
        eventText.text = event?.let { "Event ID: ${it.id.take(8)}  ·  ${formatTime(it.createdAtMillis)}" }
            ?: "No emergency event created"
        val trackingCount = event?.trackingUpdates ?: 0
        locationText.text = when (event?.locationStatus) {
            LocationStatus.PENDING -> "Location: requesting demo location…"
            LocationStatus.UNAVAILABLE -> "Location: unavailable · alert flow continued"
            LocationStatus.AVAILABLE -> event?.location?.let {
                "Location: sample ${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)} · ±${it.accuracyMeters} m · updated ${formatTime(it.updatedAtMillis)} · $trackingCount updates"
            } ?: "Location: sample available"
            null -> "Location: not started"
        }
        messageText.text = snapshot.lastMessage

        alertsList.removeAllViews()
        event?.alerts?.forEach { attempt ->
            val status = when (attempt.status) {
                AlertStatus.PENDING -> "PENDING"
                AlertStatus.SIMULATED_SUBMITTED -> "SIMULATED · NOT SENT"
            }
            alertsList.addView(text("${attempt.contact}    $status", 14f, Color.rgb(51, 65, 85)), marginParams(top = 3, bottom = 3))
        }

        activityList.removeAllViews()
        val activity = event?.activity.orEmpty()
        if (activity.isEmpty()) {
            activityList.addView(text("The algorithm timeline will appear here.", 14f, Color.GRAY))
        } else {
            activity.asReversed().forEach {
                activityList.addView(text(it, 13f, Color.rgb(71, 85, 105)), marginParams(top = 3, bottom = 3))
            }
        }
    }

    override fun onDestroy() {
        if (::coordinator.isInitialized) coordinator.close()
        super.onDestroy()
    }

    private fun sectionTitle(value: String) = text(value, 17f, Color.rgb(31, 41, 55), true)

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
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
}
