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
            var stringList = prefs.getStringSet(K.STATIONS_KEY, null)?.toList()
            Log.d(TAG, "stations: $stringList")
            val items = stringList?.map { str -> Station.fromJson(str as String) }
            return items ?: emptyList();
        } catch (error: Exception) {
            Log.e(TAG, "getStations error: $error")
            return emptyList();
        }
    }

    fun saveStations(stations: List<Station>) {
        try {
            Log.d(TAG, "saveStations: $stations")
            var stringList = stations?.map { station -> station.toJsonEncoded() }
            val editor = prefs.edit()
            editor.putStringSet(K.STATIONS_KEY, stringList?.toSet())
            editor.commit()
        } catch (error: Exception) {
            Log.e(TAG, "saveStations error: $error")
        }
    }
}