package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.AuthorSearchResultDTO
import com.makkenzo.codehorizon.dtos.CourseSearchResultDTO
import com.makkenzo.codehorizon.dtos.GlobalSearchResponseDTO
import com.makkenzo.codehorizon.dtos.SearchResultItemDTO
import com.makkenzo.codehorizon.models.*
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.repositories.ProfileRepository
import com.makkenzo.codehorizon.repositories.UserRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.TextCriteria
import org.springframework.data.mongodb.core.query.TextQuery
import org.springframework.stereotype.Service

@Service
class SearchService(
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
    private val profileRepository: ProfileRepository,
    private val mongoTemplate: MongoTemplate,
    private val authorizationService: AuthorizationService
) {

    fun searchCoursesWithTextIndex(query: String, limit: Int): List<CourseSearchResultDTO> {
        val textCriteria = TextCriteria.forDefaultLanguage().matchingPhrase(query)

        val mongoQuery = TextQuery.query(textCriteria).limit(limit)
            .addCriteria(Criteria.where("deletedAt").`is`(null))

        val courses = mongoTemplate.find(mongoQuery, Course::class.java)

        val authorIds = courses.map { it.authorId }.distinct()
        val authors = userRepository.findAllById(authorIds).associateBy { it.id }

        return courses.map { course ->
            CourseSearchResultDTO(
                id = course.id!!,
                title = course.title,
                slug = course.slug,
                imagePreview = course.imagePreview,
                authorUsername = authors[course.authorId]?.username ?: "N/A"
            )
        }
    }

    fun searchAuthorsCombined(query: String, limit: Int): List<AuthorSearchResultDTO> {
        val userCriteria = Criteria().orOperator(
            Criteria.where("username").regex(query, "i"),
            Criteria.where("email").regex(query, "i")
        )
        val userQuery = Query(userCriteria)
        val usersByName = mongoTemplate.find(userQuery, com.makkenzo.codehorizon.models.User::class.java)
        val userIdsByName = usersByName.mapNotNull { it.id }.toSet()

        val profileCriteria = Criteria().orOperator(
            Criteria.where("firstName").regex(query, "i"),
            Criteria.where("lastName").regex(query, "i")
        )

        val profileQuery = Query(profileCriteria)
        val profilesByName = mongoTemplate.find(profileQuery, Profile::class.java)
        val userIdsFromProfile = profilesByName.map { it.userId }.toSet()

        val allUserIds = (userIdsByName + userIdsFromProfile).toList()

        if (allUserIds.isEmpty()) return emptyList()

        val finalUsers = userRepository.findAllById(allUserIds).associateBy { it.id!! }
        val finalProfiles = profileRepository.findAllById(allUserIds).associateBy { it.userId }


        return allUserIds.mapNotNull { userId ->
            val user = finalUsers[userId] ?: return@mapNotNull null
            val profile = finalProfiles[userId]
            val userSettings = user.accountSettings ?: AccountSettings()

            val currentAuthUser: User? = try {
                authorizationService.getCurrentUserEntity()
            } catch (e: Exception) {
                null
            }
            val isOwner = currentAuthUser?.id == user.id
            val isAdmin = currentAuthUser?.let { authorizationService.isCurrentUserAdmin() } ?: false
            val isRegisteredViewer = currentAuthUser != null

            var bioToShow: String? = null
            if (profile != null) {
                val canViewBio = when {
                    isOwner || isAdmin -> true
                    userSettings.privacySettings.profileVisibility == ProfileVisibility.PRIVATE -> false
                    userSettings.privacySettings.profileVisibility == ProfileVisibility.REGISTERED_USERS -> isRegisteredViewer
                    else -> true
                }
                if (canViewBio) {
                    bioToShow = profile.bio
                }
            }

            AuthorSearchResultDTO(
                userId = user.id!!,
                username = user.username,
                displayName = getDisplayName(user, profile),
                avatarUrl = profile?.avatarUrl,
                avatarColor = profile?.avatarColor,
                bio = bioToShow
            )
        }.take(limit)
    }

    fun globalSearch(query: String, courseLimit: Int = 5, authorLimit: Int = 3): GlobalSearchResponseDTO {
        if (query.length < 2) {
            return GlobalSearchResponseDTO(emptyList())
        }

        val courseResults = searchCoursesWithTextIndex(query, courseLimit)
            .map { SearchResultItemDTO(type = "course", data = it) }

        val authorResults = searchAuthorsCombined(query, authorLimit)
            .map { SearchResultItemDTO(type = "author", data = it) }

        val combinedResults = (courseResults + authorResults)

        return GlobalSearchResponseDTO(results = combinedResults)
    }

    private fun getDisplayName(user: com.makkenzo.codehorizon.models.User, profile: Profile?): String {
        val firstName = profile?.firstName
        val lastName = profile?.lastName
        return when {
            !firstName.isNullOrBlank() && !lastName.isNullOrBlank() -> "$firstName $lastName"
            !firstName.isNullOrBlank() -> firstName
            !lastName.isNullOrBlank() -> lastName
            else -> user.username
        }
    }
}