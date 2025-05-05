package com.makkenzo.codehorizon.updaters

import com.makkenzo.codehorizon.services.MediaProcessingService
import org.springframework.boot.CommandLineRunner

//@Component
class VideoLengthUpdater(
    private val mediaProcessingService: MediaProcessingService
) : CommandLineRunner {
    override fun run(vararg args: String?) {
        println("🔄 Начинаю обновление длин всех видео в курсах...")
        mediaProcessingService.updateVideoLengthForAllCoursesWithZeroOrNull()
        println("✅ Обновление завершено.")
    }
}
