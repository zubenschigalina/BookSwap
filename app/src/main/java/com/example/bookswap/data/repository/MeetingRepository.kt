package com.example.bookswap.data.repository

import com.example.bookswap.data.model.Meeting
import com.example.bookswap.data.model.MeetingStatus
import com.example.bookswap.data.model.Proposal
import com.example.bookswap.data.model.ProposalStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Meeting scheduling, mutual confirmation and cancellation (Epics 8 & 10).
 *
 * Layout: `meetings/{proposalId}` -> [Meeting]. Keying the document by proposal id makes
 * [schedule] idempotent — a double tap can't create two meetings for one exchange.
 *
 * Timing rules (the 2-hour window) live in
 * [com.example.bookswap.data.model.MeetingRules] so they stay unit-testable; this class
 * only persists the outcome.
 */
class MeetingRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
) {
    private val meetings = firestore.collection("meetings")
    private val proposals = firestore.collection("proposals")
    private val books = firestore.collection("books")

    /** Live meeting for one exchange; emits null until the proposer schedules. */
    fun observeForProposal(proposalId: String): Flow<Meeting?> = callbackFlow {
        val registration = meetings.document(proposalId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(Meeting::class.java))
            }
        awaitClose { registration.remove() }
    }

    /** Every meeting this user is part of, either side of the exchange. */
    fun observeForUser(userId: String): Flow<List<Meeting>> = callbackFlow {
        val registration = meetings
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Meeting::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    /**
     * Epic 8 initiation: the proposer sets the first date/time/place. Their own
     * confirmation is implied (they chose it), so only the recipient still has to agree.
     *
     * Uses `set`, so re-scheduling a never-confirmed meeting simply overwrites it.
     */
    suspend fun schedule(proposal: Proposal, dateTime: Date, locationAddress: String) {
        val meeting = Meeting(
            id = proposal.id,
            proposalId = proposal.id,
            proposerId = proposal.proposerId,
            recipientId = proposal.recipientId,
            participantIds = listOf(proposal.proposerId, proposal.recipientId),
            dateTime = dateTime,
            locationAddress = locationAddress,
            isConfirmedProposer = true,
            isConfirmedRecipient = false,
            status = MeetingStatus.PENDING_AGREEMENT.name,
        )
        meetings.document(proposal.id).set(meeting).await()
    }

    /**
     * Epic 8/10 reschedule: records a *requested* change without disturbing the active
     * schedule ("prior schedule remains active until the proposed new date/time is
     * confirmed"), and drops the status back to Pending Agreement.
     *
     * The requester counts as confirming their own suggestion; the other party does not.
     */
    suspend fun requestChange(
        meetingId: String,
        requestedById: String,
        isProposer: Boolean,
        dateTime: Date,
        locationAddress: String,
    ) {
        meetings.document(meetingId).update(
            mapOf(
                "proposedDateTime" to dateTime,
                "proposedLocationAddress" to locationAddress,
                "proposedById" to requestedById,
                "status" to MeetingStatus.PENDING_AGREEMENT.name,
                "isConfirmedProposer" to isProposer,
                "isConfirmedRecipient" to !isProposer,
            ),
        ).await()
    }

    /**
     * Epic 8 agreement: [userId] accepts the current state. If a change was pending it is
     * promoted to the active schedule first; once both sides have confirmed, the meeting
     * becomes CONFIRMED.
     */
    suspend fun confirm(meeting: Meeting, userId: String) {
        val isProposer = userId == meeting.proposerId
        val confirmedProposer = isProposer || meeting.isConfirmedProposer
        val confirmedRecipient = !isProposer || meeting.isConfirmedRecipient
        val bothAgree = confirmedProposer && confirmedRecipient

        val updates = mutableMapOf<String, Any?>(
            "isConfirmedProposer" to confirmedProposer,
            "isConfirmedRecipient" to confirmedRecipient,
            "status" to if (bothAgree) {
                MeetingStatus.CONFIRMED.name
            } else {
                MeetingStatus.PENDING_AGREEMENT.name
            },
        )

        // Promote a pending reschedule only when this confirmation completes the pair;
        // otherwise the request stays pending for the other party to see.
        if (bothAgree && meeting.hasPendingChange) {
            updates["dateTime"] = meeting.proposedDateTime
            updates["locationAddress"] = meeting.proposedLocationAddress
            updates["proposedDateTime"] = null
            updates["proposedLocationAddress"] = ""
            updates["proposedById"] = ""
        }

        meetings.document(meeting.id).update(updates).await()
    }

    /**
     * Epic 10 cancellation. In one batch:
     *  - the meeting → CANCELLED, recording who cancelled, why, and whether it was inside
     *    the 2-hour emergency window
     *  - the proposal → CANCELLED (the exchange is off)
     *  - every reserved book (requested + offered) → AVAILABLE, so it re-enters discovery
     *
     * [isEmergency] is decided by the caller from
     * [com.example.bookswap.data.model.MeetingRules]; it is stored for the moderation
     * trail rather than re-derived here.
     *
     * Like [ProposalRepository.accept] this is a batch, not a transaction — fine for the
     * MVP's two-party, low-contention case.
     */
    suspend fun cancel(
        meeting: Meeting,
        proposal: Proposal,
        cancelledById: String,
        reason: String,
        isEmergency: Boolean,
    ) {
        val releasedIds = (listOf(proposal.requestedBookId) + proposal.offeredBookIds).distinct()

        firestore.runBatch { batch ->
            batch.update(
                meetings.document(meeting.id),
                mapOf(
                    "status" to MeetingStatus.CANCELLED.name,
                    "cancelledById" to cancelledById,
                    "cancelReason" to reason,
                    "wasEmergencyCancel" to isEmergency,
                ),
            )
            batch.update(proposals.document(proposal.id), "status", ProposalStatus.CANCELLED.name)
            releasedIds.forEach { bookId ->
                batch.update(books.document(bookId), "status", "AVAILABLE")
            }
        }.await()
    }
}
