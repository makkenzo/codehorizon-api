package com.makkenzo.codehorizon.models

enum class ProgrammingLanguage(val displayName: String, val fileExtension: String) {
    PYTHON("Python", ".py"),
    JAVASCRIPT("JavaScript", ".js");

    companion object {
        fun fromString(s: String?): ProgrammingLanguage? {
            return entries.find { it.name.equals(s, ignoreCase = true) || it.displayName.equals(s, ignoreCase = true) }
        }
    }
}
