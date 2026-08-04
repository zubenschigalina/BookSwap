package com.example.bookswap.data.repository

import com.example.bookswap.data.model.Proposal
import com.example.bookswap.data.model.ProposalStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Exchange proposals + acceptance state machine (Epic 7).
 *
 * Layout: proposals/{id} -> [Proposal]. Queries filter by a single field and sort
 * client-side (the inbox offers its own sort options), so no composite indexes are
 * needed.
 */
class ProposalRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
) {
    private val proposals = firestore.collection("proposals")

    /** Proposals sent TO this user (their inbox). */
    fun observeIncoming(recipientId: String): Flow<List<Proposal>> =
        observeBy("recipientId", recipientId)

    /** Proposals sent BY this user (their outbox). */
    fun observeOutgoing(proposerId: String): Flow<List<Proposal>> =
        observeBy("proposerId", proposerId)

    /** Live single proposal, so an open exchange reacts to being cancelled elsewhere. */
    fun observeProposal(proposalId: String): Flow<Proposal?> = callbackFlow {
        val registration = proposals.document(proposalId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(Proposal::class.java))
            }
        awaitClose { registration.remove() }
    }

    private fun observeBy(field: String, value: String): Flow<List<Proposal>> = callbackFlow {
        val registration = proposals
            .whereEqualTo(field, value)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Proposal::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun createProposal(proposal: Proposal) {
        val doc = proposals.document()
        doc.set(proposal.copy(id = doc.id)).await()
    }

    suspend fun reject(proposalId: String) {
        proposals.document(proposalId).update("status", ProposalStatus.REJECTED.name).await()
    }

    /**
     * Accepts a proposal (decision #3). In one batch:
     *  - this proposal → ACCEPTED
     *  - every book in the exchange (requested + offered) → RESERVED (leaves discovery)
     *  - every *other* PENDING proposal touching any of those books → EXPIRED
     *
     * Not a transaction: the competing set is read first, then written. Acceptable for
     * the MVP's low-contention single-owner accept; a Cloud Function would harden it.
     */
    suspend fun accept(proposal: Proposal) {
        val reservedIds = (listOf(proposal.requestedBookId) + proposal.offeredBookIds).distinct()

        // Find competing PENDING proposals: those requesting a now-reserved book, or
        // offering one. Firestore can't OR across fields, so two queries + a union.
        // `whereIn` / `whereArrayContainsAny` cap at 10 values; an exchange has ≤ 4.
        val byRequested = proposals
            .whereIn("requestedBookId", reservedIds)
            .get().await().toObjects(Proposal::class.java)
        val byOffered = proposals
            .whereArrayContainsAny("offeredBookIds", reservedIds)
            .get().await().toObjects(Proposal::class.java)

        val competing = (byRequested + byOffered)
            .filter { it.status == ProposalStatus.PENDING.name && it.id != proposal.id }
            .distinctBy { it.id }

        firestore.runBatch { batch ->
            batch.update(proposals.document(proposal.id), "status", ProposalStatus.ACCEPTED.name)
            reservedIds.forEach { bookId ->
                batch.update(firestore.collection("books").document(bookId), "status", "RESERVED")
            }
            competing.forEach { other ->
                batch.update(proposals.document(other.id), "status", ProposalStatus.EXPIRED.name)
            }
        }.await()
    }
}
