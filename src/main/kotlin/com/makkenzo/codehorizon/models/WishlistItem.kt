package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "wishlist")
data class WishlistItem(
    @Id val id: String? = null,
    val userId: String,
    val courseId: String,
    val addedAt: Instant = Instant.now()
)
