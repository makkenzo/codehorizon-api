package com.makkenzo.codehorizon.dtos

data class PagedResponseDTO<T>(
    val content: List<T>,
    val pageNumber: Int,
    val pageSize: Int,
    val totalElements: Long,
    val totalPages: Int,
    val isLast: Boolean
)
