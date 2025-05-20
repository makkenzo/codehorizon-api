package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.*
import com.makkenzo.codehorizon.events.UserLeveledUpEvent
import com.makkenzo.codehorizon.events.UserLoggedInEvent
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.models.*
import com.makkenzo.codehorizon.repositories.CourseProgressRepository
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.repositories.ProfileRepository
import com.makkenzo.codehorizon.repositories.UserRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation.*
import org.springframework.data.mongodb.core.aggregation.ConvertOperators
import org.springframework.data.mongodb.core.aggregation.LookupOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
    private val authorizationService: AuthorizationService,
    private val achievementService: AchievementService,
    private val eventPublisher: ApplicationEventPublisher
) {
    companion object {
        const val XP_FOR_LESSON_COMPLETION: Long = 10
        const val XP_FOR_COURSE_COMPLETION: Long = 100
        const val XP_FOR_REVIEW: Long = 15
        const val XP_FOR_DAILY_LOGIN: Long = 5
        const val XP_BASE_FOR_NEXT_LEVEL: Long = 100
        const val STREAK_BONUS_3_DAYS_XP: Long = 25
        const val STREAK_BONUS_7_DAYS_XP: Long = 75
        const val XP_LEVEL_SCALING_FACTOR: Long = 50
    }

    fun calculateXpForNextLevel(level: Int): Long {
        if (level <= 0) return XP_BASE_FOR_NEXT_LEVEL
        return XP_BASE_FOR_NEXT_LEVEL + ((level - 1) * XP_LEVEL_SCALING_FACTOR)
    }

    fun gainXp(userId: String, amount: Long, activityType: String): User? {
        if (amount <= 0) return userRepository.findById(userId).orElse(null)

        val user = userRepository.findById(userId).orElse(null)
            ?: run {
                return null
            }

        val oldLevel = user.level
        user.xp += amount

        var levelsGained = 0
        while (user.xp >= user.xpForNextLevel) {
            user.xp -= user.xpForNextLevel
            user.level += 1
            user.xpForNextLevel = calculateXpForNextLevel(user.level)
            levelsGained++
        }

        val savedUser = userRepository.save(user)

        if (levelsGained > 0) {
            eventPublisher.publishEvent(UserLeveledUpEvent(this, userId, savedUser.level, oldLevel))
        }
        achievementService.checkAndGrantAchievements(userId, AchievementTriggerType.TOTAL_XP_EARNED, user.xp.toInt())

        return savedUser
    }

    @Transactional
    fun recordUserLogin(userId: String): User? {
        val user = userRepository.findById(userId).orElse(null) ?: return null

        val now = Instant.now()
        val today = LocalDate.ofInstant(now, ZoneOffset.UTC)
        val lastLoginDay = user.lastLoginDate?.let { LocalDate.ofInstant(it, ZoneOffset.UTC) }

        if (lastLoginDay == null || !lastLoginDay.isEqual(today)) {
            gainXp(userId, XP_FOR_DAILY_LOGIN, "Daily login bonus")

            if (lastLoginDay != null && lastLoginDay.isEqual(today.minusDays(1))) {
                user.dailyLoginStreak += 1
            } else if (lastLoginDay == null || !lastLoginDay.isEqual(today)) {
                user.dailyLoginStreak = 1
            }

            user.lastLoginDate = now
            val savedUser = userRepository.save(user)

            eventPublisher.publishEvent(UserLoggedInEvent(this, userId))

            when (savedUser.dailyLoginStreak) {
                3 -> gainXp(userId, STREAK_BONUS_3_DAYS_XP, "3-day login streak bonus")
                7 -> gainXp(userId, STREAK_BONUS_7_DAYS_XP, "7-day login streak bonus")
            }
            return savedUser
        }
        return user
    }

    @Transactional
    fun recordLessonCompletionActivity(userId: String): User? {
        val user = userRepository.findById(userId).orElse(null) ?: return null

        user.totalLessonsCompleted += 1

        val now = Instant.now()
        val today = LocalDate.ofInstant(now, ZoneOffset.UTC)
        val lastLessonDay = user.lastLessonActivityDate?.let { LocalDate.ofInstant(it, ZoneOffset.UTC) }

        if (lastLessonDay == null || !lastLessonDay.isEqual(today)) {
            if (lastLessonDay != null && lastLessonDay.isEqual(today.minusDays(1))) {
                user.lessonCompletionStreakDaily += 1
            } else {
                user.lessonCompletionStreakDaily = 1
            }
            user.lastLessonActivityDate = now
        }

        val savedUser = userRepository.save(user)

        achievementService.checkAndGrantAchievements(
            userId,
            AchievementTriggerType.LESSON_COMPLETION_COUNT_TOTAL,
            savedUser.totalLessonsCompleted.toInt()
        )
        achievementService.checkAndGrantAchievements(
            userId,
            AchievementTriggerType.LESSON_COMPLETION_STREAK_DAILY,
            savedUser.lessonCompletionStreakDaily
        )

        return savedUser
    }

    fun findAllUsersAdmin(pageable: Pageable): PagedResponseDTO<AdminUserDTO> {
        val userPage = userRepository.findAll(pageable)
        val userIds = userPage.content.mapNotNull { it.id }

        val profilesMap: Map<String, Profile> = if (userIds.isNotEmpty()) {
            val profileQuery = Query(Criteria.where("userId").`in`(userIds))
            mongoTemplate.find(profileQuery, Profile::class.java).associateBy { it.userId }
        } else {
            emptyMap()
        }

        val userDTOs = userPage.content.map { user ->
            val profile = profilesMap[user.id!!]
            AdminUserDTO(
                id = user.id!!,
                username = user.username,
                email = user.email,
                isVerified = user.isVerified,
                roles = user.roles,
                imageUrl = profile?.avatarUrl,
                createdAt = user.createdAt,
                lastLoginDate = user.lastLoginDate
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

    fun getUserByIdForAdmin(id: String): AdminUserDTO {
        val user = userRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден") }

        val profile = profileRepository.findByUserId(user.id!!)

        return AdminUserDTO(
            id = user.id!!,
            username = user.username,
            email = user.email,
            isVerified = user.isVerified,
            roles = user.roles,
            imageUrl = profile?.avatarUrl,
            createdAt = user.createdAt,
            lastLoginDate = user.lastLoginDate
        )
    }

    fun adminUpdateUser(userId: String, request: AdminUpdateUserRequestDTO): AdminUserDTO {
        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден") }


        if (user.id == authorizationService.getCurrentUserEntity().id &&
            user.roles.contains("ROLE_ADMIN") &&
            !(request.roles?.any { it.equals("ROLE_ADMIN", ignoreCase = true) || it.equals("ADMIN", ignoreCase = true) }
                ?: false)
        ) {
            val adminCount = userRepository.findAll().count { u ->
                u.roles.any {
                    it.equals("ROLE_ADMIN", ignoreCase = true) || it.equals(
                        "ADMIN",
                        ignoreCase = true
                    )
                }
            }
            if (adminCount <= 1) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Нельзя убрать роль последнего администратора.")
            }
        }

        val newRoles = request.roles?.map { role ->
            if (role.startsWith("ROLE_")) role else "ROLE_$role"
        }?.distinct() ?: user.roles

        val updatedUser = user.copy(
            roles = newRoles.filter { it.isNotBlank() },
            isVerified = request.isVerified ?: user.isVerified
        )

        val savedUser = userRepository.save(updatedUser)
        val userProfile = profileService.getProfileByUserId(user.id!!)

        return AdminUserDTO(
            id = savedUser.id!!,
            username = savedUser.username,
            email = savedUser.email,
            isVerified = savedUser.isVerified,
            roles = savedUser.roles,
            imageUrl = userProfile.avatarUrl,
            createdAt = savedUser.createdAt,
            lastLoginDate = savedUser.lastLoginDate
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
        val requestedUser: User = userRepository.findByUsername(username)
            ?: throw NotFoundException("Пользователь '$username' не найден")
        val requestedUserProfile: com.makkenzo.codehorizon.models.Profile =
            profileRepository.findByUserId(requestedUser.id!!)
                ?: throw NotFoundException("Профиль для пользователя '$username' не найден")

        val requestedUserSettings: AccountSettings = requestedUser.accountSettings ?: AccountSettings()
        val privacySettings = requestedUserSettings.privacySettings

        val currentAuthUser: User? = try {
            authorizationService.getCurrentUserEntity()
        } catch (e: ResponseStatusException) {
            null
        }

        val isOwner = currentAuthUser?.id == requestedUser.id
        val isAdmin = currentAuthUser?.let { authorizationService.isCurrentUserAdmin() } ?: false
        val isRegisteredViewer = currentAuthUser != null

        when (privacySettings.profileVisibility) {
            ProfileVisibility.PRIVATE -> {
                if (!isOwner && !isAdmin) {
                    throw org.springframework.security.access.AccessDeniedException("Этот профиль является приватным.")
                }
            }

            ProfileVisibility.REGISTERED_USERS -> {
                if (!isRegisteredViewer) {
                    throw org.springframework.security.access.AccessDeniedException("Этот профиль доступен только зарегистрированным пользователям.")
                }
            }

            ProfileVisibility.PUBLIC -> {
            }
        }

        val finalProfileDto = ProfileDTO(
            firstName = requestedUserProfile.firstName,
            lastName = requestedUserProfile.lastName,
            avatarUrl = requestedUserProfile.avatarUrl,
            avatarColor = requestedUserProfile.avatarColor,
            bio = requestedUserProfile.bio,
            location = requestedUserProfile.location,
            website = if (isOwner || isAdmin || privacySettings.profileVisibility == ProfileVisibility.PUBLIC || (privacySettings.profileVisibility == ProfileVisibility.REGISTERED_USERS)) {
                requestedUserProfile.website
            } else null
        )

        val showCoursesInProgress = when {
            isOwner || isAdmin -> true
            privacySettings.profileVisibility == ProfileVisibility.PRIVATE -> false
            !privacySettings.showCoursesInProgressOnProfile -> false
            privacySettings.profileVisibility == ProfileVisibility.REGISTERED_USERS -> isRegisteredViewer
            else -> true
        }
        val coursesInProgressDTO: List<PublicCourseInfoDTO>? = if (showCoursesInProgress) {
            val progressPageable =
                PageRequest.of(0, 6, Sort.by(Sort.Direction.DESC, "lastUpdated"))
            val progressList = courseProgressRepository.findByUserId(requestedUser.id!!, progressPageable).content
            val courseIdsInProgress = progressList.map { it.courseId }
            if (courseIdsInProgress.isNotEmpty()) {
                val coursesInProgressMap =
                    courseRepository.findAllById(courseIdsInProgress).filter { it.deletedAt == null }
                        .associateBy { it.id }
                progressList.mapNotNull { progress ->
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
            } else emptyList()
        } else null

        val showCompletedCourses = when {
            isOwner || isAdmin -> true
            privacySettings.profileVisibility == ProfileVisibility.PRIVATE -> false
            !privacySettings.showCompletedCoursesOnProfile -> false
            privacySettings.profileVisibility == ProfileVisibility.REGISTERED_USERS -> isRegisteredViewer
            else -> true
        }
        val completedCoursesCount = if (showCompletedCourses) {
            courseProgressRepository.countByUserIdAndProgressGreaterThanEqual(requestedUser.id!!, 100.0)
        } else 0

        val createdCoursesDTO: List<PublicCourseInfoDTO>? = if (requestedUser.createdCourseIds.isNotEmpty()) {
            val createdCourses = courseRepository.findAllById(requestedUser.createdCourseIds)
                .filter { it.deletedAt == null }
                .take(6)
            createdCourses.map { course ->
                PublicCourseInfoDTO(
                    id = course.id!!,
                    title = course.title,
                    slug = course.slug,
                    imagePreview = course.imagePreview,
                    progress = null
                )
            }
        } else null

        return UserProfileDTO(
            id = requestedUser.id!!,
            username = requestedUser.username,
            profile = finalProfileDto,
            coursesInProgress = coursesInProgressDTO?.ifEmpty { null },
            completedCoursesCount = completedCoursesCount,
            createdCourses = createdCoursesDTO?.ifEmpty { null },
            level = requestedUser.level
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

    fun findByUsername(username: String): User? {
        return userRepository.findByUsername(username)
    }

    private fun isSameDay(date1: Instant, date2: Instant): Boolean {
        val d1 = date1.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val d2 = date2.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return d1.isEqual(d2)
    }

    private fun isYesterday(dateToCheck: Instant, currentDate: Instant): Boolean {
        val d1 = dateToCheck.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val d2 = currentDate.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return d1.isEqual(d2.minusDays(1))
    }
}