package com.example.bookswap.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A user's interest in someone else's book (Epic 6). Created on a swipe-right / Like.
 * Doc id is deterministic (`{userId}_{bookId}`) so favoriting is idempotent and easy
 * to remove. Book title/cover are denormalized so the Favorites list renders without
 * an extra read; the live AVAILABLE/UNAVAILABLE badge is resolved against the book.
 *
 * Stored at Firestore path `favorites/{userId}_{bookId}`.
 */
data class Favorite(
    val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    /** Book owner — the recipient of the interest alert (see [InterestAlert]). */
    val ownerId: String = "",
    val bookTitle: String = "",
    val bookCoverUrl: String = "",
    @ServerTimestamp val createdAt: Date? = null,
)
