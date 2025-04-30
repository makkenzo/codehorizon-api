package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.models.Purchase
import com.makkenzo.codehorizon.repositories.PurchaseRepository
import org.springframework.stereotype.Service

@Service
class PurchaseService(private val purchaseRepository: PurchaseRepository) {
    fun createPurchase(
        userId: String,
        courseId: String,
        stripeSessionId: String,
        amount: Long,
        currency: String
    ) {
        if (purchaseRepository.existsByStripeSessionId(stripeSessionId)) {
            println("Покупка для Stripe сессии $stripeSessionId уже существует. Пропускаем создание.")
            return
        }

        val purchase = Purchase(
            userId = userId,
            courseId = courseId,
            stripeSessionId = stripeSessionId,
            amount = amount,
            currency = currency
        )

        try {
            purchaseRepository.save(purchase)
            println("Запись о покупке для сессии $stripeSessionId успешно создана.")
        } catch (e: Exception) {
            println("!!! Ошибка при сохранении записи о покупке для сессии $stripeSessionId: ${e.message}")
            throw e
        }
    }
}