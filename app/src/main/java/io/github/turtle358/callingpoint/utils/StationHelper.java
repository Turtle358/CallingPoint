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

public class StationHelper {
    private static final Map<String, String> stationMap = new HashMap<>();
    private static final Map<String, String> crsMap = new HashMap<>();

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
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String stationName = keys.next();
                String crsCode = jsonObject.getString(stationName);
                stationMap.put(stationName, crsCode);
                crsMap.put(crsCode, stationName);
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
        if(crs==null)
            return "Unknown";
        return crsMap.getOrDefault(crs.toUpperCase(), "Unknown");
    }

}