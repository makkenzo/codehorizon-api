package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "wishlists")
data class Wishlist(
    @Id val id: String? = null,
    val userId: String,
    val items: MutableList<String> = mutableListOf()
)
