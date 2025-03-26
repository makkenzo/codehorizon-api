package com.makkenzo.codehorizon.updaters

import com.makkenzo.codehorizon.services.CourseService
import org.springframework.boot.CommandLineRunner

//@Component
class VideoLengthUpdater(private val courseService: CourseService) : CommandLineRunner {

    override fun run(vararg args: String?) {
//        println("🔄 Начинаю обновление длин всех видео в курсах...")
//        courseService.updateAllCoursesVideoLength()
//        println("✅ Обновление завершено.")
    }
}
