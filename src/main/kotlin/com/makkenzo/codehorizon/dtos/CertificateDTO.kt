package com.makkenzo.codehorizon.dtos

data class CertificateDTO(
    val id: String,
    val uniqueCertificateId: String,
    val courseTitle: String,
    val completionDate: String,
    val instructorName: String? = null,
    val category: String? = null
)