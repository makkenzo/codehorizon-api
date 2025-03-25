package com.makkenzo.codehorizon.updaters

import com.makkenzo.codehorizon.services.ProfileService
import org.springframework.boot.CommandLineRunner

//@Component
class ProfileColorUpdater(
    private val profileService: ProfileService
) : CommandLineRunner {
    override fun run(vararg args: String?) {
//        profileService.updateAllProfilesWithDominantColor()
        println("Обновление доминирующих цветов завершено!")
    }
}
