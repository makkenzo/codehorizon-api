package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.MentorshipApplicationDTO
import com.makkenzo.codehorizon.dtos.MentorshipApplicationRequestDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.events.MentorshipApplicationApprovedEvent
import com.makkenzo.codehorizon.events.MentorshipApplicationRejectedEvent
import com.makkenzo.codehorizon.events.NewMentorshipApplicationEvent
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.models.ApplicationStatus
import com.makkenzo.codehorizon.models.MentorshipApplication
import com.makkenzo.codehorizon.repositories.MentorshipApplicationRepository
import com.makkenzo.codehorizon.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class MentorshipApplicationService(
    private val applicationRepository: MentorshipApplicationRepository,
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(MentorshipApplicationService::class.java)

    fun hasActiveApplication(userId: String): Boolean {
        return applicationRepository.existsByUserIdAndStatus(userId, ApplicationStatus.PENDING)
    }

    @Transactional
    fun applyForMentorship(userId: String, requestDTO: MentorshipApplicationRequestDTO): MentorshipApplicationDTO {
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("Пользователь с ID $userId не найден") }

        if (user.roles.contains("ROLE_MENTOR") || user.roles.contains("MENTOR")) {
            throw IllegalStateException("Пользователь уже является ментором.")
        }
        if (hasActiveApplication(userId)) {
            throw IllegalStateException("У вас уже есть активная заявка на рассмотрении.")
        }

        val application = MentorshipApplication(
            userId = user.id!!,
            username = user.username,
            userEmail = user.email,
            reason = requestDTO.reason
        )
        val savedApplication = applicationRepository.save(application)
        logger.info("Пользователь {} подал заявку на менторство. ID заявки: {}", userId, savedApplication.id)

        eventPublisher.publishEvent(
            NewMentorshipApplicationEvent(
                this,
                savedApplication.id!!,
                savedApplication.username,
                savedApplication.userEmail
            )
        )

        return mapToDTO(savedApplication)
    }

    fun getUserApplication(userId: String): MentorshipApplicationDTO? {
        return applicationRepository.findByUserId(userId)?.let { mapToDTO(it) }
    }

    fun getAllApplications(status: ApplicationStatus?, pageable: Pageable): PagedResponseDTO<MentorshipApplicationDTO> {
        val page: Page<MentorshipApplication> = if (status != null) {
            applicationRepository.findByStatus(status, pageable)
        } else {
            applicationRepository.findAll(pageable)
        }
        return PagedResponseDTO(
            content = page.content.map { mapToDTO(it) },
            pageNumber = page.number,
            pageSize = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            isLast = page.isLast
        )
    }

    @Transactional
    fun approveApplication(applicationId: String, adminUserId: String): MentorshipApplicationDTO {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { NotFoundException("Заявка с ID $applicationId не найдена") }

        if (application.status != ApplicationStatus.PENDING) {
            throw IllegalStateException("Заявка уже была рассмотрена. Текущий статус: ${application.status}")
        }

        val user = userRepository.findById(application.userId)
            .orElseThrow { NotFoundException("Пользователь заявки с ID ${application.userId} не найден") }

        val currentRoles = user.roles.toMutableList()
        if (!currentRoles.contains("ROLE_MENTOR")) {
            currentRoles.add("ROLE_MENTOR")
        }
        if (!currentRoles.contains("ROLE_USER") && !currentRoles.contains("USER")) {
            currentRoles.add("ROLE_USER")
        }

        val updatedUser = user.copy(roles = currentRoles.distinct())
        userRepository.save(updatedUser)

        application.status = ApplicationStatus.APPROVED
        application.reviewedAt = Instant.now()
        application.reviewedBy = adminUserId

        val savedApplication = applicationRepository.save(application)

        logger.info(
            "Заявка ID {} для пользователя {} одобрена администратором {}",
            applicationId,
            application.userId,
            adminUserId
        )

        eventPublisher.publishEvent(
            MentorshipApplicationApprovedEvent(
                this,
                savedApplication.id!!,
                savedApplication.userId,
                savedApplication.username,
                savedApplication.userEmail
            )
        )

        return mapToDTO(savedApplication)
    }

    @Transactional
    fun rejectApplication(
        applicationId: String,
        adminUserId: String,
        rejectionReason: String?
    ): MentorshipApplicationDTO {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { NotFoundException("Заявка с ID $applicationId не найдена") }

        if (application.status != ApplicationStatus.PENDING) {
            throw IllegalStateException("Заявка уже была рассмотрена. Текущий статус: ${application.status}")
        }

        application.status = ApplicationStatus.REJECTED
        application.rejectionReason = rejectionReason
        application.reviewedAt = Instant.now()
        application.reviewedBy = adminUserId
        val savedApplication = applicationRepository.save(application)

        logger.info(
            "Заявка ID {} для пользователя {} отклонена администратором {}. Причина: {}",
            applicationId,
            application.userId,
            adminUserId,
            rejectionReason ?: "Не указана"
        )
        
        eventPublisher.publishEvent(
            MentorshipApplicationRejectedEvent(
                this,
                savedApplication.id!!,
                savedApplication.userId,
                savedApplication.username,
                savedApplication.userEmail,
                savedApplication.rejectionReason
            )
        )

        return mapToDTO(savedApplication)
    }

    private fun mapToDTO(app: MentorshipApplication): MentorshipApplicationDTO {
        val user = userRepository.findById(app.userId).orElse(null)

        return MentorshipApplicationDTO(
            id = app.id!!,
            userId = app.userId,
            username = app.username,
            userEmail = app.userEmail,
            status = app.status,
            reason = app.reason,
            rejectionReason = app.rejectionReason,
            appliedAt = app.appliedAt,
            reviewedAt = app.reviewedAt,
            reviewedBy = app.reviewedBy,
            userRegisteredAt = user?.createdAt
        )
    }
}