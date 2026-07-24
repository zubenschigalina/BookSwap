package com.example.bookswap.data.repository

import com.example.bookswap.data.model.Book
import com.example.bookswap.data.model.Favorite
import com.example.bookswap.data.model.InterestAlert
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Favorites + owner interest alerts (Epic 6).
 *
 * Layout:
 *   favorites/{userId}_{bookId}  -> [Favorite] (deterministic id = idempotent)
 *   interest_alerts/{id}         -> [InterestAlert] for the book owner
 *
 * Queries filter by a single field only and sort client-side, so no composite
 * Firestore indexes are needed here.
 */
class FavoriteRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
) {
    private val favorites = firestore.collection("favorites")
    private val alerts = firestore.collection("interest_alerts")

    private fun docId(userId: String, bookId: String) = "${userId}_$bookId"

    /** Live list of the user's favorites, newest first. */
    fun observeMyFavorites(userId: String): Flow<List<Favorite>> = callbackFlow {
        val registration = favorites
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(Favorite::class.java).orEmpty()
                    .sortedByDescending { it.createdAt }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /** Live list of interest alerts for a book owner, newest first. */
    fun observeInterestAlerts(ownerId: String): Flow<List<InterestAlert>> = callbackFlow {
        val registration = alerts
            .whereEqualTo("recipientId", ownerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(InterestAlert::class.java).orEmpty()
                    .sortedByDescending { it.createdAt }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Records a favorite and notifies the owner (decision #1). No-op if this user has
     * already favorited this book, so the owner isn't alerted twice.
     *
     * @param actorName the interested user's display name, shown in the owner's alert.
     */
    suspend fun addFavorite(book: Book, userId: String, actorName: String) {
        val id = docId(userId, book.id)
        val existing = favorites.document(id).get().await()
        if (existing.exists()) return

        val favorite = Favorite(
            id = id,
            userId = userId,
            bookId = book.id,
            ownerId = book.ownerId,
            bookTitle = book.title,
            bookCoverUrl = book.imageUrls.firstOrNull().orEmpty(),
        )
        val alertRef = alerts.document()
        val alert = InterestAlert(
            id = alertRef.id,
            recipientId = book.ownerId,
            actorId = userId,
            actorName = actorName,
            bookId = book.id,
            bookTitle = book.title,
        )

        firestore.runBatch { batch ->
            batch.set(favorites.document(id), favorite)
            batch.set(alertRef, alert)
        }.await()
    }

    suspend fun removeFavorite(userId: String, bookId: String) {
        favorites.document(docId(userId, bookId)).delete().await()
    }

    suspend fun markAlertRead(alertId: String) {
        alerts.document(alertId).update("read", true).await()
    }
}
