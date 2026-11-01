package com.example.emergencysos

import java.util.UUID

enum class EmergencyState { IDLE, INITIATED, ALERTING, ACTIVE, CLOSED }
enum class LocationStatus { PENDING, AVAILABLE, UNAVAILABLE }
enum class AlertStatus {
    PENDING,
    READY_TO_COMPOSE,
    COMPOSER_OPENED,
    COMPOSER_FAILED,
    SENDING,
    SUBMITTED,
    SUBMISSION_UNCONFIRMED,
    DELIVERED,
    FAILED,
    DELIVERY_UNCONFIRMED,
    SIMULATED_SUBMITTED;

    fun canTransitionTo(next: AlertStatus): Boolean = when (next) {
        READY_TO_COMPOSE -> this == PENDING
        COMPOSER_OPENED -> this == READY_TO_COMPOSE || this == COMPOSER_OPENED ||
            this == COMPOSER_FAILED
        COMPOSER_FAILED -> this == READY_TO_COMPOSE || this == COMPOSER_OPENED ||
            this == COMPOSER_FAILED
        SENDING -> this == PENDING
        SUBMITTED -> this == SENDING || this == SUBMISSION_UNCONFIRMED
        SUBMISSION_UNCONFIRMED -> this == SENDING
        DELIVERED -> this == SENDING || this == SUBMITTED ||
            this == SUBMISSION_UNCONFIRMED || this == DELIVERY_UNCONFIRMED
        FAILED -> this == SENDING || this == PENDING || this == SUBMISSION_UNCONFIRMED
        DELIVERY_UNCONFIRMED -> this == SENDING || this == SUBMITTED ||
            this == SUBMISSION_UNCONFIRMED
        PENDING, SIMULATED_SUBMITTED -> false
    }
}

data class AlertAttempt(
    val contact: String,
    val alertId: String = UUID.randomUUID().toString(),
    var status: AlertStatus = AlertStatus.PENDING,
    var statusMessage: String? = null
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
