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
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.aggregation.ArrayOperators
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
    fun createCourse(
        title: String,
        description: String,
        price: Double,
        authorId: String,
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
            difficulty = difficultyLevel
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

    fun getCourses(
        title: String?,
        description: String?,
        minRating: Double?,
        maxDuration: Double?,
        category: String?,
        difficulty: CourseDifficultyLevels?,
        sortBy: String?,
        pageable: Pageable
    ): PagedResponseDTO<CourseDTO> {
        // Собираем критерии фильтрации
        val criteria = Criteria()
        title?.let { criteria.and("title").regex(".*$it.*", "i") }
        description?.let { criteria.and("description").regex(".*$it.*", "i") }
        minRating?.let { criteria.and("rating").gte(it) }
        maxDuration?.let { criteria.and("duration").lte(it) }
        category?.let { criteria.and("category").`is`(it) }
        difficulty?.let { criteria.and("difficulty").`is`(it) }

        // Стадия match с фильтрами
        val matchStage = Aggregation.match(criteria)
        // Стадия lookup для объединения с коллекцией профилей
        val lookupStage = Aggregation.lookup("profiles", "authorId", "userId", "authorProfile")
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
            "difficulty"
        )
            .and(ArrayOperators.ArrayElemAt.arrayOf("\$authorProfile.firstName").elementAt(0)).`as`("authorFirstName")
            .and(ArrayOperators.ArrayElemAt.arrayOf("\$authorProfile.lastName").elementAt(0)).`as`("authorLastName")
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
        aggregationOperations.add(projectStage)
        // Добавляем стадию сортировки только если она не пустая
        if (sort.isSorted) {
            aggregationOperations.add(Aggregation.sort(sort))
        }
        // Пагинация: пропустить и ограничить
        aggregationOperations.add(Aggregation.skip((pageable.pageNumber * pageable.pageSize).toLong()))
        aggregationOperations.add(Aggregation.limit(pageable.pageSize.toLong()))

        val aggregation = Aggregation.newAggregation(aggregationOperations)

        // Выполняем агрегацию
        val aggregationResult = mongoTemplate.aggregate(aggregation, "courses", Document::class.java)
        val coursesDTO: List<CourseDTO> = aggregationResult.mappedResults.map { doc ->
            val authorFirstName = doc.getString("authorFirstName") ?: ""
            val authorLastName = doc.getString("authorLastName") ?: ""
            val authorName =
                if (authorFirstName.isBlank() && authorLastName.isBlank()) "Неизвестный автор" else "$authorFirstName $authorLastName".trim()

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
                authorName = authorName
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