package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.UpdateProfileDTO
import com.makkenzo.codehorizon.models.Profile
import com.makkenzo.codehorizon.repositories.ProfileRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.awt.Color
import java.awt.image.BufferedImage
import java.net.URI
import javax.imageio.ImageIO

@Service
class ProfileService(private val profileRepository: ProfileRepository) {

    fun createProfile(profile: Profile): Profile {
        if (profileRepository.findByUserId(profile.userId) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Профиль для данного пользователя уже существует")
        }
        return profileRepository.save(profile)
    }

    fun getProfileById(id: String): Profile =
        profileRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Профиль не найден") }

    fun getProfileByUserId(userId: String): Profile =
        profileRepository.findByUserId(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Профиль не найден")

    fun updateProfile(userId: String, updatedProfileDTO: UpdateProfileDTO): Profile {
        val existingProfile = profileRepository.findByUserId(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Профиль не найден")

        val dominantColor = updatedProfileDTO.avatarUrl?.let { getDominantColor(it) } ?: existingProfile.avatarColor

        val profileToUpdate = existingProfile.copy(
            avatarUrl = updatedProfileDTO.avatarUrl ?: existingProfile.avatarUrl,
            avatarColor = dominantColor,
            bio = updatedProfileDTO.bio ?: existingProfile.bio,
            firstName = updatedProfileDTO.firstName ?: existingProfile.firstName,
            lastName = updatedProfileDTO.lastName ?: existingProfile.lastName,
            location = updatedProfileDTO.location ?: existingProfile.location,
            website = updatedProfileDTO.website ?: existingProfile.website
        )

        return profileRepository.save(profileToUpdate)
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
    }


    fun deleteProfile(id: String) {
        if (!profileRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Профиль не найден")
        }
        profileRepository.deleteById(id)
    }
}
