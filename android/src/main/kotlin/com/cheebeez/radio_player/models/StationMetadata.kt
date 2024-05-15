package com.cheebeez.radio_player.models

import com.cheebeez.radio_player.K
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

data class StationMetadata(
    val title: String,
    val artist: String,
    var cover: String?,
) {
    val trackTitle: String
        get() = if (title != K.UNKNOWN && title.isNotBlank() && artist != K.UNKNOWN && artist.isNotBlank()) "$title - $artist" else ""

    val isDefault: Boolean
        get() = cover.isNullOrBlank() || (cover?.contains(K.DEFAULT_SONG_IMAGE_NAME) ?: true)

    companion object {
        private val gson = Gson()
        fun fromJson(jsonString: String): StationMetadata {
            return try {
                gson.fromJson(jsonString, StationMetadata::class.java)
            } catch (e: JsonSyntaxException) {
                StationMetadata(
                    title = K.UNKNOWN,
                    artist = K.UNKNOWN,
                    cover = K.DEFAULT_SONG_IMAGE_NAME
                )
            }
        }
    }
}