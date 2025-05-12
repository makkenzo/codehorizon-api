package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.services.AuthorizationService
import com.makkenzo.codehorizon.services.CloudflareService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/s3")
@Tag(name = "S3")
class S3Controller(
    private val cloudflareService: CloudflareService,
    private val authorizationService: AuthorizationService
) {
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Загрузка файла", security = [SecurityRequirement(name = "bearerAuth")])
    fun uploadToS3(
        @Parameter(
            description = "Директория хранения",
            schema = Schema(type = "string", format = "string")
        )
        @RequestParam("directory") directory: String,
        @Parameter(
            description = "Файл",
            schema = Schema(type = "string", format = "binary")
        )
        @RequestPart("file") file: MultipartFile
    ): ResponseEntity<Any> {
        val requiredPermission: String = when (directory) {
            "avatars" -> "file:upload:avatar"
            "course-previews" -> "file:upload:course_preview"
            "lesson-main-attachments", "lesson-attachments" -> "file:upload:lesson_attachment"
            "signatures" -> "file:upload:signature"
            else -> {
                "file:upload:other"
            }
        }

        if (!authorizationService.hasAuthority(requiredPermission)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "У вас нет прав для загрузки файла в директорию '$directory'"))
        }

        if (requiredPermission == "file:upload:other" && directory != "some_allowed_other_directory") {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to "Загрузка в указанную директорию не разрешена."))
        }

        return try {
            cloudflareService.validateFile(file)
            val fileUrl = cloudflareService.uploadFileToR2(file, directory)
            ResponseEntity.ok(mapOf("url" to fileUrl))
        } catch (e: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to e.message))
        } catch (e: IllegalArgumentException) {
            println(e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        }
    }
}