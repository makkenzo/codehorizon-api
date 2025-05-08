package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.*
import com.makkenzo.codehorizon.models.Profile
import com.makkenzo.codehorizon.models.User
import com.makkenzo.codehorizon.repositories.CourseProgressRepository
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.repositories.ProfileRepository
import com.makkenzo.codehorizon.repositories.UserRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation.*
import org.springframework.data.mongodb.core.aggregation.ConvertOperators
import org.springframework.data.mongodb.core.aggregation.LookupOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.regex.Pattern

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val profileService: ProfileService,
    private val profileRepository: ProfileRepository,
    private val courseRepository: CourseRepository,
    private val courseProgressRepository: CourseProgressRepository,
    private val mongoTemplate: MongoTemplate,
) {
    fun findAllUsersAdmin(pageable: Pageable): PagedResponseDTO<AdminUserDTO> {
        val userPage = userRepository.findAll(pageable)
        val userDTOs = userPage.content.map { user ->
            AdminUserDTO(
                id = user.id!!,
                username = user.username,
                email = user.email,
                isVerified = user.isVerified,
                roles = user.roles
            )
        }
        return PagedResponseDTO(
            content = userDTOs,
            pageNumber = userPage.number,
            pageSize = userPage.size,
            totalElements = userPage.totalElements,
            totalPages = userPage.totalPages,
            isLast = userPage.isLast
        )
    }

    fun adminUpdateUser(userId: String, request: AdminUpdateUserRequestDTO): AdminUserDTO {
        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден") }

        val newRoles = request.roles?.map { role ->
            if (role.startsWith("ROLE_")) role else "ROLE_$role"
        }?.distinct() ?: user.roles

        val updatedUser = user.copy(
            roles = newRoles.filter { it.isNotBlank() },
            isVerified = request.isVerified ?: user.isVerified
        )

        val savedUser = userRepository.save(updatedUser)

        return AdminUserDTO(
            id = savedUser.id!!,
            username = savedUser.username,
            email = savedUser.email,
            isVerified = savedUser.isVerified,
            roles = savedUser.roles
        )
    }

    fun registerUser(username: String, email: String, password: String, confirmPassword: String): User {
        val trimmedUsername = username.trim()

        if (trimmedUsername.isBlank()) {
            throw IllegalArgumentException("Username cannot be empty or contain only whitespace")
        }

        if (trimmedUsername.length < 3 || trimmedUsername.length > 50) {
            throw IllegalArgumentException("Username length must be between 3 and 50 characters")
        }

        if (userRepository.findByEmail(email) != null) {
            throw IllegalArgumentException("Email already exists")
        }

        if (userRepository.findByUsername(trimmedUsername) != null) {
            throw IllegalArgumentException("Username already exists")
        }

        if (password != confirmPassword) {
            throw IllegalArgumentException("Passwords do not match")
        }

        validatePassword(password)

        val user = User(
            username = username,
            email = email,
            passwordHash = passwordEncoder.encode(password)
        )
        val savedUser = userRepository.save(user)

        val profile = Profile(userId = savedUser.id!!)
        profileService.createProfile(profile)

        return savedUser
    }

    fun getUserById(id: String): User = userRepository.findById(id)
        .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден") }

    fun activateAccount(email: String): Boolean {
        val user = userRepository.findByEmail(email) ?: return false

        if (user.isVerified) {
            return true
        }

        user.isVerified = true
        userRepository.save(user)
        return true
    }

    fun resetPassword(user: User, password: String, confirmPassword: String): User {
        if (password != confirmPassword) {
            throw IllegalArgumentException("Passwords do not match")
        }

        validatePassword(password)

        user.passwordHash = passwordEncoder.encode(password)

        return userRepository.save(user)
    }

    private fun validatePassword(password: String) {
        if (password.length < 8) {
            throw IllegalArgumentException("Password must be at least 8 characters long")
        }
        if (!password.contains(Regex("[A-Z]"))) {
            throw IllegalArgumentException("Password must contain at least one uppercase letter")
        }
        if (!password.contains(Regex("[0-9]"))) {
            throw IllegalArgumentException("Password must contain at least one digit")
        }
        if (!password.contains(Regex("[!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]"))) {
            throw IllegalArgumentException("Password must contain at least one special character")
        }
        if (password.contains(Regex("\\s"))) {
            throw IllegalArgumentException("Password cannot contain whitespace characters")
        }

        val pattern = Pattern.compile("^(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,}\$")

        if (!pattern.matcher(password).matches()) {
            throw IllegalArgumentException("Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character")
        }
    }

    fun authenticateUser(login: String, password: String): User? {
        val user = findByLogin(login)
        return if (user != null && passwordEncoder.matches(password, user.passwordHash)) user else null
    }

    fun updateRefreshToken(email: String, refreshToken: String) {
        val user = userRepository.findByEmail(email)
        if (user != null) {
            userRepository.save(user.copy(refreshToken = refreshToken))
        }
    }

    fun findByRefreshToken(refreshToken: String): User? {
        return userRepository.findByRefreshToken(refreshToken)
    }

    fun findById(id: String): User? {
        return userRepository.findById(id).orElse(null)
    }

    fun findByEmail(email: String): User? {
        return userRepository.findByEmail(email)
    }

    fun findByLogin(login: String): User? {
        return userRepository.findByUsernameOrEmail(login, login)
    }

    @Cacheable(value = ["userProfiles"], key = "#username")
    fun getProfileByUsername(username: String): UserProfileDTO {
        val user = userRepository.findByUsername(username) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND, "Профиль с таким юзернеймом не найден"
        )

        val profile = profileRepository.findByUserId(user.id!!)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Профиль пользователя не найден"
            )

        val progressPageable = PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "lastUpdated"))
        val progressList = courseProgressRepository.findByUserId(user.id, progressPageable).content
        val courseIdsInProgress = progressList.map { it.courseId }
        val coursesInProgressMap = courseRepository.findAllById(courseIdsInProgress).associateBy { it.id }

        val coursesInProgressDTO = progressList.mapNotNull { progress ->
            coursesInProgressMap[progress.courseId]?.let { course ->
                PublicCourseInfoDTO(
                    id = course.id!!,
                    title = course.title,
                    slug = course.slug,
                    imagePreview = course.imagePreview,
                    progress = progress.progress
                )
            }
        }

        val completedCount = courseProgressRepository.countByUserIdAndProgressGreaterThanEqual(user.id, 100.0)

        val createdCoursesDTO = if (user.createdCourseIds.isNotEmpty()) {
            val createdCourses = courseRepository.findAllById(user.createdCourseIds)
            createdCourses.map { course ->
                PublicCourseInfoDTO(
                    id = course.id!!,
                    title = course.title,
                    slug = course.slug,
                    imagePreview = course.imagePreview,
                    progress = null
                )
            }
        } else {
            null
        }

        return UserProfileDTO(
            id = user.id,
            username = user.username,
            profile = ProfileDTO(
                firstName = profile.firstName,
                lastName = profile.lastName,
                avatarUrl = profile.avatarUrl,
                avatarColor = profile.avatarColor,
                bio = profile.bio,
                location = profile.location,
                website = profile.website
            ),

            coursesInProgress = coursesInProgressDTO.ifEmpty { null },
            completedCoursesCount = completedCount,
            createdCourses = createdCoursesDTO
        )
    }

    fun getPopularAuthors(limit: Int): List<PopularAuthorDTO> {
        val matchStage = match(Criteria.where("createdCourseIds").exists(true).ne(emptyList<String>()))
        val projectStage1 = project("_id", "username", "createdCourseIds")
            .and("createdCourseIds").size().`as`("courseCount")
        val sortStage = sort(Sort.Direction.DESC, "courseCount")
        val limitStage = limit(limit.toLong())
        val addFieldsStage =
            addFields().addField("userIdStr").withValue(ConvertOperators.ToString.toString("\$_id")).build()
        val lookupStage = LookupOperation.newLookup()
            .from("profiles")
            .localField("userIdStr")
            .foreignField("userId")
            .`as`("authorProfile")
        val unwindStage = unwind("authorProfile", true)
        val projectStageFinal = project("courseCount", "username")
            .and("_id").`as`("userId")
            .and("authorProfile.firstName").`as`("firstName")
            .and("authorProfile.lastName").`as`("lastName")
            .and("authorProfile.avatarUrl").`as`("avatarUrl")
            .and("authorProfile.avatarColor").`as`("avatarColor")
            .andExclude("_id")

        val aggregation = newAggregation(
            matchStage,
            projectStage1,
            sortStage,
            limitStage,
            addFieldsStage,
            lookupStage,
            unwindStage,
            projectStageFinal
        )

        val aggregationResults = mongoTemplate.aggregate(
            aggregation,
            User::class.java,
            PopularAuthorDTO::class.java
        )

        return aggregationResults.mappedResults
    }
}