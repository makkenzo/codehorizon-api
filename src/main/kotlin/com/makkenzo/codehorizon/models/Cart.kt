package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "carts")
data class Cart(
    @Id val id: String? = null,
    val userId: String,
    val items: MutableList<CartItem> = mutableListOf()
)

data class CartItem(
    val productId: String,
    val quantity: Int
)