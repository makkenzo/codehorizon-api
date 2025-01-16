package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.models.Course
import com.makkenzo.codehorizon.repositories.CourseRepository
import org.springframework.stereotype.Service

@Service
class CourseService(
    private val courseRepository: CourseRepository
) {
    fun createCourse(title: String, description: String): Course {
        val course = Course(title = title, description = description)
        return courseRepository.save(course)
    }

    fun getCourses(): List<Course> {
        return courseRepository.findAll()
    }
}