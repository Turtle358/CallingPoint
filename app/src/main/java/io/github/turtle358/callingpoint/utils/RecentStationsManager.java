package io.github.turtle358.callingpoint.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class RecentStationsManager {
    private final SharedPreferences prefs;
    private final Gson gson;
    private static final int MAX_RECENT = 5;

    public RecentStationsManager(Context context) {
        prefs = context.getSharedPreferences("recent_stations_prefs", Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public void addStation(String stationName) {
        if (stationName == null || stationName.trim().isEmpty()) return;

        List<String> recents = getRecentStations();
        recents.remove(stationName);
        recents.add(0, stationName);

        if (recents.size() > MAX_RECENT) {
            recents = recents.subList(0, MAX_RECENT);
        }
        prefs.edit().putString("recent_list", gson.toJson(recents)).apply();
    }

    public List<String> getRecentStations() {
        String json = prefs.getString("recent_list", null);
        if (json == null) return new ArrayList<>();
        return gson.fromJson(json, new TypeToken<List<String>>(){}.getType());
    }
}