package com.makkenzo.codehorizon.services

import com.stripe.Stripe
import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionCreateParams
import org.springframework.stereotype.Service

@Service
class PaymentService(private val courseService: CourseService) {
    private val stripeSecretKey =
        System.getenv("STRIPE_SECRET_KEY") ?: throw RuntimeException("Missing STRIPE_SECRET_KEY")
    private val frontDomainUrl =
        System.getenv("FRONT_DOMAIN_URL") ?: throw RuntimeException("Missing FRONT_DOMAIN_URL")

    fun createCheckoutSession(courseId: String, userId: String, coupon: String?): String {
        Stripe.apiKey = stripeSecretKey

        val course = courseService.getCourseById(courseId)

        val discountedPrice = ((course.price - course.discount) * 100).toLong().coerceAtLeast(0L)

        val sessionParams = SessionCreateParams.builder()
            .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl("$frontDomainUrl/payment/success?session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl("$frontDomainUrl/payment/cancel")
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(1)
                    .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder().setCurrency("usd")
                            .setUnitAmount(discountedPrice)
                            .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(course.title)
                                    .setDescription("Курс")
                                    .build()
                            ).build()
                    ).build()

            )
            .putMetadata("userId", userId)
            .putMetadata("courseId", courseId)
            .build()

        val session = Session.create(sessionParams)
        return session.id
    }
}