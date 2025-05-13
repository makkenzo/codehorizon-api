package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.events.CoursePurchasedEvent
import com.makkenzo.codehorizon.models.Purchase
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.repositories.PurchaseRepository
import com.makkenzo.codehorizon.repositories.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class PurchaseService(
    private val purchaseRepository: PurchaseRepository,
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
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
            val savedPurchase = purchaseRepository.save(purchase)
            println("Запись о покупке для сессии $stripeSessionId успешно создана.")

            val user = userRepository.findById(userId).orElse(null)
            val course = courseRepository.findById(courseId).orElse(null)

            if (user != null && course != null) {
                eventPublisher.publishEvent(
                    CoursePurchasedEvent(
                        eventSource = this,
                        userId = user.id!!,
                        userUsername = user.username,
                        userEmail = user.email,
                        courseId = course.id!!,
                        courseTitle = course.title,
                        courseSlug = course.slug
                    )
                )
            }
        } catch (e: Exception) {
            println("!!! Ошибка при сохранении записи о покупке для сессии $stripeSessionId: ${e.message}")
            throw e
        }
    }
}