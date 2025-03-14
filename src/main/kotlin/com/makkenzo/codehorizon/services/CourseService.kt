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
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service

@Service
class CourseService(
    private val courseRepository: CourseRepository,
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val mongoTemplate: MongoTemplate
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
        val query = Query()
        query.fields().exclude("lessons")

        title?.let { query.addCriteria(Criteria.where("title").regex(".*$it.*", "i")) }
        description?.let { query.addCriteria(Criteria.where("description").regex(".*$it.*", "i")) }
        minRating?.let { query.addCriteria(Criteria.where("rating").gte(it)) }
        maxDuration?.let { query.addCriteria(Criteria.where("duration").lte(it)) }
        category?.let { query.addCriteria(Criteria.where("category").`is`(it)) }
        difficulty?.let { query.addCriteria(Criteria.where("difficulty").`is`(it)) }

        val sort = when (sortBy) {
            "price_asc" -> Sort.by("price").ascending()
            "price_desc" -> Sort.by("price").descending()
            "popular" -> Sort.by("rating").descending()
            else -> Sort.unsorted()
        }
        query.with(sort)
        query.with(pageable)

        val courses = mongoTemplate.find(query, Course::class.java)
        val totalElements = mongoTemplate.count(query, Course::class.java)
        val totalPages =
            (totalElements / pageable.pageSize).toInt() + if (totalElements % pageable.pageSize > 0) 1 else 0

        return PagedResponseDTO(
            content = courses.map { it.toDto() },
            pageNumber = pageable.pageNumber,
            pageSize = pageable.pageSize,
            totalElements = totalElements,
            totalPages = totalPages,
            isLast = pageable.pageNumber >= totalPages - 1
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