package com.makkenzo.codehorizon.models

data class Review(
    var id: String? = null,
    var text: String? = null,
    var rating: Int? = null,
    var authorId: String? = null,
    var courseId: String? = null,
)