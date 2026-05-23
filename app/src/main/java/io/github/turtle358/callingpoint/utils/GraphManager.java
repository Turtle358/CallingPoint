package io.github.turtle358.callingpoint.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GraphManager {

    private static final String PREF_NAME = "NetworkGraphPrefs";
    private static final String KEY_GRAPH = "graph_data";

    private static GraphManager instance;
    private final SharedPreferences prefs;
    private final Gson gson;
    private Map<String, Set<String>> networkGraph;

    private GraphManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        loadGraph();
    }

    public static synchronized GraphManager getInstance(Context context) {
        if (instance == null) {
            instance = new GraphManager(context);
        }
        return instance;
    }

    private void loadGraph() {
        String json = prefs.getString(KEY_GRAPH, null);
        if (json != null) {
            Type type = new TypeToken<Map<String, Set<String>>>() {}.getType();
            networkGraph = gson.fromJson(json, type);
        } else {
            networkGraph = new HashMap<>();
        }
    }

    private void saveGraph() {
        String json = gson.toJson(networkGraph);
        prefs.edit().putString(KEY_GRAPH, json).apply();
    }


    public void addConnection(String originCrs, String destinationCrs) {
        if (originCrs == null || destinationCrs == null || originCrs.equals(destinationCrs)) return;

        if (!networkGraph.containsKey(originCrs)) {
            networkGraph.put(originCrs, new HashSet<>());
        }

        boolean isNewConnection = networkGraph.get(originCrs).add(destinationCrs);

        if (isNewConnection) {
            saveGraph();
        }
    }

    public Set<String> getConnections(String crs) {
        return networkGraph.getOrDefault(crs, new HashSet<>());
    }

    public int getKnownStationCount() {
        return networkGraph.size();
    }

    public void clearMemory() {
        networkGraph.clear();
        prefs.edit().remove(KEY_GRAPH).apply();
    }
}