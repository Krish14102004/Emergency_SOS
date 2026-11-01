package com.example.emergencysos

import java.util.UUID

enum class EmergencyState { IDLE, INITIATED, ALERTING, ACTIVE, CLOSED }
enum class LocationStatus { PENDING, AVAILABLE, UNAVAILABLE }
enum class AlertStatus { PENDING, SIMULATED_SUBMITTED }

data class AlertAttempt(
    val contact: String,
    var status: AlertStatus = AlertStatus.PENDING
)

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val updatedAtMillis: Long
)

data class EmergencyEvent(
    val id: String = UUID.randomUUID().toString(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    var state: EmergencyState = EmergencyState.INITIATED,
    var locationStatus: LocationStatus = LocationStatus.PENDING,
    var location: LocationSnapshot? = null,
    val alerts: MutableList<AlertAttempt> = mutableListOf(),
    val activity: MutableList<String> = mutableListOf(),
    var trackingUpdates: Int = 0,
    var lastLocationTimestampMillis: Long = 0L
)

data class DemoSnapshot(
    val event: EmergencyEvent?,
    val history: List<EmergencyEvent>,
    val lastMessage: String
)
