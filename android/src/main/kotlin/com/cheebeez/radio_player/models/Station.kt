package com.cheebeez.radio_player.models

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Station(
    val id: Int, val title: String, val coverUrl: String, val streamUrl: String
) {

    companion object {
        private val gson = Gson()

        fun fromJson(jsonString: String): Station {
            return gson.fromJson(jsonString, Station::class.java)
        }

        fun fromJsonList(items: List<String>): List<Station> {
            return items.map { jsonString -> fromJson(jsonString) }
        }
    }

    fun toJson(): Map<String, Any> {
        val jsonString = gson.toJson(this)
        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        return gson.fromJson(jsonString, mapType)
    }

    fun toJsonEncoded(): String {
        return gson.toJson(this)
    }
}
