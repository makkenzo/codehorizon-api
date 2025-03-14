package com.makkenzo.codehorizon.dtos

import java.io.Serializable

data class PagedResponseDTO<T>(
    val content: List<T>,
    val pageNumber: Int,
    val pageSize: Int,
    val totalElements: Long,
    val totalPages: Int,
    val isLast: Boolean
) : Serializable
