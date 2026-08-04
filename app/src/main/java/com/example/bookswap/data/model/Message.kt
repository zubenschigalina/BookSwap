package com.example.bookswap.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * One chat message in an exchange thread (Epic 9). Messaging unlocks only once the
 * proposal is ACCEPTED — enforced both in the UI and in `firestore.rules`, which reads
 * the parent proposal's status.
 *
 * Stored as a **subcollection** at `proposals/{proposalId}/messages/{id}`. Nesting under
 * the proposal is what gives Epic 9's "transcript remains permanently linked to the
 * exchange record" for free, and lets the security rule reach the parent to check
 * acceptance and participation.
 *
 * `senderName` is denormalized so the thread renders without a profile read per message.
 */
data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    /** One of [MessageType] names. */
    val type: String = MessageType.TEXT.name,
    /** Body for TEXT, and the human-readable label for LOCATION. */
    val text: String = "",
    /** Download URL for IMAGE messages. */
    val imageUrl: String = "",
    /** Optional coordinates for LOCATION messages; absent for a label-only pin. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ServerTimestamp val createdAt: Date? = null,
)

enum class MessageType {
    TEXT,
    IMAGE,
    /** A shared meeting point: a label plus optional coordinates. */
    LOCATION,
}
