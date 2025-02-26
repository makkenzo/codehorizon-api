package com.makkenzo.codehorizon.services

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

    fun updateProfile(id: String, updatedProfile: Profile): Profile {
        val existingProfile = profileRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Профиль не найден") }

        val profileToUpdate = existingProfile.copy(
            avatarUrl = updatedProfile.avatarUrl ?: existingProfile.avatarUrl,
            bio = updatedProfile.bio ?: existingProfile.bio,
            firstName = updatedProfile.firstName ?: existingProfile.firstName,
            lastName = updatedProfile.lastName ?: existingProfile.lastName,
            location = updatedProfile.location ?: existingProfile.location,
            website = updatedProfile.website ?: existingProfile.website
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
