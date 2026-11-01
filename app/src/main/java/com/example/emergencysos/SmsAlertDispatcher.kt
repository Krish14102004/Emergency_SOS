package com.example.emergencysos

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

class SmsAlertDispatcher(private val context: Context) {
    fun openComposer(phoneNumber: String, message: String): Result<Unit> {
        val normalizedNumber = PhoneNumberValidator.normalize(phoneNumber)
            ?: return Result.failure(IllegalArgumentException("The phone number is invalid."))

        return try {
            val intent = Intent(
                Intent.ACTION_SENDTO,
                Uri.fromParts("smsto", normalizedNumber, null)
            ).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (error: ActivityNotFoundException) {
            Result.failure(IllegalStateException("No SMS app is available on this device.", error))
        } catch (error: SecurityException) {
            Result.failure(IllegalStateException("The SMS app could not be opened.", error))
        } catch (error: IllegalArgumentException) {
            Result.failure(IllegalStateException("The SMS recipient or message is invalid.", error))
        }
    }
}
