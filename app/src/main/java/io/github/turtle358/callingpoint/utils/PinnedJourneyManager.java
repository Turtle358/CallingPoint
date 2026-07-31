package io.github.turtle358.callingpoint.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import io.github.turtle358.callingpoint.models.JourneyOption;

public class PinnedJourneyManager {

    private static final String PREF_NAME = "pinned_journeys_prefs";
    private static final String KEY_JOURNEYS = "saved_journeys_list";
    private final SharedPreferences prefs;
    private final Gson gson;

    public PinnedJourneyManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public void saveAllPinnedJourneys(List<JourneyOption> journeys) {
        prefs.edit().putString(KEY_JOURNEYS, gson.toJson(journeys)).apply();
    }

    public void saveJourney(JourneyOption journey) {
        List<JourneyOption> currentList = getPinnedJourneys();
        for (JourneyOption existing : currentList) {
            if (existing.departTime.equals(journey.departTime) && existing.duration.equals(journey.duration)) {
                return;
            }
        }
        currentList.add(journey);
        prefs.edit().putString(KEY_JOURNEYS, gson.toJson(currentList)).apply();
    }

    public void removeJourney(JourneyOption journeyToRemove) {
        List<JourneyOption> currentList = getPinnedJourneys();
        currentList.removeIf(j -> j.departTime.equals(journeyToRemove.departTime) && j.duration.equals(journeyToRemove.duration));
        prefs.edit().putString(KEY_JOURNEYS, gson.toJson(currentList)).apply();
    }

    public List<JourneyOption> getPinnedJourneys() {
        String json = prefs.getString(KEY_JOURNEYS, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<JourneyOption>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public boolean isJourneyPinned(JourneyOption journey) {
        List<JourneyOption> currentList = getPinnedJourneys();
        for (JourneyOption existing : currentList) {
            if (existing.departTime.equals(journey.departTime) && existing.duration.equals(journey.duration)) {
                return true;
            }
        }
        return false;
    }

    public void toggleJourneyPin(JourneyOption journey) {
        if (isJourneyPinned(journey)) {
            removeJourney(journey);
        } else {
            saveJourney(journey);
        }
    }
}