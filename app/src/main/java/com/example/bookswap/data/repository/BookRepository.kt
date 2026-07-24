package com.example.bookswap.data.repository

import android.net.Uri
import com.example.bookswap.data.model.Book
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
 * Firestore + Storage access for books (Epic 3 catalog, Epic 4 discovery).
 *
 * Layout:
 *   books/{id}                        -> [Book] document
 *   book_images/{bookId}/{index}.jpg  -> Storage objects for the cover/photos
 */
class BookRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val storage: FirebaseStorage = Firebase.storage,
) {
    private val books = firestore.collection("books")

    /** Live list of the given owner's books, newest first (drives My Library). */
    fun observeMyBooks(ownerId: String): Flow<List<Book>> = callbackFlow {
        val registration = books
            .whereEqualTo("ownerId", ownerId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Book::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun getBook(id: String): Book? =
        books.document(id).get().await().toObject(Book::class.java)

    /**
     * Creates a book: reserves an id, uploads its images, then writes the document.
     * @return the saved book (with id and image URLs populated).
     */
    suspend fun addBook(book: Book, imageUris: List<Uri>): Book {
        val doc = books.document()
        val urls = uploadImages(doc.id, imageUris)
        val saved = book.copy(id = doc.id, imageUrls = urls)
        doc.set(saved).await()
        return saved
    }

    /**
     * Updates an existing book. Any [newImageUris] are uploaded and appended after
     * [keptImageUrls] (images the user chose to keep).
     */
    suspend fun updateBook(book: Book, keptImageUrls: List<String>, newImageUris: List<Uri>) {
        val startIndex = keptImageUrls.size
        val newUrls = uploadImages(book.id, newImageUris, startIndex)
        val updated = book.copy(imageUrls = keptImageUrls + newUrls)
        books.document(book.id).set(updated).await()
    }

    suspend fun deleteBook(id: String) {
        books.document(id).delete().await()
        // Best-effort image cleanup; ignore if the folder is already gone.
        runCatching {
            storage.reference.child("book_images/$id").listAll().await().items
                .forEach { it.delete().await() }
        }
    }

    /**
     * Discovery query (PRD Epic 4): AVAILABLE books in [city], excluding the current
     * user's own books. Requires a Firestore composite index on (ownerCity, status).
     */
    suspend fun discover(city: String, excludeOwnerId: String): List<Book> =
        books
            .whereEqualTo("ownerCity", city)
            .whereEqualTo("status", "AVAILABLE")
            .get().await()
            .toObjects(Book::class.java)
            .filter { it.ownerId != excludeOwnerId }

    private suspend fun uploadImages(
        bookId: String,
        uris: List<Uri>,
        startIndex: Int = 0,
    ): List<String> = uris.mapIndexed { i, uri ->
        val ref = storage.reference.child("book_images/$bookId/${startIndex + i}.jpg")
        ref.putFile(uri).await()
        ref.downloadUrl.await().toString()
    }
}
