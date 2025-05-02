package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "reviews")
@CompoundIndex(name = "course_author_idx", def = "{'courseId': 1, 'authorId': 1}", unique = true)
data class Review(
    @Id val id: String? = null,
    val courseId: String,
    val authorId: String,
    var rating: Int,
    var text: String? = null,
    @Indexed
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now()
)