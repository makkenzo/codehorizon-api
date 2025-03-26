package com.makkenzo.codehorizon.utils

object MediaUtils {
    fun getVideoDuration(videoUrl: String): Double {
        val process = ProcessBuilder(
            "ffprobe",
            "-v",
            "error",
            "-select_streams",
            "v:0",
            "-show_entries",
            "format=duration",
            "-of",
            "csv=p=0",
            videoUrl
        ).start()

        val duration = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()

        return duration.toDoubleOrNull() ?: 0.0
    }
}