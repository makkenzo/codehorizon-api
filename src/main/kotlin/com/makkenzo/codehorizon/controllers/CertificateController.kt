package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.CertificateDTO
import com.makkenzo.codehorizon.services.CertificateService
import com.makkenzo.codehorizon.utils.JwtUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
@Tag(name = "Certificates", description = "Управление сертификатами")
@SecurityRequirement(name = "bearerAuth")
class CertificateController(
    private val jwtUtils: JwtUtils,
    private val certificateService: CertificateService
) {
    @GetMapping("/users/me/certificates")
    @Operation(summary = "Получить список сертификатов текущего пользователя")
    @PreAuthorize("isAuthenticated()")
    fun getMyCertificates(request: HttpServletRequest): ResponseEntity<List<CertificateDTO>> {
        val token = request.cookies?.find { it.name == "access_token" }?.value
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val userId = jwtUtils.getIdFromToken(token)

        val certificates = certificateService.getCertificatesForUser(userId)
        return ResponseEntity.ok(certificates)
    }

    @GetMapping("/certificates/{certificateId}/download")
    @Operation(summary = "Скачать PDF сертификат по ID")
    @PreAuthorize("isAuthenticated()")
    fun downloadCertificate(
        @PathVariable certificateId: String,
        request: HttpServletRequest
    ): ResponseEntity<ByteArray> {
        val token = request.cookies?.find { it.name == "access_token" }?.value
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val userId = jwtUtils.getIdFromToken(token)

        try {
            val pdfBytes = certificateService.getCertificatePdfBytes(certificateId, userId)
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_PDF
            headers.setContentDispositionFormData("attachment", "certificate-$certificateId.pdf")

            return ResponseEntity(pdfBytes, headers, HttpStatus.OK)
        } catch (e: com.makkenzo.codehorizon.exceptions.NotFoundException) {
            return ResponseEntity.notFound().build()
        } catch (e: org.springframework.security.access.AccessDeniedException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
}