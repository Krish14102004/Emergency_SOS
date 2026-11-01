package com.example.emergencysos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberValidatorTest {
    @Test
    fun normalizeRemovesFormattingAndKeepsCountryCode() {
        assertEquals("+14155550123", PhoneNumberValidator.normalize("+1 (415) 555-0123"))
    }

    @Test
    fun normalizeConvertsInternationalAccessPrefix() {
        assertEquals("+919876543210", PhoneNumberValidator.normalize("0091 98765-43210"))
    }

    @Test
    fun normalizeRejectsInvalidOrIncompleteNumbers() {
        assertEquals(null, PhoneNumberValidator.normalize("not a number"))
        assertEquals(null, PhoneNumberValidator.normalize("12345"))
        assertEquals(null, PhoneNumberValidator.normalize("+0123456789"))
    }

    @Test
    fun normalizeAllRequiresDistinctValidRecipientsAndCapsList() {
        assertTrue(PhoneNumberValidator.normalizeAll(listOf("+14155550123", "+919876543210")).isSuccess)
        assertFalse(PhoneNumberValidator.normalizeAll(listOf("+14155550123", "+1 (415) 555-0123")).isSuccess)
        assertFalse(
            PhoneNumberValidator.normalizeAll(
                listOf("+14155550123", "+919876543210", "+33123456789", "+491234567890", "+61123456789", "+81123456789")
            ).isSuccess
        )
        assertFalse(PhoneNumberValidator.normalizeAll(listOf(" ", "\n")).isSuccess)
    }

    @Test
    fun alertStatusesDoNotClaimDeliveryBeforeAProviderCallback() {
        assertTrue(AlertStatus.PENDING.canTransitionTo(AlertStatus.SENDING))
        assertTrue(AlertStatus.SENDING.canTransitionTo(AlertStatus.SUBMITTED))
        assertTrue(AlertStatus.SUBMITTED.canTransitionTo(AlertStatus.DELIVERED))
        assertFalse(AlertStatus.PENDING.canTransitionTo(AlertStatus.DELIVERED))
        assertFalse(AlertStatus.DELIVERED.canTransitionTo(AlertStatus.SUBMITTED))
        assertFalse(AlertStatus.FAILED.canTransitionTo(AlertStatus.DELIVERED))
        assertFalse(AlertStatus.SIMULATED_SUBMITTED.canTransitionTo(AlertStatus.SUBMITTED))
    }
}
