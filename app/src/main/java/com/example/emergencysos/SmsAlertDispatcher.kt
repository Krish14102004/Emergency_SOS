package com.example.emergencysos

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager

class SmsAlertDispatcher(private val context: Context) {
    fun send(
        phoneNumber: String,
        message: String,
        eventId: String,
        alertId: String
    ): Result<Unit> {
        return try {
            val sentIntent = statusIntent(ACTION_SMS_SENT, phoneNumber, eventId, alertId, "sent")
            val deliveredIntent = statusIntent(ACTION_SMS_DELIVERED, phoneNumber, eventId, alertId, "delivered")
            val sentPendingIntent = pendingBroadcast(sentIntent, requestCode(alertId, 1))
            val deliveredPendingIntent = pendingBroadcast(deliveredIntent, requestCode(alertId, 2))
            val manager = context.getSystemService(SmsManager::class.java)
                ?: return Result.failure(IllegalStateException("SMS is not available on this device."))
            manager.sendTextMessage(phoneNumber, null, message, sentPendingIntent, deliveredPendingIntent)
            Result.success(Unit)
        } catch (error: SecurityException) {
            Result.failure(IllegalStateException("SMS permission is not available.", error))
        } catch (error: IllegalArgumentException) {
            Result.failure(IllegalStateException("The phone number or SMS message is invalid.", error))
        } catch (error: UnsupportedOperationException) {
            Result.failure(IllegalStateException("SMS is not supported by this device or carrier.", error))
        } catch (error: IllegalStateException) {
            Result.failure(IllegalStateException("The device could not start the SMS send.", error))
        }
    }

    private fun statusIntent(
        action: String,
        phoneNumber: String,
        eventId: String,
        alertId: String,
        callback: String
    ) = Intent(context, SmsStatusReceiver::class.java).apply {
        this.action = action
        data = Uri.parse("emergency-sos://sms/$alertId/$callback")
        putExtra(EXTRA_EVENT_ID, eventId)
        putExtra(EXTRA_ALERT_ID, alertId)
        putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
    }

    private fun pendingBroadcast(intent: Intent, requestCode: Int) =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun requestCode(alertId: String, callback: Int) =
        31 * alertId.hashCode() + callback

    companion object {
        const val ACTION_SMS_SENT = "com.example.emergencysos.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.example.emergencysos.SMS_DELIVERED"
        const val ACTION_STATUS_CHANGED = "com.example.emergencysos.SMS_STATUS_CHANGED"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_ALERT_ID = "alert_id"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_STATUS = "alert_status"
        const val EXTRA_STORAGE_ERROR = "storage_error"
    }
}
