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
import org.bson.Document
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.aggregation.ArrayOperators
import org.springframework.data.mongodb.core.aggregation.ConvertOperators
import org.springframework.data.mongodb.core.aggregation.ConvertOperators.ToString
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service

@Service
class CourseService(
    private val courseRepository: CourseRepository,
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val mongoTemplate: MongoTemplate
) {
    @CacheEvict(value = ["courses"], allEntries = true)
    fun createCourse(
        title: String,
        description: String,
        price: Double,
        authorId: String,
        category: String,
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
            difficulty = difficultyLevel,
            category = category,
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

    @Cacheable("courses")
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

        val durationCriteria = mutableListOf<Criteria>()
        minDuration?.let { durationCriteria.add(Criteria.where("videoLength").gte(it)) }
        maxDuration?.let { durationCriteria.add(Criteria.where("videoLength").lte(it)) }
        if (durationCriteria.isNotEmpty()) {
            criteria.add(Criteria().andOperator(*durationCriteria.toTypedArray()))
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


        // Определяем сортировку
        val sort = when (sortBy) {
            "price_asc" -> Sort.by("price").ascending()
            "price_desc" -> Sort.by("price").descending()
            "popular" -> Sort.by("rating").descending()
            else -> Sort.unsorted()
        }

        // Создаем список операций агрегации
        val aggregationOperations = mutableListOf<AggregationOperation>()

        aggregationOperations.add(matchStage)
        aggregationOperations.add(lookupStage)
        aggregationOperations.add(addFieldsStage)
        aggregationOperations.add(lookupUsersStage)
        aggregationOperations.add(projectStage)
        // Добавляем стадию сортировки только если она не пустая
        if (sort.isSorted) {
            aggregationOperations.add(Aggregation.sort(sort))
        }
        // Пагинация: пропустить и ограничить
        val pageNumber = if (pageable.pageNumber > 0) pageable.pageNumber - 1 else 0
        aggregationOperations.add(Aggregation.skip((pageNumber * pageable.pageSize).toLong()))
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

        // Отдельная агрегация для подсчета общего количества документов
        val countAggregation = Aggregation.newAggregation(
            matchStage,
            Aggregation.count().`as`("total")
        )
        val countResult = mongoTemplate.aggregate(countAggregation, "courses", Document::class.java)
        val totalElements = if (countResult.mappedResults.isNotEmpty()) {
            countResult.mappedResults[0].getInteger("total").toLong()
        } else {
            0L
        }
        val totalPages =
            (totalElements / pageable.pageSize).toInt() + if (totalElements % pageable.pageSize > 0) 1 else 0

        return PagedResponseDTO(
            content = coursesDTO,
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