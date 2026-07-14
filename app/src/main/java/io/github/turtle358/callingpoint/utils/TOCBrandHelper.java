package io.github.turtle358.callingpoint.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.Toc;

public class TOCBrandHelper {
    private static final List<Toc> allTocs = new ArrayList<>();
    private static JSONObject userColoursJson;

    public static void init(Context context) {
        String json = null;
        try {
            File file = new File(context.getFilesDir(), "tocs_remote.json");
            if (file.exists()) {
                FileInputStream fis = context.openFileInput("tocs_remote.json");
                int size = fis.available();
                byte[] buffer = new byte[size];
                fis.read(buffer);
                fis.close();
                json = new String(buffer, StandardCharsets.UTF_8);
            } else {
                InputStream is = context.getAssets().open("tocs.json");
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();
                json = new String(buffer, StandardCharsets.UTF_8);
            }

            JSONArray array = new JSONArray(json);
            allTocs.clear();

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Toc t = new Toc();
                t.code = obj.getString("code");
                t.acronym = obj.optString("acronym", t.code);
                t.name = obj.getString("name");
                t.colour = obj.getString("colour");
                t.manager = obj.getString("manager");
                t.shown = obj.optInt("shown", 1);
                t.delayRepayUrl = obj.optString("delayRepayUrl", null);

                allTocs.add(t);
            }
            allTocs.sort((t1, t2) -> t1.name.compareToIgnoreCase(t2.name));

        } catch (Exception e) {
            e.printStackTrace();
        }

        SharedPreferences prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        String jsonString = prefs.getString("TOC_COLORS_USER", "{}");
        try {
            userColoursJson = new JSONObject(jsonString);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<Toc> getVisibleTocs() {
        List<Toc> visibleTocs = new ArrayList<>();
        for (Toc t : allTocs) {
            if (t.shown != 0) {
                visibleTocs.add(t);
            }
        }
        return visibleTocs;
    }

    public static String getNameForCode(String code) {
        if (code == null) return "Unknown";
        for (Toc t : allTocs) {
            if (t.code.equalsIgnoreCase(code)) return t.name;
        }
        return code;
    }

    public static String getAcronymForCode(String code) {
        if (code == null) return "??";
        for (Toc t : allTocs) {
            if (t.code.equalsIgnoreCase(code)) {
                return t.acronym != null ? t.acronym : code;
            }
        }
        return code;
    }

    public static int getColourForToc(String tocName) {
        try {
            if (userColoursJson != null && userColoursJson.has(tocName)) {
                return Color.parseColor(userColoursJson.getString(tocName));
            }
            for (Toc t : allTocs) {
                if (t.name.equalsIgnoreCase(tocName)) {
                    return Color.parseColor(t.colour);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Color.parseColor("#333333");
    }

    public static String getManagerForToc(String tocname) {
        for (Toc t : allTocs) {
            if(t.name.equalsIgnoreCase(tocname)) {
                return t.manager;
            }
        }
        return "other";
    }

    public static String getDelayRepayUrlForName(String tocName) {
        if (tocName == null) return null;
        for (Toc t : allTocs) {
            if (t.name.equalsIgnoreCase(tocName)) return t.delayRepayUrl;
        }
        return null;
    }

    public static int getIconForToc(String operatorName) {
        if (getManagerForToc(operatorName).equalsIgnoreCase("tfl")) {
            return R.drawable.ic_tfl;
        } else if (getManagerForToc(operatorName).equalsIgnoreCase("gbr")){
            return R.drawable.ic_gbr;
        } else if (getManagerForToc(operatorName).equalsIgnoreCase("national")) {
            return R.drawable.ic_national_rail;
        } else if (getManagerForToc(operatorName).equalsIgnoreCase("eurostar")) {
            return R.drawable.ic_eurostar;
        } else {
            return R.drawable.ic_train;
        }
    }
}