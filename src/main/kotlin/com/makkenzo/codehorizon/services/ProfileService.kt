package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.UpdateProfileDTO
import com.makkenzo.codehorizon.models.Profile
import com.makkenzo.codehorizon.repositories.ProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.awt.Color
import java.awt.image.BufferedImage
import java.net.URI
import javax.imageio.ImageIO

@Service
class ProfileService(
    private val profileRepository: ProfileRepository,
    private val cloudflareService: CloudflareService
) {
    private val logger = LoggerFactory.getLogger(ProfileService::class.java)

    fun createProfile(profile: Profile): Profile {
        if (profileRepository.findByUserId(profile.userId) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Профиль для данного пользователя уже существует")
        }
        return profileRepository.save(profile)
    }

    @Cacheable(value = ["profiles"], key = "#userId")
    fun getProfileByUserId(userId: String): Profile =
        profileRepository.findByUserId(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Профиль не найден")

    @Transactional
    @CacheEvict(value = ["profiles"], key = "#userId")
    fun updateProfile(userId: String, updatedProfileDTO: UpdateProfileDTO): Profile {
        val existingProfile = profileRepository.findByUserId(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Профиль не найден")

        val oldAvatarUrl = existingProfile.avatarUrl
        val oldSignatureUrl = existingProfile.signatureUrl
        val newAvatarUrl = updatedProfileDTO.avatarUrl
        val newSignatureUrl = updatedProfileDTO.signatureUrl

        val avatarChanged =
            updatedProfileDTO.avatarUrl != null && updatedProfileDTO.avatarUrl != existingProfile.avatarUrl
        val currentAvatarUrl = updatedProfileDTO.avatarUrl ?: existingProfile.avatarUrl

        val profileToUpdate = existingProfile.copy(
            avatarUrl = newAvatarUrl,
            bio = updatedProfileDTO.bio ?: existingProfile.bio,
            firstName = updatedProfileDTO.firstName ?: existingProfile.firstName,
            lastName = updatedProfileDTO.lastName ?: existingProfile.lastName,
            location = updatedProfileDTO.location ?: existingProfile.location,
            website = updatedProfileDTO.website ?: existingProfile.website,
            signatureUrl = newSignatureUrl
        )

        val savedProfile = profileRepository.save(profileToUpdate)

        if (oldAvatarUrl != null && oldAvatarUrl != newAvatarUrl) {
            cloudflareService.deleteFileFromR2Async(oldAvatarUrl)
        }
        if (oldSignatureUrl != null && oldSignatureUrl != newSignatureUrl) {
            if (oldSignatureUrl.startsWith(cloudflareService.r2PublicBaseUrl)) {
                cloudflareService.deleteFileFromR2Async(oldSignatureUrl)
            }
        }
        if (oldAvatarUrl != newAvatarUrl && !newAvatarUrl.isNullOrBlank()) {
            updateAvatarColorAsync(savedProfile.id!!, newAvatarUrl)
        }

        return savedProfile
    }

    @Async
    @Transactional
    fun updateAvatarColorAsync(profileId: String, avatarUrl: String) {
        try {
            logger.info("Начинаем расчет доминантного цвета для профиля {} и URL {}", profileId, avatarUrl)
            val dominantColor = getDominantColor(avatarUrl)

            val profile = profileRepository.findById(profileId).orElse(null)
            if (profile == null) {
                logger.warn("Асинхронное обновление цвета: Профиль {} не найден.", profileId)
                return
            }

            if (profile.avatarUrl == avatarUrl && profile.avatarColor != dominantColor) {
                val updatedProfile = profile.copy(avatarColor = dominantColor)
                profileRepository.save(updatedProfile)
                logger.info("Асинхронно обновлен цвет аватара для профиля {}: {}", profileId, dominantColor)
            } else if (profile.avatarUrl != avatarUrl) {
                logger.info(
                    "URL аватара для профиля {} изменился ({}), расчет цвета ({}) отменен.",
                    profileId,
                    profile.avatarUrl,
                    dominantColor
                )
            }
        } catch (e: Exception) {
            logger.error("Ошибка при асинхронном расчете/обновлении цвета для профиля {}: {}", profileId, e.message, e)
        }
    }

    fun updateAllProfilesWithDominantColor() {
        val profiles = profileRepository.findAll()

        profiles.forEach { profile ->
            if (!profile.avatarUrl.isNullOrBlank()) {
                val dominantColor = getDominantColor(profile.avatarUrl)
                val updatedProfile = profile.copy(avatarColor = dominantColor)
                profileRepository.save(updatedProfile)
            }
        }
    }

    fun getDominantColor(imageUrl: String): String {
        try {
            val image: BufferedImage = ImageIO.read(URI(imageUrl).toURL())
            val colorMap = mutableMapOf<Color, Int>()

            for (x in 0 until image.width step 10) {
                for (y in 0 until image.height step 10) {
                    val pixel = Color(image.getRGB(x, y))
                    // Игнорируем слишком светлые пиксели (почти белые)
                    if (pixel.red > 220 && pixel.green > 220 && pixel.blue > 220) continue
                    colorMap[pixel] = colorMap.getOrDefault(pixel, 0) + 1
                }
            }

            var dominantColor = colorMap.maxByOrNull { it.value }?.key ?: Color(128, 128, 128)

            // Если цвет всё равно слишком светлый, затемняем его
            if (dominantColor.red > 200 && dominantColor.green > 200 && dominantColor.blue > 200) {
                dominantColor = Color(
                    (dominantColor.red * 0.7).toInt(),
                    (dominantColor.green * 0.7).toInt(),
                    (dominantColor.blue * 0.7).toInt()
                )
            }

            return "#%02x%02x%02x".format(dominantColor.red, dominantColor.green, dominantColor.blue)
        } catch (e: Exception) {
            logger.error("Не удалось получить/обработать изображение по URL {}: {}", imageUrl, e.message)
            return "#808080"
        }
    }

    fun deleteProfile(id: String) {
        if (!profileRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Профиль не найден")
        }
        profileRepository.deleteById(id)
    }
}
