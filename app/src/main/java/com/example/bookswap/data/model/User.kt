package com.example.bookswap.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A BookSwap user profile, stored at Firestore path `users/{id}`.
 *
 * Sprint 1 populates the profile fields (firstName, profilePhotoUrl, city,
 * preferredMeetingPoint, phoneNumber). The reputation fields (ratingAvg,
 * exchangeCount, trustScore) are declared here to match the PRD data model but
 * are not driven until later sprints.
 *
 * All fields have defaults so Firestore can deserialize with its no-arg contract.
 */
data class User(
    val id: String = "",
    val email: String = "",
    val firstName: String = "",
    val profilePhotoUrl: String = "",
    val city: String = "",
    val preferredMeetingPoint: String = "",
    val phoneNumber: String = "",
    val ratingAvg: Double = 0.0,
    val exchangeCount: Int = 0,
    val trustScore: Double = 0.0,
    val status: String = "ACTIVE",
    @ServerTimestamp val createdAt: Date? = null,
) {
    /**
     * PRD Epic 2: required fields are First Name, Profile Photo, Current City and
     * Preferred Meeting Point (phone number is optional). A user is only allowed
     * into the app once all four are set.
     */
    val isComplete: Boolean
        get() = firstName.isNotBlank() &&
            profilePhotoUrl.isNotBlank() &&
            city.isNotBlank() &&
            preferredMeetingPoint.isNotBlank()
}
