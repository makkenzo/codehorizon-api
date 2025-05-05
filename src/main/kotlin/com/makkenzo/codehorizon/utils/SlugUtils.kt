package com.makkenzo.codehorizon.utils

import java.text.Normalizer

object SlugUtils {
    private val cyrillicToLatinMap = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo", 'ж' to "zh",
        'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu",
        'я' to "ya",
        'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D", 'Е' to "E", 'Ё' to "Yo", 'Ж' to "Zh",
        'З' to "Z", 'И' to "I", 'Й' to "Y", 'К' to "K", 'Л' to "L", 'М' to "M", 'Н' to "N", 'О' to "O",
        'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T", 'У' to "U", 'Ф' to "F", 'Х' to "Kh", 'Ц' to "Ts",
        'Ч' to "Ch", 'Ш' to "Sh", 'Щ' to "Shch", 'Ъ' to "", 'Ы' to "Y", 'Ь' to "", 'Э' to "E", 'Ю' to "Yu",
        'Я' to "Ya"
    )

    private fun transliterate(input: String): String {
        val builder = StringBuilder()
        for (char in input) {
            builder.append(cyrillicToLatinMap[char] ?: char)
        }
        return builder.toString()
    }

    fun generateSlug(title: String): String {
        val transliteratedTitle = transliterate(title)

        val normalized = Normalizer.normalize(transliteratedTitle, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

        val lowerCase = normalized.lowercase()

        val replaced = lowerCase.replace(Regex("[^a-z0-9]+"), "-")

        return replaced.trim('-')
    }

    fun generateUniqueSlug(
        input: String,
        existsBySlug: (String) -> Boolean
    ): String {
        val slug = generateSlug(input)
        var uniqueSlug = slug
        var counter = 1

        if (uniqueSlug.isBlank()) {
            uniqueSlug = "untitled"
        }
        val baseSlugForCounter = uniqueSlug

        
        while (existsBySlug(uniqueSlug)) {
            uniqueSlug = "$baseSlugForCounter-$counter"
            counter++
        }

        return uniqueSlug
    }
}