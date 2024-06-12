package com.cheebeez.radio_player


import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import com.cheebeez.radio_player.models.Station


class StationsRepository(context: Context) {
    companion object {
        private val TAG: String = "StationsRepositoryLog";
    }

    var prefs: SharedPreferences;

    init {
        Log.d(TAG, "init package: ${context.packageName}")
        prefs = context.getSharedPreferences("RadioPrefs", Context.MODE_PRIVATE);
    }

    fun getStations(): List<Station> {
        Log.d(TAG, "getStations: ")
        try {
            val stationsJson = prefs.getString(K.STATIONS_KEY, "[]")
            Log.d(TAG, "stations: $stationsJson")
            return Station.fromJsonList(stationsJson!!)
        } catch (error: Exception) {
            Log.e(TAG, "getStations error: $error")
            return emptyList()
        }
    }

    fun saveStations(stations: List<Station>) {
        Log.d(TAG, "saveStations: $stations")
        try {
            val stationsJson = Station.toJsonList(stations)
            val editor = prefs.edit()
            editor.putString(K.STATIONS_KEY, stationsJson)
            editor.apply()
        } catch (error: Exception) {
            Log.e(TAG, "saveStations error: $error")
        }
    }
}