package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.Purchase
import org.springframework.data.mongodb.repository.MongoRepository

interface PurchaseRepository : MongoRepository<Purchase, String> {
    fun existsByStripeSessionId(stripeSessionId: String): Boolean
}