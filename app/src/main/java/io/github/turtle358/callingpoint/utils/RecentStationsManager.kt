package io.github.turtle358.callingpoint.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.reflect.TypeToken
import com.google.gson.Gson

class RecentStationsManager(context: Context) {
    private val prefs: SharedPreferences
    private val gson: Gson
    companion object {
        const val MAX_RECENT = 5
    }
    init {
        prefs = context.getSharedPreferences("recent_stations_prefs", Context.MODE_PRIVATE)
        gson = Gson()
    }

    fun addStation(stationName: String) {
        if(stationName == null || stationName.trim().isEmpty()) return
        var recents: MutableList<String> = getRecentStations()
        recents.remove(stationName)
        recents.add(0, stationName)
        if(recents.size > MAX_RECENT) {
            recents = recents.subList(0, MAX_RECENT)
        }
        prefs.edit().putString("recent_list", gson.toJson(recents)).apply()
    }

    fun getRecentStations(): MutableList<String> {
        val json = prefs.getString("recent_list", null) ?: return ArrayList()
        return gson.fromJson(json, object : TypeToken<MutableList<String>>() {}.type)
    }


}