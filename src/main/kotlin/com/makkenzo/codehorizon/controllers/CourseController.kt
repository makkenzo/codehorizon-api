package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.CreateCourseRequestDTO
import com.makkenzo.codehorizon.dtos.LessonRequestDTO
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.models.Course
import com.makkenzo.codehorizon.models.Lesson
import com.makkenzo.codehorizon.services.CourseService
import com.makkenzo.codehorizon.utils.JwtUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/courses")
@Tag(name = "Course", description = "Курсы")
class CourseController(private val courseService: CourseService, private val jwtUtils: JwtUtils) {
    @GetMapping
    @Operation(summary = "Получить все курсы", security = [SecurityRequirement(name = "bearerAuth")])
    fun getAllCourses(): ResponseEntity<List<Course>> {
        val courses = courseService.getCourses()
        return ResponseEntity.ok(courses)
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "Получить курс по ID", security = [SecurityRequirement(name = "bearerAuth")])
    fun getCourseById(@PathVariable courseId: String): ResponseEntity<Course> {
        return try {
            val course = courseService.getCourseById(courseId)
            ResponseEntity.ok(course)
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        }
    }

    @GetMapping("/author/{authorId}")
    @Operation(summary = "Получить курсы по автору", security = [SecurityRequirement(name = "bearerAuth")])
    fun getCoursesByAuthor(@PathVariable authorId: String): ResponseEntity<List<Course>> {
        val courses = courseService.getCoursesByAuthor(authorId)
        return ResponseEntity.ok(courses)
    }

    @PostMapping
    @Operation(summary = "Создание нового курса", security = [SecurityRequirement(name = "bearerAuth")])
    fun createCourse(
        @RequestBody requestBody: CreateCourseRequestDTO,
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        return try {
            val token =
                request.getHeader("Authorization") ?: throw IllegalArgumentException("Authorization header is missing")
            val authorId =
                jwtUtils.getAuthorIdFromToken(token.substring(7).trim()) // Получаем ID пользователя из токена
            val course = courseService.createCourse(requestBody.title, requestBody.description, authorId)
            ResponseEntity.ok(course)
        } catch (e: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to e.message))
        } catch (e: IllegalArgumentException) {
            println(e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/{courseId}/lessons")
    @Operation(summary = "Добавить лекцию в курс", security = [SecurityRequirement(name = "bearerAuth")])
    fun addLesson(
        @PathVariable courseId: String,
        @RequestBody lesson: LessonRequestDTO,
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        return try {
            val token =
                request.getHeader("Authorization") ?: throw IllegalArgumentException("Authorization header is missing")
            val authorId =
                jwtUtils.getAuthorIdFromToken(token.substring(7).trim()) // Получаем ID пользователя из токена
            val updatedCourse = courseService.addLesson(courseId, lesson, authorId)
            ResponseEntity.ok(updatedCourse)
        } catch (e: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to e.message))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        }
    }

    @PutMapping("/{courseId}")
    @Operation(summary = "Обновить курс", security = [SecurityRequirement(name = "bearerAuth")])
    fun updateCourse(
        @PathVariable courseId: String,
        @RequestBody requestBody: CreateCourseRequestDTO,
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        return try {
            val token =
                request.getHeader("Authorization") ?: throw IllegalArgumentException("Authorization header is missing")
            val authorId = jwtUtils.getAuthorIdFromToken(token.substring(7).trim())
            val updatedCourse =
                courseService.updateCourse(courseId, requestBody.title, requestBody.description, authorId)
            ResponseEntity.ok(updatedCourse)
        } catch (e: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to e.message))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "Course not found"))
        }
    }

    @PutMapping("/{courseId}/lessons/{lessonId}")
    @Operation(summary = "Обновить лекцию в курсе", security = [SecurityRequirement(name = "bearerAuth")])
    fun updateLesson(
        @PathVariable courseId: String,
        @PathVariable lessonId: String,
        @RequestBody updatedLesson: LessonRequestDTO,
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        return try {
            val token =
                request.getHeader("Authorization") ?: throw IllegalArgumentException("Authorization header is missing")
            val authorId = jwtUtils.getAuthorIdFromToken(token.substring(7).trim())
            val updatedCourse = courseService.updateLesson(courseId, lessonId, updatedLesson, authorId)

            ResponseEntity.ok(updatedCourse)
        } catch (e: AccessDeniedException) {
            ResponseEntity.status((HttpStatus.FORBIDDEN)).body(mapOf("error" to e.message))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        } catch (e: NotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        }
    }

    @DeleteMapping("/{courseId}/lessons/{lessonId}")
    @Operation(summary = "Удалить лекцию из курса", security = [SecurityRequirement(name = "bearerAuth")])
    fun deleteLesson(
        @PathVariable courseId: String,
        @PathVariable lessonId: String,
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        return try {
            val token =
                request.getHeader("Authorization") ?: throw IllegalArgumentException("Authorization header is missing")
            val authorId = jwtUtils.getAuthorIdFromToken(token.substring(7).trim())
            val updatedCourse = courseService.deleteLesson(courseId, lessonId, authorId)
            ResponseEntity.ok(updatedCourse)
        } catch (e: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to e.message))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        } catch (e: NotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/{courseId}/lessons")
    @Operation(summary = "Получить все лекции в курсе", security = [SecurityRequirement(name = "bearerAuth")])
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
    @Operation(summary = "Получить лекцию по ID", security = [SecurityRequirement(name = "bearerAuth")])
    fun getLessonById(@PathVariable courseId: String, @PathVariable lessonId: String): ResponseEntity<Lesson> {
        return try {
            val lesson = courseService.getLessonById(courseId, lessonId)
            ResponseEntity.ok(lesson)
        } catch (e: NotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(null)
        }
    }
}