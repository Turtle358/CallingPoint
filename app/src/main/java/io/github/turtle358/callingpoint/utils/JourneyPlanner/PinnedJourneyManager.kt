package io.github.turtle358.callingpoint.utils.JourneyPlanner;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
            if (existing.departTime.equals(journey.departTime) &&
                    existing.duration.equals(journey.duration) &&
                    Objects.equals(existing.departureDate, journey.departureDate)) {
                return;
            }
        }
        currentList.add(journey);
        prefs.edit().putString(KEY_JOURNEYS, gson.toJson(currentList)).apply();
    }

    public void removeJourney(JourneyOption journeyToRemove) {
        List<JourneyOption> currentList = getPinnedJourneys();
        currentList.removeIf(j -> j.departTime.equals(journeyToRemove.departTime) &&
                j.duration.equals(journeyToRemove.duration) &&
                Objects.equals(j.departureDate, journeyToRemove.departureDate));
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
            if (existing.departTime.equals(journey.departTime) &&
                    existing.duration.equals(journey.duration) &&
                    Objects.equals(existing.departureDate, journey.departureDate)) {
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

    public void updateJourney(JourneyOption updatedJourney) {
        List<JourneyOption> currentList = getPinnedJourneys();
        for (int i = 0; i < currentList.size(); i++) {
            JourneyOption existing = currentList.get(i);
            if (existing.departTime.equals(updatedJourney.departTime) &&
                    existing.duration.equals(updatedJourney.duration) &&
                    Objects.equals(existing.departureDate, updatedJourney.departureDate)) {

                currentList.set(i, updatedJourney);
                prefs.edit().putString(KEY_JOURNEYS, gson.toJson(currentList)).apply();
                return;
            }
        }
    }
}