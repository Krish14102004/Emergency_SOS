package com.example.emergencysos

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(SmsAlertDispatcher.EXTRA_EVENT_ID)
        val alertId = intent.getStringExtra(SmsAlertDispatcher.EXTRA_ALERT_ID)
        val phoneNumber = intent.getStringExtra(SmsAlertDispatcher.EXTRA_PHONE_NUMBER)
        if (eventId.isNullOrBlank() || alertId.isNullOrBlank() || phoneNumber.isNullOrBlank()) {
            Log.e(TAG, "Ignoring an SMS callback with missing identifiers.")
            return
        }

        val status = when (intent.action) {
            SmsAlertDispatcher.ACTION_SMS_SENT ->
                if (resultCode == Activity.RESULT_OK) AlertStatus.SUBMITTED else AlertStatus.FAILED
            SmsAlertDispatcher.ACTION_SMS_DELIVERED ->
                if (resultCode == Activity.RESULT_OK) AlertStatus.DELIVERED else AlertStatus.DELIVERY_UNCONFIRMED
            else -> return
        }
        val detail = when {
            intent.action == SmsAlertDispatcher.ACTION_SMS_SENT && status == AlertStatus.SUBMITTED ->
                "The device/carrier accepted the send attempt; human receipt is not confirmed."
            intent.action == SmsAlertDispatcher.ACTION_SMS_SENT ->
                "The device reported SMS send failure (result $resultCode)."
            status == AlertStatus.DELIVERED ->
                "The carrier reported delivery; this does not confirm the recipient read it."
            else -> "Delivery could not be confirmed (result $resultCode)."
        }

        val store = SessionStore(context)
        val update = store.updateAlertStatus(eventId, alertId, status, detail)
        val changed = Intent(SmsAlertDispatcher.ACTION_STATUS_CHANGED)
            .setPackage(context.packageName)
            .putExtra(SmsAlertDispatcher.EXTRA_EVENT_ID, eventId)
            .putExtra(SmsAlertDispatcher.EXTRA_ALERT_ID, alertId)
            .putExtra(SmsAlertDispatcher.EXTRA_PHONE_NUMBER, phoneNumber)
            .putExtra(SmsAlertDispatcher.EXTRA_STATUS, status.name)
        update.onSuccess {
            context.sendBroadcast(changed)
        }.onFailure { error ->
            Log.e(TAG, "Could not save SMS status for event $eventId.", error)
            context.sendBroadcast(
                changed.putExtra(
                    SmsAlertDispatcher.EXTRA_STORAGE_ERROR,
                    error.message ?: "SMS status could not be saved."
                )
            )
        }
    }

    companion object {
        private const val TAG = "EmergencySmsStatus"
    }
}
