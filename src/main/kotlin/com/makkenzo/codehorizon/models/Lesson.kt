package com.makkenzo.codehorizon.models

import java.io.Serializable


data class Lesson(
    val id: String? = null,
    var title: String,
    var slug: String? = null,
    var content: String? = null,
    var codeExamples: List<String> = emptyList(),
    var tasks: List<Task> = emptyList(),
    var attachments: List<Attachment> = emptyList(),
    var mainAttachment: String? = null
) : Serializable
