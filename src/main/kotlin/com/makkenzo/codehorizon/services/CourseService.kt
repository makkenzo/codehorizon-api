package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.models.Course
import com.makkenzo.codehorizon.models.Lesson
import com.makkenzo.codehorizon.repositories.CourseRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service

@Service
class CourseService(
    private val courseRepository: CourseRepository,
    private val userService: UserService
) {
    fun createCourse(title: String, description: String, authorId: String): Course {
        val author = userService.findById(authorId) ?: throw IllegalArgumentException("User not found")
        if (!author.roles.contains("ADMIN")) {
            throw AccessDeniedException("Only admins can create courses")
        }
        val course = Course(title = title, description = description, authorId = authorId)
        return courseRepository.save(course)
    }

    fun addLesson(courseId: String, lesson: Lesson, authorId: String): Course {
        val author = userService.findById(authorId) ?: throw IllegalArgumentException("User not found")
        if (!author.roles.contains("ADMIN")) {
            throw AccessDeniedException("Only admins can add lessons")
        }
        val course = courseRepository.findById(courseId).orElseThrow { IllegalArgumentException("Course not found") }
        val updatedCourse = course.copy(lessons = course.lessons + lesson)
        return courseRepository.save(updatedCourse)
    }

    fun getCoursesByAuthor(authorId: String): List<Course> {
        return courseRepository.findByAuthorId(authorId)
    }

    fun getCourses(): List<Course> {
        return courseRepository.findAll()
    }

    
}