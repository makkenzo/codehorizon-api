package com.makkenzo.codehorizon.dtos

data class CourseSearchResultDTO(
    val id: String,
    val title: String,
    val slug: String,
    val imagePreview: String?,
    val authorUsername: String
)

data class AuthorSearchResultDTO(
    val userId: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val avatarColor: String?,
    val bio: String?
)

data class SearchResultItemDTO(
    val type: String,
    val data: Any
)

data class GlobalSearchResponseDTO(
    val results: List<SearchResultItemDTO>
)