package com.makkenzo.codehorizon.repositories

import com.makkenzo.codehorizon.models.Submission
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface SubmissionRepository : MongoRepository<Submission, String> {
    fun findByUserIdAndTaskIdOrderBySubmittedAtDesc(
        userId: String,
        taskId: String,
        pageable: Pageable
    ): Page<Submission>

    fun findByUserIdAndLessonId(userId: String, lessonId: String): List<Submission>
    fun findByUserIdAndCourseId(userId: String, courseId: String): List<Submission>
}