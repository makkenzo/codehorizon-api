package com.makkenzo.codehorizon.services

import com.stripe.Stripe
import com.stripe.model.Event
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import org.springframework.stereotype.Service

@Service
class StripeService(
    private val purchaseService: PurchaseService,
    private val courseProgressService: CourseProgressService
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
                val session = event.dataObjectDeserializer.`object`.get() as Session

                val userId = session.metadata["userId"]
                val courseId = session.metadata["courseId"]

                if (userId != null && courseId != null) {
                    purchaseService.createPurchase(userId, courseId, session.id)
                    courseProgressService.addCourseProgress(userId, courseId)
                }
            }

            else -> println("Unhandled event type: ${event.type}")
        }
    }
}