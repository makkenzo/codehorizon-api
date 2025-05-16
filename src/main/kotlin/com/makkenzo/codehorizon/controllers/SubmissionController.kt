package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.SubmitAnswerDTO
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.models.Submission
import com.makkenzo.codehorizon.models.SubmissionStatus
import com.makkenzo.codehorizon.models.TaskType
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.repositories.SubmissionRepository
import com.makkenzo.codehorizon.services.AuthorizationService
import com.makkenzo.codehorizon.services.GradingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/submissions")
@Tag(name = "Submissions", description = "Отправка и получение ответов на задания")
@SecurityRequirement(name = "bearerAuth")
class SubmissionController(
    private val authorizationService: AuthorizationService,
    private val courseRepository: CourseRepository,
    private val submissionRepository: SubmissionRepository,
    private val gradingService: GradingService
) {
    @PostMapping
    @Operation(summary = "Отправить ответ на задание")
    @PreAuthorize("isAuthenticated()")
    fun submitAnswer(@Valid @RequestBody submitAnswerDTO: SubmitAnswerDTO): ResponseEntity<Submission> {
        val currentUser = authorizationService.getCurrentUserEntity()

        val course = courseRepository.findById(submitAnswerDTO.courseId)
            .orElseThrow { NotFoundException("Курс с ID ${submitAnswerDTO.courseId} не найден") }

        val lesson = course.lessons.find { it.id == submitAnswerDTO.lessonId }
            ?: throw NotFoundException("Урок с ID ${submitAnswerDTO.lessonId} не найден в курсе ${course.title}")

        val task = lesson.tasks.find { it.id == submitAnswerDTO.taskId }
            ?: throw NotFoundException("Задача с ID ${submitAnswerDTO.taskId} не найдена в уроке ${lesson.title}")

        // TODO: Добавить проверку, имеет ли пользователь доступ к этому курсу (записан ли он)
        // Например, через CourseProgressRepository.existsByUserIdAndCourseId(currentUser.id!!, course.id!!)
        // if (!courseProgressRepository.existsByUserIdAndCourseId(currentUser.id!!, course.id!!)) {
        //    throw AccessDeniedException("У вас нет доступа к этому курсу.")
        // }

        val submission = Submission(
            userId = currentUser.id!!,
            courseId = submitAnswerDTO.courseId,
            lessonId = submitAnswerDTO.lessonId,
            taskId = submitAnswerDTO.taskId,
            language = if (task.taskType == TaskType.CODE_INPUT) submitAnswerDTO.language else null,
            answerCode = if (task.taskType == TaskType.CODE_INPUT) submitAnswerDTO.answerCode else null,
            answerText = if (task.taskType != TaskType.CODE_INPUT) submitAnswerDTO.answerText else null,
            status = SubmissionStatus.PENDING
        )

        val savedSubmission = submissionRepository.save(submission)

        gradingService.gradeSubmission(savedSubmission, task)

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(savedSubmission)
    }

    @GetMapping("/{submissionId}")
    @Operation(summary = "Получить информацию об отправленном ответе по ID")
    @PreAuthorize("isAuthenticated()")
    fun getSubmission(@PathVariable submissionId: String): ResponseEntity<Submission> {
        val currentUser = authorizationService.getCurrentUserEntity()
        val submission = submissionRepository.findById(submissionId)
            .orElseThrow { NotFoundException("Ответ с ID $submissionId не найден") }

        if (submission.userId != currentUser.id && !authorizationService.isCurrentUserAdmin() && !authorizationService.isCurrentUserMentor()) {
            if (authorizationService.isCurrentUserMentor()) {
                val course = courseRepository.findById(submission.courseId).orElse(null)
                if (course == null || course.authorId != currentUser.id) {
                    throw AccessDeniedException("У вас нет прав на просмотр этого ответа.")
                }
            } else {
                throw AccessDeniedException("У вас нет прав на просмотр этого ответа.")
            }
        }
        return ResponseEntity.ok(submission)
    }

    @GetMapping("/task/{taskId}/my-latest")
    @Operation(summary = "Получить последний отправленный ответ текущего пользователя на задачу")
    @PreAuthorize("isAuthenticated()")
    fun getMyLatestSubmissionForTask(@PathVariable taskId: String): ResponseEntity<Submission> {
        val currentUserId = authorizationService.getCurrentUserEntity().id!!
        val pageable = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "submittedAt"))
        val submissionsPage =
            submissionRepository.findByUserIdAndTaskIdOrderBySubmittedAtDesc(currentUserId, taskId, pageable)

        return if (submissionsPage.hasContent()) {
            ResponseEntity.ok(submissionsPage.content[0])
        } else {
            ResponseEntity.notFound().build()
        }
    }
}