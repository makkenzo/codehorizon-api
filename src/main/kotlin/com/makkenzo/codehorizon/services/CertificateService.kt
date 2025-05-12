package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.CertificateDTO
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.models.Certificate
import com.makkenzo.codehorizon.models.Profile
import com.makkenzo.codehorizon.models.User
import com.makkenzo.codehorizon.repositories.CertificateRepository
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.repositories.ProfileRepository
import com.makkenzo.codehorizon.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class CertificateService(
    private val certificateRepository: CertificateRepository,
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val profileRepository: ProfileRepository,
    private val pdfGenerationService: PdfGenerationService,
    private val authorizationService: AuthorizationService
) {
    private val logger = LoggerFactory.getLogger(CertificateService::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())

    private fun generateFriendlyCertificateId(): String {
        val randomNumberPart = (10000..99999).random().toString()
        val randomCharsPart = UUID.randomUUID().toString()
            .replace("-", "")
            .take(5)
            .uppercase()
        return "CERT-$randomNumberPart-$randomCharsPart"
    }

    @Transactional
    fun createCertificateRecord(userId: String, courseId: String): Certificate? {
        if (certificateRepository.existsByUserIdAndCourseId(userId, courseId)) {
            logger.info("Сертификат для пользователя {} и курса {} уже существует.", userId, courseId)
            return certificateRepository.findByUserId(userId).find { it.courseId == courseId }
        }

        val studentUser = userRepository.findById(userId).orElse(null)
        val course = courseRepository.findById(courseId).orElse(null)
        val studentProfile = profileRepository.findByUserId(userId)

        if (studentUser == null || course == null) {
            logger.error("Не удалось создать сертификат: пользователь {} или курс {} не найден.", userId, courseId)
            return null
        }

        val studentName: String = when {
            studentProfile != null -> {
                when {
                    !studentProfile.firstName.isNullOrBlank() && !studentProfile.lastName.isNullOrBlank() ->
                        "${studentProfile.firstName} ${studentProfile.lastName}"

                    !studentProfile.firstName.isNullOrBlank() -> studentProfile.firstName
                    !studentProfile.lastName.isNullOrBlank() -> studentProfile.lastName
                    else -> studentUser.username
                }
            }

            else -> studentUser.username
        }

        var instructorDisplayName: String? = "Инструктор Курса"
        var instructorSigUrl: String? = null
        val courseAuthorUser: User? = userRepository.findById(course.authorId).orElse(null)

        if (courseAuthorUser != null) {
            val instructorProfile: Profile? = profileRepository.findByUserId(courseAuthorUser.id!!)
            instructorDisplayName = when {
                instructorProfile != null -> {
                    instructorSigUrl = instructorProfile.signatureUrl
                    when {
                        !instructorProfile.firstName.isNullOrBlank() && !instructorProfile.lastName.isNullOrBlank() ->
                            "${instructorProfile.firstName} ${instructorProfile.lastName}"

                        !instructorProfile.firstName.isNullOrBlank() -> instructorProfile.firstName
                        !instructorProfile.lastName.isNullOrBlank() -> instructorProfile.lastName
                        else -> courseAuthorUser.username
                    }
                }

                else -> courseAuthorUser.username
            }
        } else {
            logger.warn("Автор курса (инструктор) с ID {} не найден для курса {}.", course.authorId, courseId)
        }

        val certificate = Certificate(
            userId = userId,
            courseId = courseId,
            courseTitle = course.title,
            userName = studentName,
            uniqueCertificateId = generateFriendlyCertificateId(),
            instructorName = instructorDisplayName,
            instructorSignatureUrl = instructorSigUrl,
            category = course.category
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
            "Создан сертификат ID: {} для пользователя {} и курса {}. Инструктор: {}, URL подписи: {}",
            savedCertificate.uniqueCertificateId,
            userId,
            courseId,
            savedCertificate.instructorName ?: "N/A",
            savedCertificate.instructorSignatureUrl ?: "N/A"
        )
        return savedCertificate
    }

    fun getCertificatesForUser(): List<CertificateDTO> {
        val currentUserId = authorizationService.getCurrentUserEntity().id!!
        return certificateRepository.findByUserId(currentUserId).map { cert ->
            CertificateDTO(
                id = cert.id!!,
                uniqueCertificateId = cert.uniqueCertificateId,
                courseTitle = cert.courseTitle,
                completionDate = dateFormatter.format(cert.completionDate),
                instructorName = cert.instructorName,
                category = cert.category
            )
        }
    }

    fun getCertificatePdfBytes(certificateId: String): ByteArray {
        val certificate = certificateRepository.findById(certificateId)
            .orElseThrow { NotFoundException("Сертификат с ID $certificateId не найден") }

        return pdfGenerationService.generateCertificatePdf(certificate)
    }
}