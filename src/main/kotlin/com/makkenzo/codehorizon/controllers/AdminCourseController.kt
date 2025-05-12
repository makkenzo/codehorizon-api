package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.*
import com.makkenzo.codehorizon.services.CourseService
import com.makkenzo.codehorizon.utils.JwtUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/courses")
@Tag(name = "Admin & Mentor - Courses", description = "Управление курсами (Админ: все, Ментор: свои)")
@SecurityRequirement(name = "bearerAuth")
class AdminCourseController(
    private val courseService: CourseService,
    private val jwtUtils: JwtUtils
) {
    private fun parseSortParameter(sortBy: String?): Sort {
        if (sortBy.isNullOrBlank()) return Sort.unsorted()
        val parts = sortBy.split("_")
        val property = parts.getOrNull(0) ?: return Sort.unsorted()
        val direction = if (parts.getOrNull(1)?.lowercase() == "desc") Sort.Direction.DESC else Sort.Direction.ASC
        return Sort.by(direction, property)
    }

    @GetMapping
    @Operation(summary = "Получить список всех курсов (Только Admin)")
    @PreAuthorize("hasAuthority('course:read:list:all') or hasAuthority('course:read:list:own_created')")
    fun getAllCourses(
        @RequestParam(defaultValue = "1") @Parameter(description = "Номер страницы (начиная с 1)") page: Int,
        @RequestParam(defaultValue = "20") @Parameter(description = "Количество элементов на странице") size: Int,
        @RequestParam(required = false) @Parameter(description = "Поле для сортировки (напр., title_asc, price_desc)") sortBy: String?,
        @RequestParam(required = false) @Parameter(description = "Поиск по названию") titleSearch: String?,
        @RequestParam(required = false) @Parameter(description = "Фильтр по ID автора (для менторов)") authorIdParam: String?
    ): ResponseEntity<PagedResponseDTO<AdminCourseListItemDTO>> {
        val pageIndex = if (page > 0) page - 1 else 0
        val sort = parseSortParameter(sortBy)
        val pageable: Pageable = PageRequest.of(pageIndex, size, sort)

        val coursesPage = courseService.findAllCoursesAdmin(pageable, titleSearch, authorIdParam)
        return ResponseEntity.ok(coursesPage)
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "Получить детальную информацию о курсе (Только Admin)")
    @PreAuthorize("@authorizationService.canReadAdminCourseDetails(#courseId)")
    fun getCourseDetails(@PathVariable courseId: String): ResponseEntity<AdminCourseDetailDTO> {
        val courseDetails = courseService.getCourseDetailsAdmin(courseId)
        return ResponseEntity.ok(courseDetails)
    }

    @PostMapping
    @Operation(summary = "Создать новый курс (Admin или Mentor)")
    @PreAuthorize("hasAuthority('course:create')")
    fun createCourse(
        @Valid @RequestBody request: AdminCreateUpdateCourseRequestDTO
    ): ResponseEntity<AdminCourseDetailDTO> {
        val newCourse = courseService.createCourseAdmin(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(newCourse)
    }

    @PutMapping("/{courseId}")
    @Operation(summary = "Обновить курс (Автор курса или Admin)")
    @PreAuthorize("@authorizationService.canEditCourse(#courseId)")
    fun updateCourse(
        @PathVariable courseId: String,
        @Valid @RequestBody request: AdminCreateUpdateCourseRequestDTO
    ): ResponseEntity<AdminCourseDetailDTO> {
        val updatedCourse = courseService.updateCourseAdminOrMentor(courseId, request)
        return ResponseEntity.ok(updatedCourse)
    }

    @DeleteMapping("/{courseId}")
    @Operation(summary = "Удалить курс (Автор курса или Admin)")
    @PreAuthorize("@authorizationService.canDeleteCourse(#courseId)")
    fun deleteCourse(@PathVariable courseId: String, httpRequest: HttpServletRequest): ResponseEntity<Void> {
        courseService.deleteCourseAdminOrMentor(courseId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{courseId}/lessons")
    @Operation(summary = "Добавить урок в курс (Автор курса или Admin)")
    @PreAuthorize("@authorizationService.canAddLessonToCourse(#courseId)")
    fun addLesson(
        @PathVariable courseId: String,
        @Valid @RequestBody lessonDto: AdminCreateUpdateLessonRequestDTO
    ): ResponseEntity<AdminCourseDetailDTO> {
        val updatedCourse = courseService.addLessonAdminOrMentor(courseId, lessonDto)
        return ResponseEntity.status(HttpStatus.CREATED).body(updatedCourse)
    }

    @PutMapping("/{courseId}/lessons/{lessonId}")
    @Operation(summary = "Обновить урок (Автор курса или Admin)")
    @PreAuthorize("@authorizationService.canEditLessonInCourse(#courseId, #lessonId)")
    fun updateLesson(
        @PathVariable courseId: String,
        @PathVariable lessonId: String,
        @Valid @RequestBody lessonDto: AdminCreateUpdateLessonRequestDTO
    ): ResponseEntity<AdminCourseDetailDTO> {
        val updatedCourse = courseService.updateLessonAdminOrMentor(courseId, lessonId, lessonDto)
        return ResponseEntity.ok(updatedCourse)
    }

    @DeleteMapping("/{courseId}/lessons/{lessonId}")
    @Operation(summary = "Удалить урок (Автор курса или Admin)")
    @PreAuthorize("@authorizationService.canDeleteLessonInCourse(#courseId, #lessonId)")
    fun deleteLesson(
        @PathVariable courseId: String,
        @PathVariable lessonId: String
    ): ResponseEntity<Void> {
        courseService.deleteLessonAdminOrMentor(courseId, lessonId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{courseId}/students")
    @Operation(summary = "Получить прогресс студентов курса (Автор или Admin)")
    @PreAuthorize("@authorizationService.canViewCourseStudents(#courseId)")
    fun getCourseStudentsProgress(
        @PathVariable courseId: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(defaultValue = "lastAccessedLessonAt,desc") sort: String
    ): ResponseEntity<PagedResponseDTO<StudentProgressDTO>> {
        val pageIndex = if (page > 0) page - 1 else 0

        val sortParts = sort.split(',')
        val direction = if (sortParts.size > 1 && sortParts[1].equals(
                "asc",
                ignoreCase = true
            )
        ) Sort.Direction.ASC else Sort.Direction.DESC
        val property = if (sortParts.isNotEmpty()) sortParts[0] else "lastAccessedLessonAt"
        val pageable: Pageable = PageRequest.of(pageIndex, size, Sort.by(direction, property))

        val studentsPagedResponse = courseService.getStudentProgressForCourse(courseId, pageable)

        return ResponseEntity.ok(studentsPagedResponse)
    }
}