package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.*
import com.makkenzo.codehorizon.exceptions.NotFoundException
import com.makkenzo.codehorizon.models.Course
import com.makkenzo.codehorizon.models.CourseDifficultyLevels
import com.makkenzo.codehorizon.models.Lesson
import com.makkenzo.codehorizon.repositories.CourseProgressRepository
import com.makkenzo.codehorizon.repositories.CourseRepository
import com.makkenzo.codehorizon.repositories.UserRepository
import com.makkenzo.codehorizon.utils.SlugUtils
import com.mongodb.client.model.Filters
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
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
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class CourseService(
    private val courseRepository: CourseRepository,
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val mongoTemplate: MongoTemplate,
    private val courseProgressRepository: CourseProgressRepository,
    private val mediaProcessingService: MediaProcessingService
) {


    fun findAllCoursesAdmin(
        pageable: Pageable,
        titleSearch: String?,
        authorIdFilter: String?
    ): PagedResponseDTO<AdminCourseListItemDTO> {
        val criteria = Criteria()
        titleSearch?.let {
            criteria.and("title").regex(it, "i")
        }
        authorIdFilter?.let { criteria.and("authorId").`is`(it) }

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
                lessonCount = course.lessons.size
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
    fun createCourseAdmin(request: AdminCreateUpdateCourseRequestDTO): AdminCourseDetailDTO {
        val author = userRepository.findById(request.authorId)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "Автор с ID ${request.authorId} не найден") }

        val slug = SlugUtils.generateUniqueSlug(request.title) { courseRepository.existsBySlug(it) }

        val newCourse = Course(
            title = request.title,
            slug = slug,
            description = request.description,
            price = request.price,
            discount = request.discount ?: 0.0,
            authorId = request.authorId,
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

        author.createdCourseIds.add(savedCourse.id!!)
        userRepository.save(author)

        return getCourseDetailsAdmin(savedCourse.id)
    }

    fun findByIds(courseIds: List<String>): List<CourseDTO> {
        if (courseIds.isEmpty()) return emptyList()

        val matchStage = Aggregation.match(Criteria.where("_id").`in`(courseIds))

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
        return courseRepository.findByAuthorId(authorId)
    }

    @Cacheable(value = ["courses"], key = "#courseId")
    fun getCourseById(courseId: String): Course {
        return courseRepository.findById(courseId).orElseThrow { NoSuchElementException("Course not found") }
    }

    @Cacheable(value = ["courses"], key = "#slug")
    fun getCourseBySlug(slug: String): CourseWithoutContentDTO {
        val course = courseRepository.findBySlug(slug) ?: throw NotFoundException("Course not found with slug: $slug")

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
            authorName = authorName,
            authorUsername = authorUsername,
            discount = course.discount,
            lessons = course.lessons.map {
                LessonWithoutContent(
                    slug = it.slug ?: "",
                    title = it.title
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
        sortBy: String?,
        pageable: Pageable
    ): PagedResponseDTO<CourseDTO> {
        val criteria = mutableListOf<Criteria>()

        title?.let { criteria.add(Criteria.where("title").regex(".*$it.*", "i")) }
        description?.let { criteria.add(Criteria.where("description").regex(".*$it.*", "i")) }
        minRating?.let { criteria.add(Criteria.where("rating").gte(it)) }
        category?.let { criteria.add(Criteria.where("category").`in`(it)) }
        difficulty?.let { criteria.add(Criteria.where("difficulty").`in`(it)) }

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
            "price_asc" -> aggregationOperations.add(Aggregation.sort(Sort.Direction.ASC, "price"))
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

    fun getFullCourseForLearning(courseId: String, userId: String): Course {
        val progressExists = courseProgressRepository.findByUserIdAndCourseId(userId, courseId) != null

        if (!progressExists) {
            throw AccessDeniedException("У вас нет доступа к этому курсу.")
        }

        return courseRepository.findById(courseId)
            .orElseThrow { NotFoundException("Курс с ID $courseId не найден") }
    }

    @CacheEvict(value = ["courses"], allEntries = true)
    fun updateCourseAdminOrMentor(
        courseId: String,
        request: AdminCreateUpdateCourseRequestDTO,
        currentUserId: String
    ): AdminCourseDetailDTO {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Курс не найден") }

        checkPermission(course.authorId, currentUserId, "обновить этот курс")

        if (course.authorId != request.authorId && !isAdmin(currentUserId)) {
            throw AccessDeniedException("Вы не можете изменить автора курса.")
        }
        if (course.authorId != request.authorId && isAdmin(currentUserId)) {
            if (!userRepository.existsById(request.authorId)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Новый автор с ID ${request.authorId} не найден")
            }
            // TODO: Логика переноса курса от старого автора к новому, если нужно
        }


        val newSlug = if (course.title != request.title) {
            SlugUtils.generateUniqueSlug(request.title) { newSlug ->
                newSlug != course.slug && courseRepository.existsBySlug(newSlug)
            }
        } else {
            course.slug
        }

        val updatedCourse = course.copy(
            title = request.title,
            slug = newSlug,
            description = request.description,
            price = request.price,
            discount = request.discount ?: course.discount,
            difficulty = request.difficulty,
            category = request.category,
            authorId = request.authorId, // Разрешаем менять админу
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
        mediaProcessingService.updateCourseVideoLengthAsync(savedCourse.id!!)
        return getCourseDetailsAdmin(savedCourse.id!!)
    }

    @CacheEvict(value = ["courses"], key = "#courseId")
    fun deleteCourseAdminOrMentor(courseId: String, currentUserId: String) {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Курс не найден") }

        checkPermission(course.authorId, currentUserId, "удалить этот курс")

        val author = userRepository.findById(course.authorId).orElse(null)
        author?.let {
            it.createdCourseIds.remove(courseId)
            userRepository.save(it)
        }
        courseRepository.deleteById(courseId)
        // TODO: Удалить прогресс студентов, отзывы и т.д., связанные с курсом?
    }

    @CacheEvict(value = ["courses"], key = "#courseId")
    fun addLessonAdminOrMentor(
        courseId: String,
        lessonDto: AdminCreateUpdateLessonRequestDTO,
        currentUserId: String
    ): AdminCourseDetailDTO {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Курс не найден") }
        checkPermission(course.authorId, currentUserId, "добавить урок в этот курс")

        val lessonSlug = SlugUtils.generateUniqueSlug(lessonDto.title) { slug ->
            course.lessons.any { it.slug == slug }
        }

        val newLesson = Lesson(
            id = UUID.randomUUID().toString(),
            title = lessonDto.title,
            slug = lessonSlug,
            content = lessonDto.content,
            codeExamples = lessonDto.codeExamples ?: emptyList(),
            tasks = lessonDto.tasks ?: emptyList(),
            attachments = lessonDto.attachments ?: emptyList(),
            mainAttachment = lessonDto.mainAttachment
        )

        course.lessons.add(newLesson)
        val savedCourse = courseRepository.save(course)
        return getCourseDetailsAdmin(savedCourse.id!!)
    }

    @CacheEvict(value = ["courses"], key = "#courseId")
    fun updateLessonAdminOrMentor(
        courseId: String,
        lessonId: String,
        lessonDto: AdminCreateUpdateLessonRequestDTO,
        currentUserId: String
    ): AdminCourseDetailDTO {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Курс не найден") }
        checkPermission(course.authorId, currentUserId, "обновить урок в этом курсе")

        val lessonIndex = course.lessons.indexOfFirst { it.id == lessonId }
        if (lessonIndex == -1) throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Урок с ID $lessonId не найден в курсе"
        )
        val existingLesson = course.lessons[lessonIndex]
        val newLessonSlug =
            if (existingLesson.title != lessonDto.title) {
                SlugUtils.generateUniqueSlug(lessonDto.title) { newSlug -> newSlug != existingLesson.slug && course.lessons.any { it.slug == newSlug && it.id != lessonId } }
            } else existingLesson.slug
        val updatedLesson = existingLesson.copy(
            title = lessonDto.title,
            slug = newLessonSlug,
            content = lessonDto.content,
            codeExamples = lessonDto.codeExamples ?: existingLesson.codeExamples,
            tasks = lessonDto.tasks ?: existingLesson.tasks,
            attachments = lessonDto.attachments ?: existingLesson.attachments,
            mainAttachment = lessonDto.mainAttachment
        )
        course.lessons[lessonIndex] = updatedLesson
        val savedCourse = courseRepository.save(course)
        return getCourseDetailsAdmin(savedCourse.id!!)
    }

    @CacheEvict(value = ["courses"], key = "#courseId")
    fun deleteLessonAdminOrMentor(courseId: String, lessonId: String, currentUserId: String) {
        val course = courseRepository.findById(courseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Курс не найден") }
        checkPermission(course.authorId, currentUserId, "удалить урок из этого курса")

        val removed = course.lessons.removeIf { it.id == lessonId }
        if (!removed) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Урок с ID $lessonId не найден в курсе")
        courseRepository.save(course)
    }

    private fun checkPermission(courseAuthorId: String, currentUserId: String, actionDescription: String) {
        val authentication = SecurityContextHolder.getContext().authentication
        val isAdmin = authentication?.authorities?.any { it.authority == "ROLE_ADMIN" } ?: false

        if (!isAdmin && courseAuthorId != currentUserId) {
            throw AccessDeniedException("У вас нет прав, чтобы $actionDescription.")
        }
    }

    private fun isAdmin(currentUserId: String): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication
        return authentication?.authorities?.any { it.authority == "ROLE_ADMIN" } ?: false
    }
}