package com.example.bookswap.data.model

import java.util.Date
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Epic 10's 2-hour cancellation rule, covering the PRD's "2-Hour Time Boundary Test"
 * QA case. [MeetingRules] takes `now` as a parameter precisely so the boundary is
 * testable without waiting for a clock.
 */
class MeetingRulesTest {

    private val now = Date(1_700_000_000_000L)

    private fun inFuture(millis: Long) = Date(now.time + millis)

    @Test
    fun `well outside the window needs no reason`() {
        // 3 hours out.
        assertFalse(MeetingRules.requiresEmergencyReason(inFuture(3 * HOUR), now))
    }

    @Test
    fun `just outside the window needs no reason`() {
        // 2h00m01s out.
        assertFalse(MeetingRules.requiresEmergencyReason(inFuture(2 * HOUR + 1_000), now))
    }

    @Test
    fun `exactly two hours out needs no reason`() {
        // The rule is "less than 2 hours before", so the boundary itself is a normal
        // cancellation.
        assertFalse(MeetingRules.requiresEmergencyReason(inFuture(2 * HOUR), now))
    }

    @Test
    fun `just inside the window is an emergency`() {
        // 1h59m59s out.
        assertTrue(MeetingRules.requiresEmergencyReason(inFuture(2 * HOUR - 1_000), now))
    }

    @Test
    fun `well inside the window is an emergency`() {
        assertTrue(MeetingRules.requiresEmergencyReason(inFuture(10 * 60 * 1_000), now))
    }

    @Test
    fun `a meeting time already in the past is an emergency`() {
        assertTrue(MeetingRules.requiresEmergencyReason(Date(now.time - HOUR), now))
    }

    @Test
    fun `an unscheduled meeting is never an emergency`() {
        // No agreed time yet, so there is nothing to cancel close to.
        assertFalse(MeetingRules.requiresEmergencyReason(null, now))
    }

    @Test
    fun `millisUntil is positive before the meeting and negative after`() {
        assertTrue(MeetingRules.millisUntil(inFuture(HOUR), now) == HOUR)
        assertTrue(MeetingRules.millisUntil(Date(now.time - HOUR), now) == -HOUR)
    }

    private companion object {
        const val HOUR = 60 * 60 * 1_000L
    }
}
