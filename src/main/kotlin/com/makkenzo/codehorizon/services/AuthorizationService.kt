package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.models.Course
import com.makkenzo.codehorizon.models.User
import com.makkenzo.codehorizon.repositories.*
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AuthorizationService(
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val reviewRepository: ReviewRepository,
    private val certificateRepository: CertificateRepository,
    private val courseProgressRepository: CourseProgressRepository
) {
    fun getCurrentAuthentication(): Authentication =
        SecurityContextHolder.getContext().authentication
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Пользователь не аутентифицирован")

    fun getCurrentUserDetails(): UserDetails =
        getCurrentAuthentication().principal as? UserDetails
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Не удалось получить детали пользователя")

    fun getCurrentUserEntity(): User {
        val username = getCurrentUserDetails().username
        return userRepository.findByEmail(username)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Сущность пользователя не найдена для $username")
    }

    fun hasAuthority(authority: String): Boolean =
        getCurrentUserDetails().authorities.contains(SimpleGrantedAuthority(authority))

    fun canReadAnyCourseListForAdmin(): Boolean = hasAuthority("course:read:list:all")

    fun canReadOwnCreatedCourseListForAdmin(): Boolean = hasAuthority("course:read:list:own_created")

    fun canReadAdminCourseDetails(courseId: String): Boolean {
        if (hasAuthority("course:read:details:any")) return true
        if (hasAuthority("course:read:details:own_created")) {
            val course = courseRepository.findById(courseId).orElse(null)
            return course?.authorId == getCurrentUserEntity().id
        }
        return false
    }

    fun canEditCourse(courseId: String): Boolean {
        val course = courseRepository.findById(courseId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Курс $courseId не найден для проверки прав")
        return canEditCourse(course)
    }

    fun canEditCourse(course: Course): Boolean {
        if (hasAuthority("course:edit:any")) return true
        if (hasAuthority("course:edit:own")) {
            return course.authorId == getCurrentUserEntity().id
        }
        return false
    }

    fun canDeleteCourse(courseId: String): Boolean {
        val course = courseRepository.findById(courseId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Курс $courseId не найден для проверки прав")
        return canDeleteCourse(course)
    }

    fun canDeleteCourse(course: Course): Boolean {
        if (hasAuthority("course:delete:any")) return true
        if (hasAuthority("course:delete:own")) {
            return course.authorId == getCurrentUserEntity().id
        }
        return false
    }

    fun canViewCourseStudents(courseId: String): Boolean {
        val course = courseRepository.findById(courseId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Курс $courseId не найден для проверки прав")
        return canViewCourseStudents(course)
    }

    fun canViewCourseStudents(course: Course): Boolean {
        if (hasAuthority("course:view_students:any")) return true
        if (hasAuthority("course:view_students:own")) {
            return course.authorId == getCurrentUserEntity().id
        }
        return false
    }

    fun canAddLessonToCourse(courseId: String): Boolean {
        val course = courseRepository.findById(courseId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Курс $courseId не найден для проверки прав")
        return canAddLessonToCourse(course)
    }

    fun canAddLessonToCourse(course: Course): Boolean {
        if (hasAuthority("lesson:add:any_course")) return true
        if (hasAuthority("lesson:add:own_course")) {
            return course.authorId == getCurrentUserEntity().id
        }
        return false
    }

    fun canEditLessonInCourse(
        courseId: String,
        lessonId: String
    ): Boolean {
        val course = courseRepository.findById(courseId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Курс $courseId не найден для проверки прав")
        return canEditLessonInCourse(course)
    }

    fun canEditLessonInCourse(course: Course): Boolean {
        if (hasAuthority("lesson:edit:any_course")) return true
        if (hasAuthority("lesson:edit:own_course")) {
            return course.authorId == getCurrentUserEntity().id
        }
        return false
    }

    fun canDeleteLessonInCourse(courseId: String, lessonId: String): Boolean {
        val course = courseRepository.findById(courseId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Курс $courseId не найден для проверки прав")
        return canDeleteLessonInCourse(course)
    }

    fun canDeleteLessonInCourse(course: Course): Boolean {
        if (hasAuthority("lesson:delete:any_course")) return true
        if (hasAuthority("lesson:delete:own_course")) {
            return course.authorId == getCurrentUserEntity().id
        }
        return false
    }

    fun isCurrentUserAdmin(): Boolean {
        return getCurrentUserDetails().authorities.any {
            it.authority == "ROLE_ADMIN" || it.authority == "ADMIN"
        }
    }

    fun isCurrentUserMentor(): Boolean {
        return getCurrentUserDetails().authorities.any {
            it.authority == "ROLE_MENTOR" || it.authority == "MENTOR"
        }
    }

    fun canReadSelf(): Boolean = hasAuthority("user:read:self")
    fun canEditSelf(): Boolean = hasAuthority("user:edit:self")
    fun canAdminReadAnyUser(): Boolean = hasAuthority("user:admin:read:any")
    fun canAdminEditAnyUser(): Boolean = hasAuthority("user:admin:edit:any")
    fun canAdminEditRoles(): Boolean = hasAuthority("user:admin:edit_roles")

    fun canEditOwnReview(reviewId: String): Boolean {
        val review = reviewRepository.findById(reviewId).orElse(null) ?: return false
        return hasAuthority("review:edit:own") && review.authorId == getCurrentUserEntity().id
    }

    fun canDeleteOwnReview(reviewId: String): Boolean {
        val review = reviewRepository.findById(reviewId).orElse(null) ?: return false
        return hasAuthority("review:delete:own") && review.authorId == getCurrentUserEntity().id
    }

    fun canDownloadCertificate(certificateId: String): Boolean {
        if (!hasAuthority("certificate:download:self")) return false
        val certificate = certificateRepository.findById(certificateId).orElse(null)
        return certificate?.userId == getCurrentUserEntity().id
    }

    fun isCourseAuthor(courseId: String): Boolean {
        val currentUser = getCurrentUserEntity()
        val course = courseRepository.findById(courseId)
            .orElseThrow { NotFoundException("Курс $courseId не найден для проверки авторства") }
        return course.authorId == currentUser.id
    }

    fun canReadEnrolledCourseContent(courseId: String) {
        val course = courseRepository.findById(courseId)
            .orElseThrow { NotFoundException("Курс $courseId не найден для проверки доступа") }
        if (!courseProgressRepository.existsByUserIdAndCourseId(getCurrentUserEntity().id!!, courseId)) {
            throw AccessDeniedException("У вас нет доступа к этому курсу (вы не записаны).")
        }
    }
}