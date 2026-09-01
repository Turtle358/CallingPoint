package io.github.turtle358.callingpoint.utils.JourneyPlanner

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.turtle358.callingpoint.models.JourneyOption

class PinnedJourneyManager(context: Context) {
    private val prefs: SharedPreferences
    private val gson: Gson

    init {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        gson = Gson()
    }

    fun saveAllPinnedJourneys(journeys: MutableList<JourneyOption?>?) {
        prefs.edit().putString(KEY_JOURNEYS, gson.toJson(journeys)).apply()
    }

    fun saveJourney(journey: JourneyOption) {
        val currentList = this.pinnedJourneys
        for (existing in currentList) {
            if (existing.departTime == journey.departTime &&
                existing.duration == journey.duration &&
                existing.departureDate == journey.departureDate
            ) {
                return
            }
        }
        currentList.add(journey)
        prefs.edit().putString(KEY_JOURNEYS, gson.toJson(currentList)).apply()
    }

    fun removeJourney(journeyToRemove: JourneyOption) {
        val currentList = this.pinnedJourneys
        currentList.removeIf { j: JourneyOption? ->
            j!!.departTime == journeyToRemove.departTime &&
                    j.duration == journeyToRemove.duration &&
                    j.departureDate == journeyToRemove.departureDate
        }
        prefs.edit().putString(KEY_JOURNEYS, gson.toJson(currentList)).apply()
    }

    val pinnedJourneys: MutableList<JourneyOption>
        get() {
            val json =
                prefs.getString(KEY_JOURNEYS, null)
            if (json == null || json.isEmpty()) {
                return ArrayList<JourneyOption>()
            }
            val type = object :
                TypeToken<MutableList<JourneyOption?>?>() {}.getType()
            return gson.fromJson<MutableList<JourneyOption?>>(json, type) as MutableList<JourneyOption>
        }

    fun isJourneyPinned(journey: JourneyOption): Boolean {
        val currentList = this.pinnedJourneys
        for (existing in currentList) {
            if (existing.departTime == journey.departTime &&
                existing.duration == journey.duration &&
                existing.departureDate == journey.departureDate
            ) {
                return true
            }
        }
        return false
    }

    fun toggleJourneyPin(journey: JourneyOption) {
        if (isJourneyPinned(journey)) {
            removeJourney(journey)
        } else {
            saveJourney(journey)
        }
    }

    fun updateJourney(updatedJourney: JourneyOption) {
        val currentList = this.pinnedJourneys
        for (i in currentList.indices) {
            val existing = currentList.get(i)
            if (existing.departTime == updatedJourney.departTime &&
                existing.duration == updatedJourney.duration &&
                existing.departureDate == updatedJourney.departureDate
            ) {
                currentList.set(i, updatedJourney)
                prefs.edit().putString(KEY_JOURNEYS, gson.toJson(currentList)).apply()
                return
            }
        }
    }

    companion object {
        private const val PREF_NAME = "pinned_journeys_prefs"
        private const val KEY_JOURNEYS = "saved_journeys_list"
    }
}