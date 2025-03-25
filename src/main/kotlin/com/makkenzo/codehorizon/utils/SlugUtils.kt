package com.makkenzo.codehorizon.utils

object SlugUtils {
    fun generateSlug(title: String): String {
        return title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-") // Заменяем пробелы и символы на "-"
            .trim('-')
    }

    fun generateUniqueSlug(
        input: String,
        existsBySlug: (String) -> Boolean
    ): String {
        val slug = generateSlug(input)
        var uniqueSlug = slug
        var counter = 1

        while (existsBySlug(uniqueSlug)) {
            uniqueSlug = "$slug-$counter"
            counter++
        }

        return uniqueSlug
    }
}