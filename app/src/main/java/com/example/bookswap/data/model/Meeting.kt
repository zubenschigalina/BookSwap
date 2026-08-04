package com.example.bookswap.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * The physical hand-off for an accepted exchange (Epic 8 scheduling, Epic 10
 * cancellation). One meeting per accepted proposal — the document id **is** the
 * proposal id, which makes scheduling idempotent (no way to end up with two
 * meetings for one exchange).
 *
 * Stored at Firestore path `meetings/{proposalId}`.
 *
 * ### Active vs. proposed schedule
 * Epic 10 says "prior schedule remains active until the proposed new date/time is
 * confirmed", while Epic 8 says an edit sets the status back to Pending Agreement.
 * Both hold at once only if a reschedule is a *separate* pending offer rather than an
 * in-place edit, so:
 *
 *  - [dateTime] / [locationAddress] are the **active, agreed** schedule. The 2-hour
 *    cancellation window in [MeetingRules] is measured against these.
 *  - [proposedDateTime] / [proposedLocationAddress] hold a requested change, with
 *    [proposedById] naming who asked for it. Confirming promotes them into the active
 *    fields and clears them.
 *
 * The very first schedule has no prior agreement, so it writes [dateTime] directly with
 * the proposer already counted as confirming.
 */
data class Meeting(
    val id: String = "",
    val proposalId: String = "",
    val proposerId: String = "",
    val recipientId: String = "",
    /** Both party ids, so a user's meetings are one `arrayContains` query. */
    val participantIds: List<String> = emptyList(),

    /** The active, agreed schedule. Null only before the proposer first schedules. */
    val dateTime: Date? = null,
    val locationAddress: String = "",

    /** A requested change, pending the other party's confirmation. */
    val proposedDateTime: Date? = null,
    val proposedLocationAddress: String = "",
    val proposedById: String = "",

    val isConfirmedProposer: Boolean = false,
    val isConfirmedRecipient: Boolean = false,

    /** One of [MeetingStatus] names. */
    val status: String = MeetingStatus.PENDING_AGREEMENT.name,

    val cancelledById: String = "",
    val cancelReason: String = "",
    /** True when the cancellation happened inside the 2-hour window (Epic 10). */
    val wasEmergencyCancel: Boolean = false,

    @ServerTimestamp val createdAt: Date? = null,
) {
    /** True once both parties have confirmed the active schedule. */
    val isFullyConfirmed: Boolean
        get() = isConfirmedProposer && isConfirmedRecipient

    /** True while a reschedule request is awaiting the other party. */
    val hasPendingChange: Boolean
        get() = proposedDateTime != null

    /** Whether [userId] still needs to confirm the current state. */
    fun awaitingConfirmationFrom(userId: String): Boolean = when (userId) {
        proposerId -> !isConfirmedProposer
        recipientId -> !isConfirmedRecipient
        else -> false
    }
}

enum class MeetingStatus(val label: String) {
    PENDING_AGREEMENT("Pending agreement"),
    CONFIRMED("Confirmed"),
    CANCELLED("Cancelled");

    companion object {
        fun labelOf(name: String): String =
            entries.firstOrNull { it.name == name }?.label ?: name
    }
}

/**
 * Cancellation timing rules (Epic 10). Pure functions over an injected `now` so the
 * 2-hour boundary is unit-testable without touching the clock.
 */
object MeetingRules {

    /** Epic 10: standard cancellation is allowed up to 2 hours before the meeting. */
    const val EMERGENCY_WINDOW_MS: Long = 2 * 60 * 60 * 1000

    /**
     * True when [meetingTime] is less than 2 hours away — including already in the past,
     * since a meeting that should have happened is past the point of a free cancel.
     *
     * An unscheduled meeting (`null`) is never in the window: there is no time to be
     * close to, so cancelling it is unrestricted.
     */
    fun isWithinEmergencyWindow(meetingTime: Date?, now: Date): Boolean {
        if (meetingTime == null) return false
        return meetingTime.time - now.time < EMERGENCY_WINDOW_MS
    }

    /**
     * True when cancelling requires the user to type a mandatory reason, i.e. the
     * "Emergency Cancel" path. Outside the window, cancelling is unrestricted.
     */
    fun requiresEmergencyReason(meetingTime: Date?, now: Date): Boolean =
        isWithinEmergencyWindow(meetingTime, now)

    /** Milliseconds until [meetingTime]; negative once it has passed. */
    fun millisUntil(meetingTime: Date?, now: Date): Long =
        if (meetingTime == null) Long.MAX_VALUE else meetingTime.time - now.time
}
