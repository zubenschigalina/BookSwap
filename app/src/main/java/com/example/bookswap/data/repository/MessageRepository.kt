package com.example.bookswap.data.repository

import android.net.Uri
import com.example.bookswap.data.model.Message
import com.example.bookswap.data.model.MessageType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * In-app exchange messaging (Epic 9).
 *
 * Layout:
 *   proposals/{proposalId}/messages/{id}      -> [Message]
 *   chat_images/{proposalId}/{messageId}.jpg  -> Storage objects for image attachments
 *
 * Access control ("unlocked ONLY after an exchange proposal is accepted") is enforced in
 * `firestore.rules`, which reads the parent proposal's status and participant ids — the
 * subcollection layout is what makes that check possible. The UI mirrors the same gate so
 * a locked thread never renders a composer.
 *
 * Ordering uses `createdAt` on a single subcollection, so no composite index is needed.
 */
class MessageRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val storage: FirebaseStorage = Firebase.storage,
) {
    private fun thread(proposalId: String) =
        firestore.collection("proposals").document(proposalId).collection("messages")

    /** Live transcript, oldest first (chat order). */
    fun observeThread(proposalId: String): Flow<List<Message>> = callbackFlow {
        val registration = thread(proposalId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Message::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun sendText(proposalId: String, senderId: String, senderName: String, text: String) {
        val doc = thread(proposalId).document()
        doc.set(
            Message(
                id = doc.id,
                senderId = senderId,
                senderName = senderName,
                type = MessageType.TEXT.name,
                text = text.trim(),
            ),
        ).await()
    }

    /**
     * Uploads [imageUri] then writes the message, so a thread never shows a bubble
     * pointing at a half-uploaded object. The message id is reserved up front to name the
     * Storage object.
     */
    suspend fun sendImage(proposalId: String, senderId: String, senderName: String, imageUri: Uri) {
        val doc = thread(proposalId).document()
        val ref = storage.reference.child("chat_images/$proposalId/${doc.id}.jpg")
        ref.putFile(imageUri).await()
        val url = ref.downloadUrl.await().toString()
        doc.set(
            Message(
                id = doc.id,
                senderId = senderId,
                senderName = senderName,
                type = MessageType.IMAGE.name,
                imageUrl = url,
            ),
        ).await()
    }

    /**
     * Shares a meeting point as a location pin. Coordinates are optional: without the
     * Maps SDK (deferred) a pin is a labelled address the recipient can hand to their own
     * maps app, and [latitude]/[longitude] are populated only if we ever have them.
     */
    suspend fun sendLocation(
        proposalId: String,
        senderId: String,
        senderName: String,
        label: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ) {
        val doc = thread(proposalId).document()
        doc.set(
            Message(
                id = doc.id,
                senderId = senderId,
                senderName = senderName,
                type = MessageType.LOCATION.name,
                text = label.trim(),
                latitude = latitude,
                longitude = longitude,
            ),
        ).await()
    }
}
