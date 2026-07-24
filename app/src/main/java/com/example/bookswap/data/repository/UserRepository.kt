package com.example.bookswap.data.repository

import android.net.Uri
import com.example.bookswap.data.model.CityChange
import com.example.bookswap.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore + Cloud Storage access for Epic 2 (Profile & City Audit).
 *
 * Layout:
 *   users/{uid}                              -> [User] document
 *   users/{uid}/city_change_history/{autoId} -> [CityChange] audit record
 *   profile_photos/{uid}.jpg                 -> Storage object for the profile photo
 */
class UserRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val storage: FirebaseStorage = Firebase.storage,
) {
    private fun userDoc(uid: String) = firestore.collection("users").document(uid)

    /** Emits the user document and every subsequent change to it. */
    fun observeUser(uid: String): Flow<User?> = callbackFlow {
        val registration = userDoc(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObject(User::class.java))
        }
        awaitClose { registration.remove() }
    }

    suspend fun getUser(uid: String): User? =
        userDoc(uid).get().await().toObject(User::class.java)

    /**
     * Creates or updates the profile. Uses merge so callers can save a partial set
     * of fields without clobbering the reputation fields owned by later sprints.
     */
    suspend fun saveProfile(user: User) {
        userDoc(user.id).set(user, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    /**
     * Changes the user's city and appends an audit record atomically.
     * PRD Epic 2, Scenario 2: submitting a new city + reason updates the profile
     * and adds a row to city_change_history.
     */
    suspend fun changeCity(uid: String, newCity: String, reason: String) {
        val fromCity = getUser(uid)?.city.orEmpty()
        val batch = firestore.batch()
        batch.update(userDoc(uid), "city", newCity)
        val historyDoc = userDoc(uid).collection("city_change_history").document()
        batch.set(
            historyDoc,
            CityChange(fromCity = fromCity, toCity = newCity, reason = reason),
        )
        batch.commit().await()
    }

    suspend fun cityChangeHistory(uid: String): List<CityChange> =
        userDoc(uid).collection("city_change_history")
            .orderBy("changedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get().await()
            .toObjects(CityChange::class.java)

    /** Uploads a profile photo and returns its public download URL. */
    suspend fun uploadProfilePhoto(uid: String, uri: Uri): String {
        val ref = storage.reference.child("profile_photos/$uid.jpg")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }
}
