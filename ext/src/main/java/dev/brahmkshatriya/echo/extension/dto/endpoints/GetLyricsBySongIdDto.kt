package dev.brahmkshatriya.echo.extension.dto.endpoints

import dev.brahmkshatriya.echo.extension.dto.types.ErrorDto
import dev.brahmkshatriya.echo.extension.dto.types.StructuredLyricsDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetLyricsBySongIdDto(
    @SerialName("subsonic-response")
    val subsonicResponse: SubsonicResponseDto,
) {
    @Serializable
    data class SubsonicResponseDto(
        val status: String,
        val error: ErrorDto? = null,

        val lyricsList: LyricsListDto? = null,
    ) {
        @Serializable
        data class LyricsListDto(
            val structuredLyrics: List<StructuredLyricsDto>? = null,
        )
    }
}
