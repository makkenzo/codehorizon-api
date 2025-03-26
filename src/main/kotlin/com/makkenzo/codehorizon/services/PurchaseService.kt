package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.models.Purchase
import com.makkenzo.codehorizon.repositories.PurchaseRepository
import org.springframework.stereotype.Service

@Service
class PurchaseService(private val purchaseRepository: PurchaseRepository) {
    fun createPurchase(userId: String, courseId: String, stripeSessionId: String) {
        if (purchaseRepository.existsByStripeSessionId(stripeSessionId)) {
            println("Purchase already exists for session: $stripeSessionId")
            return
        }

        val purchase = Purchase(
            userId = userId,
            courseId = courseId,
            stripeSessionId = stripeSessionId
        )
        purchaseRepository.save(purchase)
    }
}