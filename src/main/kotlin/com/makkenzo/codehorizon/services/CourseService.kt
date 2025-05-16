package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.*
import com.makkenzo.codehorizon.events.CourseCreatedByMentorEvent
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.models.*
import com.makkenzo.codehorizon.repositories.CourseProgressRepository
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.repositories.UserRepository
import com.makkenzo.codehorizon.utils.SlugUtils
import com.mongodb.client.model.Filters
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.aggregation.ArrayOperators
import org.springframework.data.mongodb.core.aggregation.ConvertOperators
import org.springframework.data.mongodb.core.aggregation.ConvertOperators.ToString
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.*

@Service
class CourseService(
    private val courseRepository: CourseRepository,
    private val userRepository: UserRepository,
    private val mongoTemplate: MongoTemplate,
    private val courseProgressRepository: CourseProgressRepository,
    private val mediaProcessingService: MediaProcessingService,
    private val cloudflareService: CloudflareService,
    private val authorizationService: AuthorizationService,
    private val eventPublisher: ApplicationEventPublisher
) {
    fun findAllCoursesAdmin(
        pageable: Pageable,
        titleSearch: String?,
        authorIdParamFromRequest: String?
    ): PagedResponseDTO<AdminCourseListItemDTO> {
        val currentUser = authorizationService.getCurrentUserEntity()
        val canReadAny = authorizationService.canReadAnyCourseListForAdmin()
        val canReadOwn = authorizationService.canReadOwnCreatedCourseListForAdmin()

        val effectiveAuthorIdFilter: String?
        if (canReadAny) {
            effectiveAuthorIdFilter = authorIdParamFromRequest
        } else if (canReadOwn) {
            effectiveAuthorIdFilter = currentUser.id!!
        } else {
            throw AccessDeniedException("У вас нет прав для просмотра этого списка курсов.")
        }

        val criteria = Criteria.where("deletedAt").`is`(null)
        titleSearch?.let {
            criteria.and("title").regex(it, "i")
        }
        effectiveAuthorIdFilter?.let { criteria.and("authorId").`is`(it) }

        val query = Query(criteria).with(pageable)
        val totalElements = mongoTemplate.count(Query(criteria), Course::class.java)
        val courses = mongoTemplate.find(query, Course::class.java)

        val authorIds = courses.map { it.authorId }.distinct()
        val authors = userRepository.findAllById(authorIds).associateBy { it.id }

        val courseDTOs = courses.map { course ->
            val author = authors[course.authorId]
            AdminCourseListItemDTO(
                id = course.id!!,
                title = course.title,
                slug = course.slug,
                authorUsername = author?.username ?: "N/A",
                price = course.price,
                discount = course.discount,
                difficulty = course.difficulty,
                category = course.category,
                lessonCount = course.lessons.size,
                imagePreview = course.imagePreview
            )
        }

        val page = PageImpl(courseDTOs, pageable, totalElements)

        return PagedResponseDTO(
            content = page.content,
            pageNumber = page.number,
            pageSize = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            isLast = page.isLast
        )
    }

    fun getCourseDetailsAdmin(courseId: String): AdminCourseDetailDTO {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Курс не найден") }

        val author = userRepository.findById(course.authorId)
            .orElse(null)

        return AdminCourseDetailDTO(
            id = course.id!!,
            title = course.title,
            slug = course.slug,
            description = course.description,
            imagePreview = course.imagePreview,
            videoPreview = course.videoPreview,
            authorId = course.authorId,
            authorUsername = author?.username ?: "N/A",
            price = course.price,
            discount = course.discount,
            isFree = course.isFree,
            difficulty = course.difficulty,
            category = course.category,
            videoLength = course.videoLength,
            lessons = course.lessons,
            featuresBadge = course.featuresBadge,
            featuresTitle = course.featuresTitle,
            featuresSubtitle = course.featuresSubtitle,
            featuresDescription = course.featuresDescription,
            features = course.features,
            benefitTitle = course.benefitTitle,
            benefitDescription = course.benefitDescription,
            testimonial = course.testimonial
        )
    }

    @Cacheable("distinctCategories")
    fun getDistinctCategories(): List<String> {
        val query = Query().addCriteria(Criteria.where("category").ne(null).ne(""))
        val categories = mongoTemplate.findDistinct(query, "category", Course::class.java, String::class.java)
        return categories.sorted()
    }

    @CacheEvict(value = ["courses"], allEntries = true)
    @Transactional
    fun createCourseAdmin(request: AdminCreateUpdateCourseRequestDTO): AdminCourseDetailDTO {
        val creatorUser = authorizationService.getCurrentUserEntity()
        var effectiveAuthorId = request.authorId

        if (authorizationService.isCurrentUserMentor() && !authorizationService.isCurrentUserAdmin()) {
            if (request.authorId.isNotBlank() && request.authorId != creatorUser.id) {
                throw AccessDeniedException("Менторы могут создавать курсы только от своего имени.")
            }
            effectiveAuthorId = creatorUser.id!!
        } else if (authorizationService.isCurrentUserAdmin()) {
            if (request.authorId.isBlank()) {
                throw IllegalArgumentException("Администратор должен указать автора курса при создании.")
            }
            userRepository.findById(request.authorId)
                .orElseThrow {
                    ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Автор с ID ${request.authorId} не найден"
                    )
                }
        } else {
            throw AccessDeniedException("У вас нет прав для создания курса.")
        }

        val author = userRepository.findById(effectiveAuthorId)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "Автор с ID $effectiveAuthorId не найден") }

        val isFreeCourse = request.isFree ?: false

        val slug = SlugUtils.generateUniqueSlug(request.title) { courseRepository.existsBySlug(it) }

        val newCourse = Course(
            title = request.title,
            slug = slug,
            description = request.description,
            price = if (isFreeCourse) 0.0 else request.price,
            discount = if (isFreeCourse) 0.0 else request.discount ?: 0.0,
            isFree = isFreeCourse,
            authorId = effectiveAuthorId,
            difficulty = request.difficulty,
            category = request.category,
            imagePreview = request.imagePreview,
            videoPreview = request.videoPreview,
            lessons = mutableListOf(),
            featuresBadge = request.featuresBadge ?: "Ключевые темы",
            featuresTitle = request.featuresTitle,
            featuresSubtitle = request.featuresSubtitle,
            featuresDescription = request.featuresDescription,
            features = request.features ?: emptyList(),
            benefitTitle = request.benefitTitle,
            benefitDescription = request.benefitDescription,
            testimonial = request.testimonial
        )

        val savedCourse = courseRepository.save(newCourse)

        if (!author.createdCourseIds.contains(savedCourse.id!!)) {
            author.createdCourseIds.add(savedCourse.id)
            userRepository.save(author)

            val courseCount = author.createdCourseIds.size
            if (author.roles.any {
                    it.equals("ROLE_MENTOR", ignoreCase = true) || it.equals(
                        "MENTOR",
                        ignoreCase = true
                    )
                }) {
                eventPublisher.publishEvent(
                    CourseCreatedByMentorEvent(
                        this,
                        author.id!!,
                        savedCourse.id!!
                    )
                )
            }
        }

        mediaProcessingService.updateCourseVideoLengthAsync(savedCourse.id!!)
        return getCourseDetailsAdmin(savedCourse.id!!)
    }

    fun findByIds(courseIds: List<String>): List<CourseDTO> {
        if (courseIds.isEmpty()) return emptyList()

        val matchStage = Aggregation.match(Criteria.where("_id").`in`(courseIds).and("deletedAt").`is`(null))

        val lookupStage = Aggregation.lookup("profiles", "authorId", "userId", "authorProfile")
        val addFieldsStage = Aggregation.addFields()
            .addField("authorIdObj")
            .withValue(ConvertOperators.valueOf("authorId").convertToObjectId())
            .build()
        val lookupUsersStage = Aggregation.lookup("users", "authorIdObj", "_id", "authorUser")

        val projectStage = Aggregation.project(
            "title",
            "slug",
            "description",
            "imagePreview",
            "videoPreview",
            "authorId",
            "rating",
            "price",
            "discount",
            "difficulty",
            "category",
            "videoLength",
        )
            .and(ArrayOperators.ArrayElemAt.arrayOf("\$authorProfile.firstName").elementAt(0)).`as`("authorFirstName")
            .and(ArrayOperators.ArrayElemAt.arrayOf("\$authorProfile.lastName").elementAt(0)).`as`("authorLastName")
            .and(ArrayOperators.ArrayElemAt.arrayOf("\$authorUser.username").elementAt(0)).`as`("authorUsername")
            .and(ToString.toString("_id")).`as`("id")

        val aggregation =
            Aggregation.newAggregation(matchStage, lookupStage, addFieldsStage, lookupUsersStage, projectStage)
        val aggregationResult = mongoTemplate.aggregate(aggregation, "courses", Document::class.java)

        return aggregationResult.mappedResults.map { doc ->
            val authorFirstName = doc.getString("authorFirstName") ?: ""
            val authorLastName = doc.getString("authorLastName") ?: ""
            val authorName = if (authorFirstName.isBlank() && authorLastName.isBlank()) "Неизвестный автор"
            else "$authorFirstName $authorLastName".trim()
            val authorUsername = doc.getString("authorUsername") ?: "Неизвестный пользователь"

            CourseDTO(
                id = doc.get("_id").toString(),
                slug = doc.getString("slug"),
                title = doc.getString("title") ?: "",
                description = doc.getString("description") ?: "",
                imagePreview = doc.getString("imagePreview"),
                videoPreview = doc.getString("videoPreview"),
                authorId = doc.getString("authorId") ?: "",
                rating = doc.getDouble("rating") ?: 0.0,
                price = doc.getDouble("price") ?: 0.0,
                discount = doc.getDouble("discount") ?: 0.0,
                difficulty = doc.getString("difficulty")?.let { CourseDifficultyLevels.valueOf(it) }
                    ?: CourseDifficultyLevels.BEGINNER,
                authorName = authorName,
                authorUsername = authorUsername,
                category = doc.getString("category") ?: "Без категории",
                videoLength = doc.getDouble("videoLength") ?: 0.0
            )
        }
    }

    fun getCoursesByAuthor(authorId: String): List<Course> {
        return courseRepository.findByAuthorIdAndDeletedAtIsNull(authorId)
    }

    @Cacheable(value = ["courses"], key = "#courseId")
    fun getCourseById(courseId: String): Course {
        return courseRepository.findByIdAndDeletedAtIsNull(courseId)
            ?: throw NotFoundException("Course not found with id: $courseId")
    }

    @Cacheable(value = ["courses"], key = "#slug")
    fun getCourseBySlug(slug: String): CourseWithoutContentDTO {
        val course = courseRepository.findBySlugAndDeletedAtIsNull(slug)
            ?: throw NotFoundException("Course not found with slug: $slug")

        val profileDoc = mongoTemplate.getCollection("profiles")
            .find(Filters.eq("userId", course.authorId))
            .firstOrNull()

        val firstName = profileDoc?.getString("firstName") ?: ""
        val lastName = profileDoc?.getString("lastName") ?: ""
        val authorName =
            if (firstName.isBlank() && lastName.isBlank()) "Неизвестный автор" else "$firstName $lastName".trim()

        val userDoc = mongoTemplate.getCollection("users")
            .find(Filters.eq("_id", ObjectId(course.authorId)))
            .firstOrNull()

        val authorUsername = userDoc?.getString("username") ?: "Неизвестный пользователь"

        return CourseWithoutContentDTO(
            id = course.id.toString(),
            slug = course.slug,
            category = course.category,
            title = course.title,
            description = course.description,
            imagePreview = course.imagePreview,
            videoPreview = course.videoPreview,
            rating = course.rating,
            difficulty = course.difficulty,
            videoLength = course.videoLength,
            price = course.price,
            isFree = course.isFree,
            authorName = authorName,
            authorUsername = authorUsername,
            discount = course.discount,
            lessons = course.lessons.map {
                LessonWithoutContent(
                    id = it.id,
                    slug = it.slug ?: "",
                    title = it.title,
                    videoLength = it.videoLength,
                )
            },
            featuresBadge = course.featuresBadge,
            features = course.features,
            featuresSubtitle = course.featuresSubtitle,
            featuresTitle = course.featuresTitle,
            featuresDescription = course.featuresDescription,
            benefitTitle = course.benefitTitle,
            benefitDescription = course.benefitDescription,
            testimonial = course.testimonial,
        )
    }

    @Cacheable(value = ["courses"])
    fun getCourses(
        title: String?,
        description: String?,
        minRating: Double?,
        minDuration: Double?,
        maxDuration: Double?,
        category: List<String>?,
        difficulty: List<CourseDifficultyLevels>?,
        isFreeFilter: Boolean?,
        sortBy: String?,
        pageable: Pageable
    ): PagedResponseDTO<CourseDTO> {
        val criteria = mutableListOf<Criteria>()

        criteria.add(Criteria.where("deletedAt").`is`(null))

        title?.let { criteria.add(Criteria.where("title").regex(".*$it.*", "i")) }
        description?.let { criteria.add(Criteria.where("description").regex(".*$it.*", "i")) }
        minRating?.let { criteria.add(Criteria.where("rating").gte(it)) }
        category?.let { criteria.add(Criteria.where("category").`in`(it)) }
        difficulty?.let { criteria.add(Criteria.where("difficulty").`in`(it)) }
        if (isFreeFilter != null) {
            if (isFreeFilter) {
                criteria.add(Criteria.where("isFree").`is`(true))
            } else {
                criteria.add(
                    Criteria().orOperator(
                        Criteria.where("isFree").`is`(false),
                        Criteria.where("isFree").`is`(null),
                        Criteria.where("isFree").exists(false)
                    )
                )
            }
        }

        val minDurationInSeconds = minDuration?.let { it * 3600 }
        val maxDurationInSeconds = maxDuration?.let { it * 3600 }

        val durationCriteria = mutableListOf<Criteria>()
        minDurationInSeconds?.let { durationCriteria.add(Criteria.where("videoLength").gte(it)) }
        maxDurationInSeconds?.let { durationCriteria.add(Criteria.where("videoLength").lte(it)) }

        if (durationCriteria.isNotEmpty()) {
            if (durationCriteria.size == 1) {
                criteria.add(durationCriteria[0])
            } else {
                criteria.add(Criteria().andOperator(*durationCriteria.toTypedArray()))
            }
        }

        val finalCriteria = if (criteria.isNotEmpty()) Criteria().andOperator(*criteria.toTypedArray()) else Criteria()

        val matchStage = Aggregation.match(finalCriteria)
        // Стадия lookup для объединения с коллекцией профилей
        val lookupStage = Aggregation.lookup("profiles", "authorId", "userId", "authorProfile")
        val addFieldsStage = Aggregation.addFields()
            .addField("authorIdObj")
            .withValue(ConvertOperators.valueOf("authorId").convertToObjectId())
            .build()
        val lookupUsersStage = Aggregation.lookup("users", "authorIdObj", "_id", "authorUser")

        // Стадия project: исключаем lessons и выбираем нужные поля, а также извлекаем имя автора из объединённого массива
        val projectStage = Aggregation.project(
            "title",
            "slug",
            "description",
            "imagePreview",
            "videoPreview",
            "authorId",
            "rating",
            "price",
            "discount",
            "isFree",
            "difficulty",
            "category",
            "videoLength",
            "createdAt"
        )
            .and(ArrayOperators.ArrayElemAt.arrayOf("\$authorProfile.firstName").elementAt(0)).`as`("authorFirstName")
            .and(ArrayOperators.ArrayElemAt.arrayOf("\$authorProfile.lastName").elementAt(0)).`as`("authorLastName")
            .and(ArrayOperators.ArrayElemAt.arrayOf("\$authorUser.username").elementAt(0)).`as`("authorUsername")
            .and(ToString.toString("_id")).`as`("id")

        val aggregationOperations = mutableListOf<AggregationOperation>()

        aggregationOperations.add(matchStage)
        aggregationOperations.add(lookupStage)
        aggregationOperations.add(addFieldsStage)
        aggregationOperations.add(lookupUsersStage)
        aggregationOperations.add(projectStage)

        when (sortBy?.lowercase()) {
            "price_asc" -> aggregationOperations.add(Aggregation.sort(Sort.Direction.ASC, "isFree", "price"))
            "price_desc" -> aggregationOperations.add(Aggregation.sort(Sort.Direction.DESC, "price"))
            "popular" -> aggregationOperations.add(Aggregation.sort(Sort.Direction.DESC, "rating"))
            "date_asc" -> aggregationOperations.add(Aggregation.sort(Sort.Direction.ASC, "createdAt"))
            "date_desc" -> aggregationOperations.add(Aggregation.sort(Sort.Direction.DESC, "createdAt"))
            "title_asc" -> aggregationOperations.add(Aggregation.sort(Sort.Direction.ASC, "title"))
            "title_desc" -> aggregationOperations.add(Aggregation.sort(Sort.Direction.DESC, "title"))
            else -> aggregationOperations.add(Aggregation.sort(Sort.Direction.DESC, "createdAt"))
        }

        val pageNumberForSkip = if (pageable.pageNumber > 0) pageable.pageNumber else 0
        aggregationOperations.add(Aggregation.skip((pageNumberForSkip * pageable.pageSize).toLong()))
        aggregationOperations.add(Aggregation.limit(pageable.pageSize.toLong()))

        val aggregation = Aggregation.newAggregation(aggregationOperations)

        // Выполняем агрегацию
        val aggregationResult = mongoTemplate.aggregate(aggregation, "courses", Document::class.java)
        val coursesDTO: List<CourseDTO> = aggregationResult.mappedResults.map { doc ->
            val authorFirstName = doc.getString("authorFirstName") ?: ""
            val authorLastName = doc.getString("authorLastName") ?: ""
            val authorName =
                if (authorFirstName.isBlank() && authorLastName.isBlank()) "Неизвестный автор" else "$authorFirstName $authorLastName".trim()
            val authorUsername = doc.getString("authorUsername") ?: "Неизвестный пользователь"

            CourseDTO(
                id = doc.get("_id").toString(),
                slug = doc.getString("slug"),
                title = doc.getString("title") ?: "",
                description = doc.getString("description") ?: "",
                imagePreview = doc.getString("imagePreview"),
                videoPreview = doc.getString("videoPreview"),
                authorId = doc.getString("authorId") ?: "",
                rating = doc.get("rating", Number::class.java)?.toDouble() ?: 0.0,
                price = doc.get("price", Number::class.java)?.toDouble() ?: 0.0,
                discount = doc.get("discount", Number::class.java)?.toDouble() ?: 0.0,
                isFree = doc.getBoolean("isFree") ?: false,
                difficulty = doc.getString("difficulty")?.let { CourseDifficultyLevels.valueOf(it) }
                    ?: CourseDifficultyLevels.BEGINNER,
                authorName = authorName,
                authorUsername = authorUsername,
                category = doc.getString("category") ?: "Без категории",
                videoLength = doc.get("videoLength", Number::class.java)?.toDouble() ?: 0.0
            )
        }

        val countAggregation = Aggregation.newAggregation(
            matchStage,
            Aggregation.count().`as`("total")
        )
        val countResult = mongoTemplate.aggregate(countAggregation, "courses", Document::class.java)
        val totalElements = if (countResult.mappedResults.isNotEmpty()) {
            countResult.mappedResults[0].getInteger("total", 0).toLong()
        } else {
            0L
        }

        val totalPages = if (pageable.pageSize > 0) {
            (totalElements / pageable.pageSize).toInt() + if (totalElements % pageable.pageSize > 0) 1 else 0
        } else {
            0
        }

        val isLastPage = if (pageable.pageSize > 0) {
            pageNumberForSkip >= totalPages - 1
        } else {
            true
        }

        return PagedResponseDTO(
            content = coursesDTO,
            pageNumber = pageable.pageNumber,
            pageSize = pageable.pageSize,
            totalElements = totalElements,
            totalPages = totalPages,
            isLast = isLastPage
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

    fun getAccessibleCourseForLearning(courseId: String): Course {
        val currentUserId = authorizationService.getCurrentUserEntity().id!!
        val course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
            ?: throw NotFoundException("Курс с ID $courseId не найден")

        val hasProgress = courseProgressRepository.existsByUserIdAndCourseId(currentUserId, courseId)

        if (!course.isFree && !hasProgress) {
            throw AccessDeniedException("У вас нет доступа к этому курсу (не куплен/не записан).")
        }

        return course
    }

    fun checkUserAccessToCourse(courseId: String, userId: String): Boolean {
        val course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
            ?: throw NotFoundException("Курс с ID $courseId не найден")

        return courseProgressRepository.existsByUserIdAndCourseId(userId, courseId)
    }

    @Transactional
    fun enrollFreeCourse(courseId: String): CourseProgress {
        val currentUserId = authorizationService.getCurrentUserEntity().id!!
        val course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
            ?: throw NotFoundException("Курс с ID $courseId не найден")

        if (!course.isFree) {
            throw IllegalArgumentException("Этот курс не является бесплатным и требует оплаты.")
        }

        val existingProgress = courseProgressRepository.findByUserIdAndCourseId(currentUserId, courseId)
        if (existingProgress != null) {
            return existingProgress
        }

        val newProgress = CourseProgress(userId = currentUserId, courseId = courseId, progress = 0.0)
        return courseProgressRepository.save(newProgress)
    }

    @CacheEvict(value = ["courses"], allEntries = true)
    fun updateCourseAdminOrMentor(
        courseId: String,
        request: AdminCreateUpdateCourseRequestDTO
    ): AdminCourseDetailDTO {
        val course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
            ?: throw NotFoundException("Course not found with id: $courseId")

        if (authorizationService.isCurrentUserMentor() && !authorizationService.isCurrentUserAdmin()) {
            if (request.authorId != course.authorId) {
                throw AccessDeniedException("Менторы не могут изменять автора курса.")
            }
        } else if (authorizationService.isCurrentUserAdmin() && request.authorId != course.authorId) {
            userRepository.findById(request.authorId)
                .orElseThrow {
                    ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Новый автор с ID ${request.authorId} не найден"
                    )
                }

            val oldAuthor = userRepository.findById(course.authorId).orElse(null)
            oldAuthor?.let {
                if (it.createdCourseIds.remove(courseId)) userRepository.save(it)
            }
            val newAuthor = userRepository.findById(request.authorId).get()
            if (!newAuthor.createdCourseIds.contains(courseId)) {
                newAuthor.createdCourseIds.add(courseId)
                userRepository.save(newAuthor)
            }
        }

        val isNowFree = request.isFree ?: course.isFree

        val newSlug = if (course.title != request.title) {
            SlugUtils.generateUniqueSlug(request.title) { newSlug ->
                newSlug != course.slug && courseRepository.existsBySlug(newSlug)
            }
        } else {
            course.slug
        }

        val oldImagePreview = course.imagePreview
        val oldVideoPreview = course.videoPreview
        val newImagePreview = request.imagePreview
        val newVideoPreview = request.videoPreview

        val updatedCourse = course.copy(
            title = request.title,
            slug = newSlug,
            description = request.description,
            isFree = isNowFree,
            price = if (isNowFree) 0.0 else request.price,
            discount = if (isNowFree) 0.0 else request.discount ?: course.discount,
            difficulty = request.difficulty,
            category = request.category,
            authorId = request.authorId,
            imagePreview = request.imagePreview ?: course.imagePreview,
            videoPreview = request.videoPreview ?: course.videoPreview,
            featuresBadge = request.featuresBadge ?: course.featuresBadge,
            featuresTitle = request.featuresTitle ?: course.featuresTitle,
            featuresSubtitle = request.featuresSubtitle ?: course.featuresSubtitle,
            featuresDescription = request.featuresDescription ?: course.featuresDescription,
            features = request.features ?: course.features,
            benefitTitle = request.benefitTitle ?: course.benefitTitle,
            benefitDescription = request.benefitDescription ?: course.benefitDescription,
            testimonial = request.testimonial ?: course.testimonial
        )

        val savedCourse = courseRepository.save(updatedCourse)

        if (oldImagePreview != null && oldImagePreview != newImagePreview) {
            cloudflareService.deleteFileFromR2Async(oldImagePreview)
        }
        if (oldVideoPreview != null && oldVideoPreview != newVideoPreview) {
            cloudflareService.deleteFileFromR2Async(oldVideoPreview)
        }

        if (newVideoPreview != oldVideoPreview) {
            mediaProcessingService.updateCourseVideoLengthAsync(savedCourse.id!!)
        }

        return getCourseDetailsAdmin(savedCourse.id!!)
    }

    @CacheEvict(value = ["courses"], key = "#courseId")
    fun deleteCourseAdminOrMentor(courseId: String) {
        val course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
            ?: throw NotFoundException("Курс $courseId не найден")

        course.imagePreview?.let { cloudflareService.deleteFileFromR2Async(it) }
        course.videoPreview?.let { cloudflareService.deleteFileFromR2Async(it) }
        course.lessons.forEach { lesson ->
            lesson.mainAttachment?.let { cloudflareService.deleteFileFromR2Async(it) }
            lesson.attachments?.forEach { attachment ->
                cloudflareService.deleteFileFromR2Async(attachment.url)
            }
        }

        val author = userRepository.findById(course.authorId).orElse(null)
        author?.let {
            it.createdCourseIds.remove(courseId)
            userRepository.save(it)
        }

        courseRepository.save(course.copy(deletedAt = Instant.now()))
    }

    @CacheEvict(value = ["courses"], key = "#courseId")
    fun addLessonAdminOrMentor(
        courseId: String,
        lessonDto: AdminCreateUpdateLessonRequestDTO
    ): AdminCourseDetailDTO {
        val course = courseRepository.findByIdAndDeletedAtIsNull(courseId) ?: throw NotFoundException(
            "Course not found with id: $courseId"
        )

        val lessonSlug = SlugUtils.generateUniqueSlug(lessonDto.title) { slug ->
            course.lessons.any { it.slug == slug }
        }

        val newLesson = Lesson(
            id = UUID.randomUUID().toString(),
            title = lessonDto.title,
            slug = lessonSlug,
            content = lessonDto.content,
            codeExamples = lessonDto.codeExamples ?: emptyList(),
            tasks = lessonDto.tasks?.map { taskDto ->
                Task(
                    id = taskDto.id ?: UUID.randomUUID().toString(),
                    description = taskDto.description,
                    solution = taskDto.solution,
                    taskType = taskDto.taskType,
                    options = taskDto.options?.toMutableList() ?: mutableListOf(),
                    language = taskDto.language,
                    boilerplateCode = taskDto.boilerplateCode,
                    testCases = taskDto.testCases?.map { tcDto ->
                        TestCase(
                            id = tcDto.id ?: UUID.randomUUID().toString(),
                            name = tcDto.name,
                            input = tcDto.input.toMutableList(),
                            expectedOutput = tcDto.expectedOutput.toMutableList(),
                            isHidden = tcDto.isHidden,
                            points = tcDto.points
                        )
                    }?.toMutableList() ?: mutableListOf(),
                    timeoutSeconds = taskDto.timeoutSeconds,
                    memoryLimitMb = taskDto.memoryLimitMb
                )
            }?.toMutableList() ?: mutableListOf(),
            attachments = lessonDto.attachments ?: emptyList(),
            mainAttachment = lessonDto.mainAttachment,
            videoLength = lessonDto.videoLength ?: 0.0
        )

        course.lessons.add(newLesson)
        val savedCourse = courseRepository.save(course)

        if (!newLesson.mainAttachment.isNullOrBlank()) {
            mediaProcessingService.updateLessonVideoLengthAsync(
                savedCourse.id!!,
                newLesson.id,
                newLesson.mainAttachment!!
            )
        }
        mediaProcessingService.updateCourseVideoLengthAsync(savedCourse.id!!)
        return getCourseDetailsAdmin(savedCourse.id)
    }

    @CacheEvict(value = ["courses"], key = "#courseId")
    fun updateLessonAdminOrMentor(
        courseId: String,
        lessonId: String,
        lessonDto: AdminCreateUpdateLessonRequestDTO
    ): AdminCourseDetailDTO {
        val course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
            ?: throw NotFoundException("Курс $courseId не найден")

        val lessonIndex = course.lessons.indexOfFirst { it.id == lessonId }
        if (lessonIndex == -1) throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Урок с ID $lessonId не найден в курсе"
        )

        val existingLesson = course.lessons[lessonIndex]

        val oldMainAttachment = existingLesson.mainAttachment
        val newMainAttachment = lessonDto.mainAttachment
        val oldAttachments = existingLesson.attachments.map { it.url }.toSet()
        val newAttachmentUrls = lessonDto.attachments?.map { it.url }?.toSet() ?: emptySet()

        val newLessonSlug =
            if (existingLesson.title != lessonDto.title) {
                SlugUtils.generateUniqueSlug(lessonDto.title) { newSlug -> newSlug != existingLesson.slug && course.lessons.any { it.slug == newSlug && it.id != lessonId } }
            } else existingLesson.slug

        val updatedLesson = existingLesson.copy(
            title = lessonDto.title,
            slug = newLessonSlug,
            content = lessonDto.content,
            codeExamples = lessonDto.codeExamples ?: existingLesson.codeExamples,
            tasks = lessonDto.tasks?.map { taskDto ->
                Task(
                    id = taskDto.id ?: UUID.randomUUID().toString(),
                    description = taskDto.description,
                    solution = taskDto.solution,
                    taskType = taskDto.taskType,
                    options = taskDto.options?.toMutableList() ?: mutableListOf(),
                    language = taskDto.language,
                    boilerplateCode = taskDto.boilerplateCode,
                    testCases = taskDto.testCases?.map { tcDto ->
                        TestCase(
                            id = tcDto.id ?: UUID.randomUUID().toString(),
                            name = tcDto.name,
                            input = tcDto.input.toMutableList(),
                            expectedOutput = tcDto.expectedOutput.toMutableList(),
                            isHidden = tcDto.isHidden,
                            points = tcDto.points
                        )
                    }?.toMutableList() ?: mutableListOf(),
                    timeoutSeconds = taskDto.timeoutSeconds,
                    memoryLimitMb = taskDto.memoryLimitMb
                )
            }?.toMutableList() ?: existingLesson.tasks,
            attachments = lessonDto.attachments ?: existingLesson.attachments,
            mainAttachment = lessonDto.mainAttachment,
            videoLength = when {
                lessonDto.videoLength != null -> lessonDto.videoLength
                lessonDto.mainAttachment != oldMainAttachment && !lessonDto.mainAttachment.isNullOrBlank() -> 0.0
                lessonDto.mainAttachment != oldMainAttachment && lessonDto.mainAttachment.isNullOrBlank() -> 0.0
                else -> existingLesson.videoLength
            }
        )

        course.lessons[lessonIndex] = updatedLesson
        val savedCourse = courseRepository.save(course)

        if (oldMainAttachment != null && oldMainAttachment != newMainAttachment) {
            cloudflareService.deleteFileFromR2Async(oldMainAttachment)
        }

        val attachmentsToDelete = oldAttachments - newAttachmentUrls
        attachmentsToDelete.forEach { cloudflareService.deleteFileFromR2Async(it) }

        if (newMainAttachment != oldMainAttachment && !newMainAttachment.isNullOrBlank()) {
            mediaProcessingService.updateLessonVideoLengthAsync(savedCourse.id!!, updatedLesson.id, newMainAttachment)
        }

        mediaProcessingService.updateCourseVideoLengthAsync(savedCourse.id!!)
        return getCourseDetailsAdmin(savedCourse.id!!)
    }

    @CacheEvict(value = ["courses"], key = "#courseId")
    fun deleteLessonAdminOrMentor(courseId: String, lessonId: String) {
        val course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
            ?: throw NotFoundException("Курс $courseId не найден")

        val lessonToRemove = course.lessons.find { it.id == lessonId }
        val removed = course.lessons.removeIf { it.id == lessonId }
        if (!removed) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Урок не найден")

        courseRepository.save(course)

        lessonToRemove?.mainAttachment?.let { cloudflareService.deleteFileFromR2Async(it) }
        lessonToRemove?.attachments?.forEach { cloudflareService.deleteFileFromR2Async(it.url) }

        mediaProcessingService.updateCourseVideoLengthAsync(course.id!!)
    }

    fun getStudentProgressForCourse(
        courseId: String,
        pageable: Pageable
    ): PagedResponseDTO<StudentProgressDTO> {
        val course = courseRepository.findById(courseId)
            .orElseThrow { NotFoundException("Курс с ID $courseId не найден") }

        val query = Query(Criteria.where("courseId").`is`(courseId)).with(pageable)

        val progressList = mongoTemplate.find(query, CourseProgress::class.java)

        val totalProgressRecords = mongoTemplate.count(
            Query(Criteria.where("courseId").`is`(courseId)),
            CourseProgress::class.java
        )

        val studentIds = progressList.map { it.userId }
        if (studentIds.isEmpty()) {
            return PagedResponseDTO(
                content = emptyList(),
                pageNumber = pageable.pageNumber,
                pageSize = pageable.pageSize,
                totalElements = totalProgressRecords,
                totalPages = if (pageable.pageSize > 0) (totalProgressRecords / pageable.pageSize + if (totalProgressRecords % pageable.pageSize == 0L) 0 else 1).toInt() else 0,
                isLast = pageable.pageNumber >= (if (pageable.pageSize > 0) (totalProgressRecords / pageable.pageSize + if (totalProgressRecords % pageable.pageSize == 0L) 0 else 1).toInt() - 1 else 0) // Рассчитываем isLast
            )
        }

        val students = userRepository.findAllById(studentIds).associateBy { it.id!! }
        val totalLessonsInCourse = course.lessons.size

        val studentProgressDTOs = progressList.mapNotNull { progress ->
            val student = students[progress.userId] ?: return@mapNotNull null
            StudentProgressDTO(
                userId = student.id!!,
                username = student.username,
                email = student.email,
                progressPercent = progress.progress,
                completedLessonsCount = progress.completedLessons.size,
                totalLessonsCount = totalLessonsInCourse,
                lastAccessedLessonAt = progress.lastUpdated
            )
        }

        val totalPages = if (pageable.pageSize > 0) {
            (totalProgressRecords / pageable.pageSize + if (totalProgressRecords % pageable.pageSize == 0L) 0 else 1).toInt()
        } else {
            0
        }
        val isLast = pageable.pageNumber >= totalPages - 1

        return PagedResponseDTO(
            content = studentProgressDTOs,
            pageNumber = pageable.pageNumber,
            pageSize = pageable.pageSize,
            totalElements = totalProgressRecords,
            totalPages = totalPages,
            isLast = isLast
        )
    }
}