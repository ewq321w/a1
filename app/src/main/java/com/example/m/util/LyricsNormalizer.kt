package com.example.m.util

import java.util.Locale

/**
 * Utility class for normalizing and generating variations of artist names and song titles
 * to improve lyrics search success rate
 */
object LyricsNormalizer {

    /**
     * Cleans an artist name by removing YouTube and streaming service suffixes
     * This is done before normalization to preserve the original artist intent
     */
    fun cleanArtistName(artist: String): String {
        return artist
            .replace(" - Topic", "", ignoreCase = true)
            .replace(" Topic", "", ignoreCase = true)
            .replace(" - Official", "", ignoreCase = true)
            .replace(" Official", "", ignoreCase = true)
            .replace(" VEVO", "", ignoreCase = true)
            .replace(" Records", "", ignoreCase = true)
            .trim()
    }

    /**
     * Cleans a song title by removing video/audio indicators while preserving version info
     * This is done before normalization to keep important version information
     */
    fun cleanSongTitle(title: String): String {
        return title
            // Only remove video/audio indicators, keep version info like "Remastered", "Deluxe"
            .replace(" - Official Video", "", ignoreCase = true)
            .replace(" Official Video", "", ignoreCase = true)
            .replace(" (Official Audio)", "", ignoreCase = true)
            .replace(" Official Audio", "", ignoreCase = true)
            .replace(" Lyric Video", "", ignoreCase = true)
            .replace(" Music Video", "", ignoreCase = true)
            // Clean up spacing
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Normalizes an artist name by removing common prefixes, suffixes, and cleaning up formatting
     */
    fun normalizeArtist(artist: String): String {
        return artist
            .trim()
            .lowercase(Locale.ROOT) // Use ROOT locale to avoid Turkish i->ı conversion
            // Remove common prefixes
            .removePrefix("the ")
            .removePrefix("a ")
            .removePrefix("an ")
            // Remove featuring information
            .split(" feat.", " ft.", " featuring ", " & ", " and ").first()
            .trim()
    }

    /**
     * Normalizes a song title with minimal changes to preserve exact matching
     * UPDATED: Preserves case and version info to avoid locale issues and improve matching
     */
    fun normalizeTitle(title: String): String {
        return title
            .trim()
            // Don't convert to lowercase - preserves original case to avoid locale issues
            // Remove featuring information only
            .split(" feat.", " ft.", " featuring ").first()
            .trim()
            // Clean up multiple spaces
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Prepares a string for use in an AZLyrics URL by removing all non-alphanumeric characters.
     */
    fun normalizeForAZLyricsUrl(input: String): String {
        return input.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9]"), "")
    }

    /**
     * Generates variations of an artist name to try different search strategies
     */
    fun generateArtistVariations(normalizedArtist: String): List<String> {
        val variations = mutableListOf<String>()

        // Original normalized version
        variations.add(normalizedArtist)

        // Try with "the" prefix if it doesn't already have it
        if (!normalizedArtist.startsWith("the ")) {
            variations.add("the $normalizedArtist")
        }

        // Try first word only (for cases like "Artist Name" -> "Artist")
        val firstWord = normalizedArtist.split(" ").first()
        if (firstWord != normalizedArtist) {
            variations.add(firstWord)
        }

        // Try without spaces (for cases like "Artist Name" -> "ArtistName")
        if (normalizedArtist.contains(" ")) {
            variations.add(normalizedArtist.replace(" ", ""))
        }

        return variations.distinct()
    }

    /**
     * Generates variations of a song title to try different search strategies
     */
    fun generateTitleVariations(normalizedTitle: String): List<String> {
        val variations = mutableListOf<String>()

        // Original normalized version
        variations.add(normalizedTitle)

        // Try first part before dash (for cases like "Song - Something" -> "Song")
        if (normalizedTitle.contains(" - ")) {
            variations.add(normalizedTitle.split(" - ").first().trim())
        }

        // Try without common words
        val withoutCommonWords = normalizedTitle
            .replace(Regex("\\b(the|a|an|and|or|of|in|on|at|to|for|with|by)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (withoutCommonWords != normalizedTitle && withoutCommonWords.isNotBlank()) {
            variations.add(withoutCommonWords)
        }

        // Try without spaces
        if (normalizedTitle.contains(" ")) {
            variations.add(normalizedTitle.replace(" ", ""))
        }

        return variations.distinct()
    }
}