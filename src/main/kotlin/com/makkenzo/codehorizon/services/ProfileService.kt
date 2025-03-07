package com.makkenzo.codehorizon.services

import com.makkenzo.codehorizon.dtos.UpdateProfileDTO
import com.makkenzo.codehorizon.models.Profile
import com.makkenzo.codehorizon.repositories.ProfileRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

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

        val profileToUpdate = existingProfile.copy(
            avatarUrl = updatedProfileDTO.avatarUrl ?: existingProfile.avatarUrl,
            bio = updatedProfileDTO.bio ?: existingProfile.bio,
            firstName = updatedProfileDTO.firstName ?: existingProfile.firstName,
            lastName = updatedProfileDTO.lastName ?: existingProfile.lastName,
            location = updatedProfileDTO.location ?: existingProfile.location,
            website = updatedProfileDTO.website ?: existingProfile.website
        )

        return profileRepository.save(profileToUpdate)
    }


    fun deleteProfile(id: String) {
        if (!profileRepository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Профиль не найден")
        }
        profileRepository.deleteById(id)
    }
}
