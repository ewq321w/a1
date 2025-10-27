package com.example.m.data

/**
 * Data class representing the result of a lyrics fetch operation
 */
data class LyricsResult(
    val lyrics: String?,
    val source: String,
    val isSuccessful: Boolean
) {
    /**
     * Returns true if lyrics were successfully fetched and are not empty
     */
    fun hasLyrics(): Boolean = !lyrics.isNullOrBlank()
}
