package com.makkenzo.codehorizon.models

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "certificates")
data class Certificate(
    @Id val id: String? = null,
    @Indexed
    val userId: String,
    @Indexed
    val courseId: String,
    val courseTitle: String,
    val userName: String,
    val uniqueCertificateId: String,
    val completionDate: Instant = Instant.now(),
    var instructorName: String? = null,
    var instructorSignatureUrl: String? = null,
    val category: String? = null
)