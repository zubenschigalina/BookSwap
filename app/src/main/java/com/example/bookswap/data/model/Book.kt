package com.example.bookswap.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A physical book offered for exchange, stored at Firestore path `books/{id}`.
 *
 * `ownerCity` is denormalized from the owner's profile so discovery can query books
 * by city + status without a join. Discovery scope (PRD Epic 4) is same-city +
 * status == AVAILABLE.
 */
data class Book(
    val id: String = "",
    val ownerId: String = "",
    val ownerCity: String = "",
    val title: String = "",
    val author: String = "",
    val isbn: String = "",
    val category: String = "",
    val language: String = "",
    val description: String = "",
    /** Stored as a [BookCondition] name. */
    val condition: String = BookCondition.GOOD.name,
    val imageUrls: List<String> = emptyList(),
    /** AVAILABLE, RESERVED, EXCHANGED, ARCHIVED, REMOVED (see docs/DECISIONS.md #5). */
    val status: String = "AVAILABLE",
    @ServerTimestamp val createdAt: Date? = null,
)

/** Fixed condition ratings shown in the add/edit form and on discovery cards. */
enum class BookCondition(val label: String) {
    NEW("New"),
    LIKE_NEW("Like new"),
    VERY_GOOD("Very good"),
    GOOD("Good"),
    ACCEPTABLE("Acceptable");

    companion object {
        fun labelOf(name: String): String =
            entries.firstOrNull { it.name == name }?.label ?: name
    }
}

/** MVP fixed pick-lists for the add/edit form. */
object BookOptions {
    val categories = listOf(
        "Fiction", "Non-fiction", "Mystery", "Sci-Fi", "Fantasy",
        "Romance", "Biography", "History", "Children", "Other",
    )
    val languages = listOf("English", "Spanish", "French", "German", "Other")
}
