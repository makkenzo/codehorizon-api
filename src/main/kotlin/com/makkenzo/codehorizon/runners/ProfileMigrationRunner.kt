package com.makkenzo.codehorizon.runners

//import com.makkenzo.codehorizon.models.Profile
//import com.makkenzo.codehorizon.repositories.ProfileRepository
//import com.makkenzo.codehorizon.repositories.UserRepository
//import org.springframework.boot.CommandLineRunner
//import org.springframework.stereotype.Component
//
//@Component
//class ProfileMigrationRunner(
//    private val userRepository: UserRepository,
//    private val profileRepository: ProfileRepository
//) : CommandLineRunner {
//    override fun run(vararg args: String?) {
//        val users = userRepository.findAll()
//        users.forEach { user ->
//            if (user.id != null && profileRepository.findByUserId(user.id) == null) {
//                val newProfile = Profile(
//                    userId = user.id,
//                )
//                profileRepository.save(newProfile)
//                println("Создан профиль для пользователя: ${user.username}")
//            }
//        }
//    }
//}