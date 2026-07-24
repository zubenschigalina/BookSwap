package com.example.bookswap.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * An audit record of a user changing their city, stored at
 * `users/{uid}/city_change_history/{autoId}`.
 *
 * PRD Epic 2: "Store full history of city changes in backend DB for
 * moderation/fraud audit." Changing a city requires a reason, captured here.
 */
data class CityChange(
    val fromCity: String = "",
    val toCity: String = "",
    val reason: String = "",
    @ServerTimestamp val changedAt: Date? = null,
)
