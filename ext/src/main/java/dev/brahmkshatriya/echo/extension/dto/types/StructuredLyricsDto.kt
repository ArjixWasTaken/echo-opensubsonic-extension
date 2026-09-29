package dev.brahmkshatriya.echo.extension.dto.types

import dev.brahmkshatriya.echo.common.models.Lyrics
import kotlinx.serialization.Serializable

@Serializable
data class StructuredLyricsDto(
    val displayArtist: String? = null,
    val displayTitle: String? = null,
    val lang: String = "und", // ISO 639 code, "und" or "xxx" when unknown
    val offset: Long = 0, // In milliseconds, positive means lines appear sooner
    val synced: Boolean = false,
    val line: List<LineDto> = emptyList(),
) {
    @Serializable
    data class LineDto(
        val start: Long? = null, // In milliseconds, only present in synced lyrics
        val value: String,
    )

    fun toLyrics(id: String, fallbackTitle: String, trackDuration: Long?): Lyrics {
        return Lyrics(
            id = id,
            title = displayTitle ?: fallbackTitle,
            subtitle = listOfNotNull(
                displayArtist,
                lang.takeIf { it != "und" && it != "xxx" }?.uppercase(),
                "Synced".takeIf { synced },
            ).joinToString(" • ").ifEmpty { null },
            lyrics = if (synced) toTimed(trackDuration) else toSimple(),
        )
    }

    private fun toSimple(): Lyrics.Simple {
        return Lyrics.Simple(line.joinToString("\n") { it.value })
    }

    private fun toTimed(trackDuration: Long?): Lyrics.Timed {
        val lines = line
            .map { (it.start ?: 0) - offset to it.value }
            .map { (start, value) -> start.coerceAtLeast(0) to value }
            .sortedBy { it.first }

        // Each line lasts until the next one starts, the last one until the track ends
        return Lyrics.Timed(
            lines.mapIndexed { i, (start, value) ->
                val end = lines.getOrNull(i + 1)?.first ?: trackDuration ?: start
                Lyrics.Item(value, start, end.coerceAtLeast(start))
            },
        )
    }
}
