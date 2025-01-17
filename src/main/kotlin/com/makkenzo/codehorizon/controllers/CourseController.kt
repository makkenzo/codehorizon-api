package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.CreateCourseRequestDTO
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
@Tag(name = "Course")
class CourseController(private val courseService: CourseService, private val jwtUtils: JwtUtils) {
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
        @RequestBody lesson: Lesson,
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

    @GetMapping
    @Operation(summary = "Получить все курсы", security = [SecurityRequirement(name = "bearerAuth")])
    fun getAllCourses(): ResponseEntity<List<Course>> {
        val courses = courseService.getCourses()
        return ResponseEntity.ok(courses)
    }

    @GetMapping("/author/{authorId}")
    @Operation(summary = "Получить курсы по автору", security = [SecurityRequirement(name = "bearerAuth")])
    fun getCoursesByAuthor(@PathVariable authorId: String): ResponseEntity<List<Course>> {
        val courses = courseService.getCoursesByAuthor(authorId)
        return ResponseEntity.ok(courses)
    }
}