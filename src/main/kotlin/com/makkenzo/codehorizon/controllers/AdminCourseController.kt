package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.*
import com.makkenzo.codehorizon.services.CourseService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/courses")
@Tag(name = "Admin - Courses", description = "Управление курсами (только для админов)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
class AdminCourseController(private val courseService: CourseService) {
    private fun parseSortParameter(sortBy: String?): Sort {
        if (sortBy.isNullOrBlank()) return Sort.unsorted()
        val parts = sortBy.split("_")
        val property = parts.getOrNull(0) ?: return Sort.unsorted()
        val direction = if (parts.getOrNull(1)?.lowercase() == "desc") Sort.Direction.DESC else Sort.Direction.ASC
        return Sort.by(direction, property)
    }

    @GetMapping
    @Operation(summary = "Получить список курсов (Admin)")
    fun getAllCourses(
        @RequestParam(defaultValue = "1") @Parameter(description = "Номер страницы (начиная с 1)") page: Int,
        @RequestParam(defaultValue = "20") @Parameter(description = "Количество элементов на странице") size: Int,
        @RequestParam(required = false) @Parameter(description = "Поле для сортировки (напр., title_asc, price_desc)") sortBy: String?,
        @RequestParam(required = false) @Parameter(description = "Поиск по названию") titleSearch: String?
    ): ResponseEntity<PagedResponseDTO<AdminCourseListItemDTO>> {
        val pageIndex = if (page > 0) page - 1 else 0
        val sort = parseSortParameter(sortBy)
        val pageable: Pageable = PageRequest.of(pageIndex, size, sort)

        val coursesPage = courseService.findAllCoursesAdmin(pageable, titleSearch)
        return ResponseEntity.ok(coursesPage)
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "Получить детальную информацию о курсе (Admin)")
    fun getCourseDetails(@PathVariable courseId: String): ResponseEntity<AdminCourseDetailDTO> {
        val courseDetails = courseService.getCourseDetailsAdmin(courseId)
        return ResponseEntity.ok(courseDetails)
    }

    @PostMapping
    @Operation(summary = "Создать новый курс (Admin)")
    fun createCourse(@RequestBody request: AdminCreateUpdateCourseRequestDTO): ResponseEntity<AdminCourseDetailDTO> {
        val newCourse = courseService.createCourseAdmin(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(newCourse)
    }

    @PutMapping("/{courseId}")
    @Operation(summary = "Обновить курс (Admin)")
    fun updateCourse(
        @PathVariable courseId: String,
        @RequestBody request: AdminCreateUpdateCourseRequestDTO
    ): ResponseEntity<AdminCourseDetailDTO> {
        val updatedCourse = courseService.updateCourseAdmin(courseId, request)
        return ResponseEntity.ok(updatedCourse)
    }

    @DeleteMapping("/{courseId}")
    @Operation(summary = "Удалить курс (Admin)")
    fun deleteCourse(@PathVariable courseId: String): ResponseEntity<Void> {
        courseService.deleteCourseAdmin(courseId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{courseId}/lessons")
    @Operation(summary = "Добавить урок в курс (Admin)")
    fun addLesson(
        @PathVariable courseId: String,
        @RequestBody lessonDto: AdminCreateUpdateLessonRequestDTO
    ): ResponseEntity<AdminCourseDetailDTO> {
        val updatedCourse = courseService.addLessonAdmin(courseId, lessonDto)
        return ResponseEntity.status(HttpStatus.CREATED).body(updatedCourse)
    }

    @PutMapping("/{courseId}/lessons/{lessonId}")
    @Operation(summary = "Обновить урок (Admin)")
    fun updateLesson(
        @PathVariable courseId: String,
        @PathVariable lessonId: String,
        @RequestBody lessonDto: AdminCreateUpdateLessonRequestDTO
    ): ResponseEntity<AdminCourseDetailDTO> {
        val updatedCourse = courseService.updateLessonAdmin(courseId, lessonId, lessonDto)
        return ResponseEntity.ok(updatedCourse)
    }

    @DeleteMapping("/{courseId}/lessons/{lessonId}")
    @Operation(summary = "Удалить урок (Admin)")
    fun deleteLesson(
        @PathVariable courseId: String,
        @PathVariable lessonId: String
    ): ResponseEntity<Void> {
        courseService.deleteLessonAdmin(courseId, lessonId)
        return ResponseEntity.noContent().build()
    }
}