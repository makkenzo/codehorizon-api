package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.models.NotificationType
import com.stripe.Stripe
import com.stripe.model.Event
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import org.springframework.stereotype.Service

@Service
class StripeService(
    private val purchaseService: PurchaseService,
    private val courseProgressService: CourseProgressService,
    private val courseService: CourseService,
    private val notificationService: NotificationService
) {
    private val stripeSecretKey = System.getenv("STRIPE_SECRET_KEY")
        ?: throw RuntimeException("Missing STRIPE_SECRET_KEY")

    private val stripeEndpointSecret = System.getenv("STRIPE_ENDPOINT_SECRET")
        ?: throw RuntimeException("Missing STRIPE_ENDPOINT_SECRET")

    fun handleWebhook(payload: String, signature: String) {
        Stripe.apiKey = stripeSecretKey

        val event: Event = try {
            Webhook.constructEvent(payload, signature, stripeEndpointSecret)
        } catch (e: Exception) {
            throw RuntimeException("Invalid Stripe Webhook Signature")
        }

        when (event.type) {
            "checkout.session.completed" -> {
                val session = event.dataObjectDeserializer.`object`.orElse(null) as? Session
                if (session == null) {
                    println("!!! Не удалось десериализовать Session из события ${event.id}")
                    return
                }

                if ("paid" == session.paymentStatus) {
                    val userId = session.metadata["userId"]
                    val courseId = session.metadata["courseId"]
                    val amountTotal = session.amountTotal
                    val currency = session.currency

                    if (userId != null && courseId != null && amountTotal != null && currency != null) {
                        purchaseService.createPurchase(
                            userId = userId,
                            courseId = courseId,
                            stripeSessionId = session.id,
                            amount = amountTotal,
                            currency = currency.lowercase()
                        )

                        val course = courseService.getCourseById(courseId)
                        val courseTitle = course?.title ?: "купленный курс"

                        notificationService.createNotification(
                            userId = userId,
                            type = NotificationType.COURSE_PURCHASED,
                            message = "Вы успешно приобрели курс \"$courseTitle\"!",
                            link = "/courses/${course.slug}/learn"
                        )

                        try {
                            courseProgressService.addCourseProgress(userId, courseId)
                            println("Прогресс для курса $courseId успешно создан/обновлен для пользователя $userId.")
                        } catch (e: Exception) {
                            println("!!! Ошибка при создании/обновлении прогресса для $userId / $courseId: ${e.message}")
                        }
                    } else {
                        println("!!! Недостаточно метаданных или данных о сумме в сессии ${session.id}: userId=$userId, courseId=$courseId, amountTotal=$amountTotal, currency=$currency")
                    }
                } else {
                    println("Сессия ${session.id} завершена, но статус оплаты НЕ 'paid' (статус: ${session.paymentStatus}). Покупка не создана.")
                }
            }

            else -> println("Unhandled event type: ${event.type}")
        }
    }
}