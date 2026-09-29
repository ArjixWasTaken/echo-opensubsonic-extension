package dev.brahmkshatriya.echo.extension.dto.types

import kotlinx.serialization.Serializable

@Serializable
data class DateDto(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
)