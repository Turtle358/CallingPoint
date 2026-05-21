package com.example.callingpoint;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PinnedDataManager {
    private static final String PREF_NAME = "PinnedDataPrefs";
    private static final String KEY_PINNED_STATIONS = "pinned_stations";
    private static final String KEY_PINNED_SERVICES = "pinned_services";

    private final SharedPreferences prefs;
    private final Gson gson;


    public PinnedDataManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }


    public List<String> getPinnedStations() {
        String json = prefs.getString(KEY_PINNED_STATIONS, "[]");
        Type type = new TypeToken<ArrayList<String>>() {}.getType();
        return gson.fromJson(json, type);
    }


    public void toggleStationPin(String crsCode) {
        List<String> stations = getPinnedStations();
        if (stations.contains(crsCode)) {
            stations.remove(crsCode);
        } else {
            stations.add(crsCode);
        }
        prefs.edit().putString(KEY_PINNED_STATIONS, gson.toJson(stations)).apply();
    }


    public boolean isStationPinned(String crsCode) {
        return getPinnedStations().contains(crsCode);
    }


    public List<String> getPinnedServices() {
        String json = prefs.getString(KEY_PINNED_SERVICES, "[]");
        Type type = new TypeToken<ArrayList<String>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void toggleServicePin(String serviceId) {
        List<String> services = getPinnedServices();
        if (services.contains(serviceId)) {
            services.remove(serviceId);
        } else {
            services.add(serviceId);
        }
        prefs.edit().putString(KEY_PINNED_SERVICES, gson.toJson(services)).apply();
    }

    public boolean isServicePinned(String serviceId) {
        return getPinnedServices().contains(serviceId);
    }
}