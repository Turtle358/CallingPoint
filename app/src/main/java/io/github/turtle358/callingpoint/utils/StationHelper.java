package io.github.turtle358.callingpoint.utils;

import android.content.Context;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.github.turtle358.callingpoint.R;

public class StationHelper {
    private static final Map<String, String> stationMap = new HashMap<>();
    private static final Map<String, String> crsMap = new HashMap<>();

    private static final Map<String, String> typeMap = new HashMap<>();

    public static void init(Context context) {
        String json = null;
        try {
            File file = new File(context.getFilesDir(), "stations_remote.json");
            if (file.exists()) {
                FileInputStream fis = context.openFileInput("stations_remote.json");
                int size = fis.available();
                byte[] buffer = new byte[size];
                fis.read(buffer);
                fis.close();
                json = new String(buffer, StandardCharsets.UTF_8);
            } else {
                InputStream is = context.getAssets().open("stations.json");
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();
                json = new String(buffer, StandardCharsets.UTF_8);
            }

            JSONObject jsonObject = new JSONObject(json);
            stationMap.clear();
            crsMap.clear();
            typeMap.clear();

            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String stationName = keys.next();

                JSONObject stationData = jsonObject.getJSONObject(stationName);
                String crsCode = stationData.getString("crs");

                String type = stationData.optString("type", "other");

                stationMap.put(stationName, crsCode);
                crsMap.put(crsCode, stationName);
                typeMap.put(stationName, type);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<String> getStationNames() {
        return new ArrayList<>(stationMap.keySet());
    }

    public static String getCrsFromName(String name) {
        return stationMap.get(name);
    }

    public static String getNameFromCrs(String crs) {
        if(crs == null)
            return "Unknown";
        return crsMap.getOrDefault(crs.toUpperCase(), "Unknown");
    }

    public static String getStationType(String name) {
        if (name == null) {
            return "national";
        }
        return typeMap.getOrDefault(name, "national");
    }

    public static int getIconForStation(String name) {
        if (getStationType(name).equalsIgnoreCase("tfl")) {
            return R.drawable.ic_tfl;
        } else if (getStationType(name).equalsIgnoreCase("gbr")){
            return R.drawable.ic_gbr;
        } else if (getStationType(name).equalsIgnoreCase("national")) {
            return R.drawable.ic_national_rail;
        } else if (getStationType(name).equalsIgnoreCase("eurostar")) {
            return R.drawable.ic_eurostar;
        } else if (getStationType(name).equalsIgnoreCase("scot")) {
            return R.drawable.ic_scotrail_small;
        } else if (getStationType(name).equalsIgnoreCase("wales")) {
            return R.drawable.ic_tfw;
        } else if (getStationType(name).equalsIgnoreCase("merseytravel")) {
            return R.drawable.ic_merseyrail;
        } else {
            return R.drawable.ic_train;
        }
    }
}