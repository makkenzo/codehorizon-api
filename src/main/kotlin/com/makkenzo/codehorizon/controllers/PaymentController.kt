package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.CheckoutRequestDTO
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.services.PaymentService
import com.makkenzo.codehorizon.services.StripeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Оплата")
class PaymentController(
    private val paymentService: PaymentService,
    private val stripeService: StripeService
) {
    @PostMapping("/checkout")
    @Operation(summary = "Создает Checkout Session для Stripe", security = [SecurityRequirement(name = "bearerAuth")])
    fun createCheckoutSession(@Valid @RequestBody request: CheckoutRequestDTO): ResponseEntity<Map<String, String>> {
        return try {
            val sessionId = paymentService.createCheckoutSession(request.courseId, request.userId, request.coupon)
            ResponseEntity.ok(mapOf("sessionId" to sessionId))
        } catch (e: NotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (e.message ?: "Курс не найден")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to (e.message ?: "Неверный запрос")))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Ошибка создания сессии оплаты"))
        }
    }

    @PostMapping("/stripe/webhook")
    @Operation(summary = "Обрабатывает вебхук Stripe")
    fun handleStripeWebhook(
        @RequestBody payload: String,
        @RequestHeader("Stripe-Signature") signature: String?
    ): ResponseEntity<String> {
        if (signature == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing Stripe-Signature header")
        }

        return try {
            stripeService.handleWebhook(payload, signature)
            ResponseEntity.ok("Success")
        } catch (e: Exception) {
            ResponseEntity.badRequest().body("Webhook error: ${e.message}")
        }
    }
}