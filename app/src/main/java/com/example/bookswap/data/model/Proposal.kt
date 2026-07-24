package com.example.bookswap.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * An exchange proposal (Epic 7). Requester-initiated (decision #2): the proposer offers
 * 1–3 of their own books for one book they want. Names/titles are denormalized so the
 * inbox renders without extra reads.
 *
 * State machine (decision #3): PENDING → ACCEPTED (books lock to RESERVED, competing
 * pending proposals auto-expire) | REJECTED | EXPIRED.
 *
 * Stored at Firestore path `proposals/{id}`.
 */
data class Proposal(
    val id: String = "",
    val proposerId: String = "",
    val proposerName: String = "",
    val recipientId: String = "",
    val requestedBookId: String = "",
    val requestedBookTitle: String = "",
    /** 1–3 of the proposer's own books offered in exchange. */
    val offeredBookIds: List<String> = emptyList(),
    val offeredBookTitles: List<String> = emptyList(),
    /** One of [ProposalStatus] names. */
    val status: String = ProposalStatus.PENDING.name,
    @ServerTimestamp val createdAt: Date? = null,
)

enum class ProposalStatus(val label: String) {
    PENDING("Pending"),
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    EXPIRED("Expired — book no longer available");

    companion object {
        fun labelOf(name: String): String =
            entries.firstOrNull { it.name == name }?.label ?: name
    }
}
