package com.makkenzo.codehorizon.models

import java.io.Serializable


data class Lesson(
    val id: String,
    var title: String,
    var slug: String? = null,
    var content: String? = null,
    var codeExamples: List<String> = emptyList(),
    var tasks: MutableList<Task> = mutableListOf(),
    var attachments: List<Attachment> = emptyList(),
    var mainAttachment: String? = null,
    var videoLength: Double? = 0.0
) : Serializable
