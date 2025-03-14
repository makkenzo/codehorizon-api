package com.makkenzo.codehorizon.services

import io.minio.MinioClient
import io.minio.PutObjectArgs
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.*


@Service
class CloudflareService(private val minioClient: MinioClient) {
    fun uploadFileToR2(file: MultipartFile, folder: String): String {
        val fileName = "${UUID.randomUUID()}_${file.originalFilename}"
        val filePath = "$folder/$fileName"
        val contentType = file.contentType ?: "application/octet-stream"

        val bucketName = "codehorizon-media"

        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(bucketName)
                .`object`(filePath)
                .stream(file.inputStream, file.size, -1)
                .contentType(file.contentType)
                .build()
        )

        return "https://codehorizon-bucket.makkenzo.com/$filePath"
    }

    fun validateFile(file: MultipartFile) {
        val allowedTypes = listOf("image/png", "image/jpeg", "image/webp", "video/mp4")
        val maxSze = 1024 * 1024 * 1024 // 1024MB

        if (file.isEmpty) throw IllegalArgumentException("File is empty")
        if (!allowedTypes.contains(file.contentType)) throw IllegalArgumentException("Invalid file type")
        if (file.size > maxSze) throw IllegalArgumentException("File size is too large")
    }
}