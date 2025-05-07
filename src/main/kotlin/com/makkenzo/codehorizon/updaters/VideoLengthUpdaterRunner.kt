//package com.makkenzo.codehorizon.updaters
//
//import com.makkenzo.codehorizon.services.MediaProcessingService
//import org.slf4j.LoggerFactory
//import org.springframework.boot.ApplicationArguments
//import org.springframework.boot.ApplicationRunner
//import org.springframework.stereotype.Component
//
//@Component
//class VideoLengthUpdaterRunner(
//    private val mediaProcessingService: MediaProcessingService
//) : ApplicationRunner {
//    private val logger = LoggerFactory.getLogger(VideoLengthUpdaterRunner::class.java)
//
//    override fun run(args: ApplicationArguments?) {
//        logger.info("Запуск VideoLengthUpdaterRunner для обновления длительности видео уроков...")
//        try {
//            mediaProcessingService.updateMissingLessonVideoLengthsGlobally()
//            // mediaProcessingService.updateVideoLengthForAllCoursesWithZeroOrNull()
//        } catch (e: Exception) {
//            logger.error("Ошибка во время выполнения VideoLengthUpdaterRunner: {}", e.message, e)
//        }
//    }
//}
