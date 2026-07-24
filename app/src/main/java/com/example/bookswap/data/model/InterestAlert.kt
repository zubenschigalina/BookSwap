package com.example.bookswap.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * An owner-facing alert that another user favorited one of their books (Epic 6,
 * decision #1: favoriting *notifies* the owner). Written client-side alongside the
 * [Favorite] so the alert appears in-app without a server round-trip.
 *
 * A real FCM *push* is a Cloud Function that fans this document out to the owner's
 * device — see docs/DECISIONS.md "external setup". The in-app alert works without it.
 *
 * Stored at Firestore path `interest_alerts/{id}`.
 */
data class InterestAlert(
    val id: String = "",
    /** The book owner who should see this alert. */
    val recipientId: String = "",
    /** The interested user who favorited the book. */
    val actorId: String = "",
    val actorName: String = "",
    val bookId: String = "",
    val bookTitle: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Date? = null,
)
