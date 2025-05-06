package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.CertificateDTO
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.models.Certificate
import com.makkenzo.codehorizon.repositories.CertificateRepository
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.repositories.ProfileRepository
import com.makkenzo.codehorizon.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class CertificateService(
    private val certificateRepository: CertificateRepository,
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val profileRepository: ProfileRepository,
    private val pdfGenerationService: PdfGenerationService
) {
    private val logger = LoggerFactory.getLogger(CertificateService::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())

    @Transactional
    fun createCertificateRecord(userId: String, courseId: String): Certificate? {
        if (certificateRepository.existsByUserIdAndCourseId(userId, courseId)) {
            logger.info("Сертификат для пользователя {} и курса {} уже существует.", userId, courseId)
            return certificateRepository.findByUserId(userId).find { it.courseId == courseId }
        }

        val user = userRepository.findById(userId).orElse(null)
        val course = courseRepository.findById(courseId).orElse(null)
        val profile = profileRepository.findByUserId(userId)

        if (user == null || course == null) {
            logger.error("Не удалось создать сертификат: пользователь {} или курс {} не найден.", userId, courseId)
            return null
        }

        val determinedUserName: String = when {
            profile != null -> {
                when {
                    !profile.firstName.isNullOrBlank() && !profile.lastName.isNullOrBlank() ->
                        "${profile.firstName} ${profile.lastName}"

                    !profile.firstName.isNullOrBlank() ->
                        profile.firstName

                    !profile.lastName.isNullOrBlank() ->
                        profile.lastName

                    else -> user.username
                }
            }

            else -> user.username
        }


        val certificate = Certificate(
            userId = userId,
            courseId = courseId,
            courseTitle = course.title,
            userName = determinedUserName
        )

        val savedCertificate = try {
            certificateRepository.save(certificate)
        } catch (e: Exception) {
            logger.error(
                "Ошибка сохранения сертификата для пользователя {} и курса {}: {}",
                userId,
                courseId,
                e.message,
                e
            )
            return null
        }

        logger.info(
            "Создан сертификат ID: {} для пользователя {} и курса {}",
            savedCertificate.uniqueCertificateId,
            userId,
            courseId
        )
        return savedCertificate
    }

    fun getCertificatesForUser(userId: String): List<CertificateDTO> {
        return certificateRepository.findByUserId(userId).map { cert ->
            CertificateDTO(
                id = cert.id!!,
                uniqueCertificateId = cert.uniqueCertificateId,
                courseTitle = cert.courseTitle,
                completionDate = dateFormatter.format(cert.completionDate)
            )
        }
    }

    fun getCertificatePdfBytes(certificateId: String, requestingUserId: String): ByteArray {
        val certificate = certificateRepository.findById(certificateId)
            .orElseThrow { NotFoundException("Сертификат с ID $certificateId не найден") }

        if (certificate.userId != requestingUserId) {
            throw AccessDeniedException("У вас нет прав на скачивание этого сертификата")
        }

        return pdfGenerationService.generateCertificatePdf(certificate)
    }
}