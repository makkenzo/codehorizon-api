package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.CourseDTO
import com.makkenzo.codehorizon.dtos.LessonRequestDTO
import com.makkenzo.codehorizon.dtos.PagedResponseDTO
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.models.Course
import com.makkenzo.codehorizon.models.CourseDifficultyLevels
import com.makkenzo.codehorizon.models.Lesson
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.repositories.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service

@Service
class CourseService(
    private val courseRepository: CourseRepository,
    private val userService: UserService,
    private val userRepository: UserRepository
) {
    fun createCourse(
        title: String,
        description: String,
        price: Double,
        authorId: String,
        imagePreview: String?,
        videoPreview: String?,
        difficultyLevel: CourseDifficultyLevels
    ): Course {
        val author = userService.findById(authorId) ?: throw IllegalArgumentException("User not found")

        if (!author.roles.contains("ADMIN")) {
            throw AccessDeniedException("Only admins can create courses")
        }

        val course = Course(
            title = title,
            description = description,
            authorId = authorId,
            price = price,
            imagePreview = imagePreview,
            videoPreview = videoPreview,
            difficulty = difficultyLevel
        )
        val savedCourse = courseRepository.save(course)

        author.createdCourseIds.add(savedCourse.id!!)
        userRepository.save(author)

        return savedCourse
    }

    fun addLesson(courseId: String, lessonDto: LessonRequestDTO, authorId: String): Course {
        val author = userService.findById(authorId) ?: throw IllegalArgumentException("User not found")
        if (!author.roles.contains("ADMIN")) {
            throw AccessDeniedException("Only admins can add lessons")
        }
        val course = courseRepository.findById(courseId).orElseThrow { IllegalArgumentException("Course not found") }
        val newLesson = lessonDto.toLesson()
        val updatedCourse = course.copy(lessons = (course.lessons + newLesson).toMutableList())
        return courseRepository.save(updatedCourse)
    }

    fun getCoursesByAuthor(authorId: String): List<Course> {
        return courseRepository.findByAuthorId(authorId)
    }

    fun getCourseById(courseId: String): Course {
        return courseRepository.findById(courseId).orElseThrow { NoSuchElementException("Course not found") }
    }

    fun getCourses(
        title: String?,
        description: String?,
        minRating: Double?,
        maxDuration: Double?,
        category: String?,
        difficulty: CourseDifficultyLevels?,
        sortBy: String?,
        pageable: Pageable
    ): PagedResponseDTO<CourseDTO> {
        val sort = when (sortBy) {
            "price_asc" -> Sort.by("price").ascending()
            "price_desc" -> Sort.by("price").descending()
            "popular" -> Sort.by("rating").descending()
            else -> Sort.unsorted()
        }

        val pageRequest = PageRequest.of(pageable.pageNumber, pageable.pageSize, sort)

        val courses: Page<Course> = if (!title.isNullOrEmpty() || !description.isNullOrEmpty()) {
            courseRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                title ?: "", description ?: "", pageRequest
            )
        } else {
            courseRepository.findAll(pageRequest)
        }

        return PagedResponseDTO(
            content = courses.content.map { it.toDto() },
            pageNumber = courses.number,
            pageSize = courses.size,
            totalElements = courses.totalElements,
            totalPages = courses.totalPages,
            isLast = courses.isLast
        )
    }

    fun getLessonsByCourseId(courseId: String): List<Lesson> {
        val course = getCourseById(courseId)
        return course.lessons
    }


    fun getLessonById(courseId: String, lessonId: String): Lesson {
        val course = getCourseById(courseId)
        return course.lessons.find { it.id == lessonId }
            ?: throw NotFoundException("Lesson not found with id: $lessonId")
    }

    fun updateCourse(courseId: String, title: String, description: String, price: Double, authorId: String): Course {
        val course = getCourseById(courseId)
        if (course.authorId != authorId) {
            throw AccessDeniedException("Only the author can update the course")
        }
        course.title = title
        course.description = description
        course.price = price
        return courseRepository.save(course)
    }

    fun updateLesson(courseId: String, lessonId: String, updatedLesson: LessonRequestDTO, authorId: String): Course {
        val course = getCourseById(courseId)
        if (course.authorId != authorId) {
            throw AccessDeniedException("Only the author can update the course")
        }
        val lesson =
            course.lessons.find { it.id == lessonId } ?: throw NotFoundException("Lesson not found with id: $lessonId")

        lesson.title = updatedLesson.title
        lesson.content = updatedLesson.content
        lesson.tasks = updatedLesson.tasks
        lesson.codeExamples = updatedLesson.codeExamples

        return courseRepository.save(course)
    }

    fun deleteLesson(courseId: String, lessonId: String, authorId: String): Course {
        val course = getCourseById(courseId)
        if (course.authorId != authorId) {
            throw AccessDeniedException("Only the author can update the course")
        }
        val lesson =
            course.lessons.find { it.id == lessonId } ?: throw NotFoundException("Lesson not found with id: $lessonId")
        course.lessons.remove(lesson)
        return courseRepository.save(course)
    }
}