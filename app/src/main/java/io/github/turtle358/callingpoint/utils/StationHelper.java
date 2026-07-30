package io.github.turtle358.callingpoint.utils;

import android.content.Context;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import io.github.turtle358.callingpoint.R;

public class StationHelper {

    private static JSONObject stationsJson = null;
    private static List<String> cachedStationNames = null;

    public static void init(Context context) {
        if (stationsJson == null) {
            try {
                InputStream is = context.getAssets().open("stations.json");
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();
                String jsonStr = new String(buffer, "UTF-8");
                stationsJson = new JSONObject(jsonStr);
            } catch (Exception e) {
                e.printStackTrace();
                stationsJson = new JSONObject();
            }
        }
    }

    public static List<String> getStationNames() {
        if (cachedStationNames == null) {
            cachedStationNames = new ArrayList<>();
            if (stationsJson != null) {
                Iterator<String> keys = stationsJson.keys();
                while (keys.hasNext()) {
                    cachedStationNames.add(keys.next());
                }
                Collections.sort(cachedStationNames);
            }
        }
        return cachedStationNames;
    }

    public static String getNameFromCode(String codeOrName) {
        if (stationsJson == null || codeOrName == null || codeOrName.isEmpty()) return "";

        if (stationsJson.has(codeOrName)) {
            return codeOrName;
        }

        Iterator<String> keys = stationsJson.keys();
        while (keys.hasNext()) {
            String stationName = keys.next();
            JSONObject stationData = stationsJson.optJSONObject(stationName);
            if (stationData != null) {
                String crs = stationData.optString("crs", "");
                String naptan = stationData.optString("naptan", "");

                if (codeOrName.equalsIgnoreCase(crs) || codeOrName.equalsIgnoreCase(naptan)) {
                    return stationName;
                }
            }
        }
        return "";
    }

    public static String getCrsFromName(String stationName) {
        if (stationsJson != null && stationName != null) {
            JSONObject data = stationsJson.optJSONObject(stationName);
            if (data != null) {
                return data.optString("crs", "");
            }
        }
        return "";
    }

    public static String getNaptanFromName(String stationName) {
        if (stationsJson != null && stationName != null) {
            JSONObject data = stationsJson.optJSONObject(stationName);
            if (data != null) {
                return data.optString("naptan", "");
            }
        }
        return "";
    }

    public static String getStationType(String stationName) {
        if (stationsJson != null && stationName != null) {
            JSONObject data = stationsJson.optJSONObject(stationName);
            if (data != null) {
                return data.optString("type", "national");
            }
        }
        return "national";
    }

    public static int getIconForStation(String name) {
        if (getStationType(name).equalsIgnoreCase("tfl") || getStationType(name).equalsIgnoreCase("tfl-national")) {
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