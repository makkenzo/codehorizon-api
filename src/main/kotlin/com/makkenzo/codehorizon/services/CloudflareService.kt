package com.makkenzo.codehorizon.services

import io.minio.MinioClient
import io.minio.PutObjectArgs
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.*


@Service
class CloudflareService(private val minioClient: MinioClient) {
    private val logger = LoggerFactory.getLogger(CloudflareService::class.java)
    private val tika = Tika()
    private val allowedMimeTypes = setOf(
        "image/png",
        "image/jpeg",
        "image/webp",
        "video/mp4"
    )
    private val maxFileSize = 1024 * 1024 * 1024 // 1024MB

    fun uploadFileToR2(file: MultipartFile, folder: String): String {
        val originalFilename = file.originalFilename ?: "unknown_file"
        val sanitizedFilenameBase = originalFilename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileExtension = originalFilename.substringAfterLast('.', "")
        val finalSanitizedFilename = if (fileExtension.isNotEmpty()) {
            "${sanitizedFilenameBase.substringBeforeLast('.')}.$fileExtension"
        } else {
            sanitizedFilenameBase
        }

        val uniqueFileName = "${UUID.randomUUID()}_$finalSanitizedFilename"
        val filePath = "$folder/$uniqueFileName"
        val bucketName = "codehorizon-media"

        validateFile(file)

        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(bucketName)
                .`object`(filePath)
                .stream(file.inputStream, file.size, -1)
                .contentType(file.contentType)
                .build()
        )

        try {
            file.inputStream.use { inputStream ->
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(bucketName)
                        .`object`(filePath)
                        .stream(inputStream, file.size, -1)
                        .contentType(detectContentType(file) ?: file.contentType ?: "application/octet-stream")
                        .build()
                )
            }
            logger.info("Файл успешно загружен в R2: {}", filePath)
            return "https://codehorizon-bucket.makkenzo.com/$filePath"
        } catch (e: Exception) {
            logger.error("Ошибка загрузки файла в R2 (path: {}): {}", filePath, e.message, e)
            throw RuntimeException("Не удалось загрузить файл: ${e.message}", e)
        }
    }

    private fun detectContentType(file: MultipartFile): String? {
        return try {
            file.inputStream.use { tika.detect(it) }
        } catch (e: Exception) {
            logger.warn("Не удалось определить тип файла '${file.originalFilename}' с помощью Tika: ${e.message}. Будет использован Content-Type от клиента.")
            null
        }
    }

    fun validateFile(file: MultipartFile) {
        if (file.isEmpty) {
            throw IllegalArgumentException("Файл не может быть пустым.")
        }
        if (file.size > maxFileSize) {
            throw IllegalArgumentException("Размер файла (${file.size / 1024 / 1024}MB) превышает допустимый лимит (${maxFileSize / 1024 / 1024}MB).")
        }

        val detectedContentType = detectContentType(file)
        val clientContentType = file.contentType?.lowercase()

        logger.info(
            "Валидация файла '{}': Client-Type='{}', Detected-Type='{}', Allowed={}",
            file.originalFilename, clientContentType, detectedContentType, allowedMimeTypes
        )

        val effectiveContentType = detectedContentType ?: clientContentType
        val isAllowed = effectiveContentType != null && allowedMimeTypes.contains(effectiveContentType)

        if (!isAllowed) {
            throw IllegalArgumentException(
                "Недопустимый тип файла. Определен как: '${effectiveContentType ?: "unknown"}'. Разрешены: ${allowedMimeTypes.joinToString()}"
            )
        }

        val originalFilename = file.originalFilename ?: ""
        if (!originalFilename.contains('.') || originalFilename.count { it == '.' } > 1) {
            logger.warn("Имя файла '{}' может быть подозрительным.", originalFilename)
        }
    }
}