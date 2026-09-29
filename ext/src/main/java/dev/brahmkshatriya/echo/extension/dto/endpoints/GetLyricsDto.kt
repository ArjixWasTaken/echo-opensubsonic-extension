package dev.brahmkshatriya.echo.extension.dto.endpoints

import dev.brahmkshatriya.echo.extension.dto.types.ErrorDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetLyricsDto(
    @SerialName("subsonic-response")
    val subsonicResponse: SubsonicResponseDto,
) {
    @Serializable
    data class SubsonicResponseDto(
        val status: String,
        val error: ErrorDto? = null,

        val lyrics: LyricsDto? = null,
    ) {
        @Serializable
        data class LyricsDto(
            val artist: String? = null,
            val title: String? = null,
            val value: String? = null,
        )
    }
}
