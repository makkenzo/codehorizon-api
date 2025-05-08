package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.CourseDTO
import com.makkenzo.codehorizon.dtos.CourseWithoutContentDTO
import com.makkenzo.codehorizon.dtos.MessageResponseDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.models.Course
import com.makkenzo.codehorizon.models.CourseDifficultyLevels
import com.makkenzo.codehorizon.models.CourseProgress
import com.makkenzo.codehorizon.models.Lesson
import com.makkenzo.codehorizon.services.CloudflareService
import com.makkenzo.codehorizon.services.CourseProgressService
import com.makkenzo.codehorizon.services.CourseService
import com.makkenzo.codehorizon.utils.JwtUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/courses")
@Tag(name = "Course", description = "Курсы")
class CourseController(
    private val courseService: CourseService,
    private val jwtUtils: JwtUtils,
    private val cloudflareService: CloudflareService,
    private val courseProgressService: CourseProgressService
) {
    @GetMapping
    @Operation(summary = "Получить все курсы")
    fun getAllCourses(
        @RequestParam(required = false) title: String?,
        @RequestParam(required = false) description: String?,
        @RequestParam(required = false) minRating: Double?,
        @RequestParam(required = false) minDuration: Double?,
        @RequestParam(required = false) maxDuration: Double?,
        @RequestParam(required = false) category: List<String>?,
        @RequestParam(required = false) difficulty: List<CourseDifficultyLevels>?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<PagedResponseDTO<CourseDTO>> {
        val pageable: Pageable = PageRequest.of(page - 1, size)

        val courses = courseService.getCourses(
            title, description, minRating, minDuration, maxDuration, category, difficulty, sortBy, pageable
        )

        return ResponseEntity.ok(courses)
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Получить курс по slug")
    fun getCourseById(@PathVariable slug: String): ResponseEntity<CourseWithoutContentDTO> {
        return try {
            val course = courseService.getCourseBySlug(slug)
            ResponseEntity.ok(course)
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        }
    }

    @GetMapping("/author/{authorId}")
    @Operation(summary = "Получить курсы по автору")
    fun getCoursesByAuthor(@PathVariable authorId: String): ResponseEntity<List<Course>> {
        val courses = courseService.getCoursesByAuthor(authorId)
        return ResponseEntity.ok(courses)
    }

    @GetMapping("/{courseId}/lessons")
    @Operation(summary = "Получить все лекции в курсе")
    fun getLessonsByCourseId(@PathVariable courseId: String): ResponseEntity<List<Lesson>> {
        return try {
            val lessons = courseService.getLessonsByCourseId(courseId)
            ResponseEntity.ok(lessons)
        } catch (e: NotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(emptyList())
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(emptyList())
        }
    }

    @GetMapping("/{courseId}/lessons/{lessonId}")
    @Operation(summary = "Получить лекцию по ID")
    fun getLessonById(@PathVariable courseId: String, @PathVariable lessonId: String): ResponseEntity<Lesson> {
        return try {
            val lesson = courseService.getLessonById(courseId, lessonId)
            ResponseEntity.ok(lesson)
        } catch (e: NotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        }
    }

//    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
//    @Operation(summary = "Создание нового курса", security = [SecurityRequirement(name = "bearerAuth")])
//    fun createCourse(
//        @RequestParam("title") title: String,
//        @RequestParam("description") description: String,
//        @RequestParam("price") price: Double,
//        @RequestParam("difficultyLevel") difficultyLevel: CourseDifficultyLevels,
//        @RequestParam("category") category: String,
//        @Parameter(
//            description = "Файл превью для изображения",
//            schema = Schema(type = "string", format = "binary")
//        )
//        @RequestPart("imagePreview", required = false) imageFile: MultipartFile?,
//        @Parameter(
//            description = "Файл превью для видео",
//            schema = Schema(type = "string", format = "binary")
//        )
//        @RequestPart("videoPreview", required = false) videoFile: MultipartFile?,
//        request: HttpServletRequest
//    ): ResponseEntity<Any> {
//        return try {
//            val token = request.cookies?.find { it.name == "access_token" }?.value
//                ?: throw IllegalArgumentException("Access token cookie is missing")
//            val authorId =
//                jwtUtils.getIdFromToken(token)
//
//            val imageUrl = imageFile?.let { cloudflareService.uploadFileToR2(it, "course_images") }
//            val videoUrl = videoFile?.let { cloudflareService.uploadFileToR2(it, "course_videos") }
//
//            val course =
//                courseService.createCourse(
//                    title,
//                    description,
//                    price,
//                    authorId,
//                    category,
//                    imageUrl,
//                    videoUrl,
//                    difficultyLevel
//                )
//            ResponseEntity.ok(course)
//        } catch (e: AccessDeniedException) {
//            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to e.message))
//        } catch (e: IllegalArgumentException) {
//            println(e.message)
//            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
//        }
//    }

    @GetMapping("/categories")
    @Operation(summary = "Получить список уникальных категорий курсов")
    fun getCategories(): ResponseEntity<List<String>> {
        val categories = courseService.getDistinctCategories()
        return ResponseEntity.ok(categories)
    }

    @GetMapping("/{courseId}/learn-content")
    @Operation(
        summary = "Получить полный контент курса для обучения",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    fun getCourseLearnContent(
        @PathVariable courseId: String,
        request: HttpServletRequest
    ): ResponseEntity<Course> {
        return try {
            val token = request.cookies?.find { it.name == "access_token" }?.value
                ?: throw IllegalArgumentException("Access token cookie is missing")
            val userId = jwtUtils.getIdFromToken(token)

            val course = courseService.getAccessibleCourseForLearning(courseId, userId)
            ResponseEntity.ok(course)
        } catch (e: AccessDeniedException) {
            throw e
        } catch (e: NotFoundException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, e.message)
        }
    }

    @PostMapping("/{courseId}/enroll")
    @Operation(
        summary = "Записаться на бесплатный курс",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    fun enrollOnCourse(
        @PathVariable courseId: String,
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        return try {
            val token = request.cookies?.find { it.name == "access_token" }?.value
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(MessageResponseDTO("Требуется авторизация"))
            val userId = jwtUtils.getIdFromToken(token)

            courseService.enrollFreeCourse(userId, courseId)
            ResponseEntity.ok(MessageResponseDTO("Вы успешно записаны на курс!"))

        } catch (e: NotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(MessageResponseDTO(e.message ?: "Курс не найден"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(MessageResponseDTO(e.message ?: "Ошибка записи на курс"))
        } catch (e: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(MessageResponseDTO("Доступ запрещен"))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MessageResponseDTO("Внутренняя ошибка сервера"))
        }
    }

    @PostMapping("/{courseId}/lessons/{lessonId}/complete")
    @Operation(
        summary = "Отметить урок как пройденный",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    fun markLessonComplete(
        @PathVariable courseId: String,
        @PathVariable lessonId: String,
        request: HttpServletRequest
    ): ResponseEntity<CourseProgress> {
        return try {
            val token = request.cookies?.find { it.name == "access_token" }?.value
                ?: throw IllegalArgumentException("Access token cookie is missing")
            val userId = jwtUtils.getIdFromToken(token)

            val updatedProgress = courseProgressService.markLessonAsComplete(userId, courseId, lessonId)
            ResponseEntity.ok(updatedProgress)
        } catch (e: NotFoundException) {
            throw e
        } catch (e: AccessDeniedException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, e.message)
        }
    }

    @GetMapping("/{courseId}/progress")
    @Operation(
        summary = "Получить прогресс пользователя по курсу",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    fun getUserCourseProgress(
        @PathVariable courseId: String,
        request: HttpServletRequest
    ): ResponseEntity<CourseProgress> {
        return try {
            val token = request.cookies?.find { it.name == "access_token" }?.value
                ?: throw IllegalArgumentException("Access token cookie is missing")
            val userId = jwtUtils.getIdFromToken(token)
            val progress = courseProgressService.getUserProgressByCourse(userId, courseId)
                ?: return ResponseEntity.notFound().build()
            ResponseEntity.ok(progress)
        } catch (e: NotFoundException) {
            return ResponseEntity.notFound().build()
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, e.message)
        }
    }
}