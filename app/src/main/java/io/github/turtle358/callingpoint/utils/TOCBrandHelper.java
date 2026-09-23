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
import java.util.Objects;

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
                t.tflId = obj.optString("tflId", t.code);
                t.acronym = obj.optString("acronym", t.code);
                t.name = obj.getString("name");
                t.colour = obj.getString("colour");
                t.secondary_colour = obj.optString("secondary_colour", "#ffffff");
                t.manager = obj.getString("manager");
                t.shown = obj.optInt("shown", 1);
                t.delayRepayUrl = obj.optString("delayRepayUrl", null);
                t.delayRepayMinTime = obj.optInt("delayRepayMinTime", 15);
                t.delayRepayMessage = obj.optString("delayRepayMessage", "");
                t.managerDescription = obj.optString("managerDescription", "");

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

    private static String cleanName(String name) {
        if (name == null) return "";
        return name.toLowerCase()
                .replace("-", " ")
                .replace(" line", "")
                .replace(" underground", "")
                .replace(" station", "")
                .trim();
    }

    public static Toc getTocByName(String operatorName) {
        if (operatorName == null) return null;

        for (Toc t : allTocs) {
            if (operatorName.equalsIgnoreCase(t.name) || operatorName.equalsIgnoreCase(t.code)) {
                return t;
            }
        }

        String cleanInput = cleanName(operatorName);

        for (Toc t : allTocs) {
            if (cleanInput.equalsIgnoreCase(cleanName(t.name))) {
                return t;
            }
        }

        for (Toc t : allTocs) {
            String cleanToc = cleanName(t.name);
            if (cleanInput.equals("northern") && cleanToc.equals("northern")) {
                return t;
            }
            if (cleanInput.equals("northern") && cleanToc.contains("line")) {
                continue;
            }

            if (!cleanToc.isEmpty() && cleanInput.length() > 2 && (cleanInput.contains(cleanToc) || cleanToc.contains(cleanInput))) {
                return t;
            }
        }

        return null;
    }

    private static String getManagerDescriptionForToc(String tocName) {
        Toc t = getTocByName(tocName);
        return t != null ? t.managerDescription : "";
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
        String lowerName = tocName.toLowerCase();
        if (lowerName.contains("bus")) {
            return Color.parseColor("#DC241F");
        } else if (lowerName.contains("walk")) {
            return Color.parseColor("#4285F4");
        }
        try {
            if (userColoursJson != null && userColoursJson.has(tocName)) {
                return Color.parseColor(userColoursJson.getString(tocName));
            }
            Toc t = getTocByName(tocName);
            if (t != null) {
                return Color.parseColor(t.colour);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Color.parseColor("#333333");
    }

    public static int getSecondaryColourForToc(String tocName) {
        String lowerName = tocName.toLowerCase();
        if (lowerName.contains("bus") || lowerName.contains("walk")) {
            return Color.parseColor("#FFFFFF");
        }

        try {
            Toc t = getTocByName(tocName);
            if (t != null && t.secondary_colour != null) {
                return Color.parseColor(t.secondary_colour);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Color.parseColor("#FFFFFF");
    }

    public static String getManagerForToc(String tocName) {
        Toc t = getTocByName(tocName);
        return t != null ? t.manager : "other";
    }

    public static String getDelayRepayUrlForName(String tocName) {
        Toc t = getTocByName(tocName);
        return t != null ? t.delayRepayUrl : null;
    }

    public static int getDelayRepayMinTimeForName(String tocName) {
        Toc t = getTocByName(tocName);
        return t != null ? t.delayRepayMinTime : 15;
    }

    public static int getIconForToc(String operatorName) {
        String lowerName = operatorName.toLowerCase();
        if (lowerName.contains("bus")) {
            return R.drawable.ic_bus;
        } else if (lowerName.contains("walk")) {
            return R.drawable.ic_walk;
        }
        String manager = getManagerForToc(operatorName);
        if (manager.equalsIgnoreCase("tfl") || manager.equalsIgnoreCase("tfl-national")) {
            return R.drawable.ic_tfl;
        } else if (manager.equalsIgnoreCase("gbr")){
            return R.drawable.ic_gbr;
        } else if (manager.equalsIgnoreCase("national")) {
            return R.drawable.ic_national_rail;
        } else if (manager.equalsIgnoreCase("scot")) {
            return R.drawable.ic_scotrail_small;
        } else if (manager.equalsIgnoreCase("merseytravel")) {
            return R.drawable.ic_merseyrail;
        } else if (manager.equalsIgnoreCase("wales")) {
            return R.drawable.ic_tfw;
        } else {
            return R.drawable.ic_train;
        }
    }

    public static String getManagerDescription(String tocName) {
        String manager = getManagerForToc(tocName);
        switch (manager.toLowerCase()) {
            case "tfl":
                return "Transport for London operates this service. It is part of the integrated London public transport network.";
            case "national":
                return tocName + " is a National Rail service, operating on the privatised UK railway network.";
            case "gbr":
                return "This service is currently operated by DfT Operator (DFTO) who will soon become Great British Railways, the publicly-owned railway.";
            case "scot":
                return "This service is operated by ScotRail, the national rail provider for Scotland.";
            case "wales":
                return "This service is operated by Transport for Wales, the national rail provider for Wales.";
            case "merseytravel":
                return "This service is part of the Merseyrail network, operating in the Liverpool City Region.";
            case "eurostar":
                return "This is a high-speed Eurostar service operating between the UK and mainland Europe.";
            case "open-access":
                return tocName + " is an open access operator. A private for profit company not part of the national rail network";
            case "london buses":
                return "This service is operated by London Buses, part of the Transport for London network.";
            default:
                return "This is an independent operator.";
        }
    }

    public static String getManagerName(String tocName) {
        String lowerName = tocName.toLowerCase();
        if (lowerName.contains("bus")) {
            return "London Buses";
        } else if (lowerName.contains("walk")) {
            return "Walking";
        }
        String manager = getManagerForToc(tocName);
        switch (manager.toLowerCase()) {
            case "tfl":
                return "Transport for London";
            case "national":
                return "National Rail — " + tocName;
            case "gbr":
                return "GBR | " + tocName;
            case "scot":
                return "Scotrail";
            case "wales":
                return "Transport for Wales";
            case "merseytravel":
                return "Merseyrail";
            case "eurostar":
                return "Eurostar";
            default:
                return tocName;
        }
    }

    public static void showTOCInfoDialog(android.content.Context context, String tocName) {
        android.app.Dialog dialog = new android.app.Dialog(context);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.layout_toc_popup);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        android.widget.ImageView logo = dialog.findViewById(R.id.popupTocLogo);
        android.widget.TextView name = dialog.findViewById(R.id.popupTocName);
        android.widget.TextView desc = dialog.findViewById(R.id.popupTocDesc);
        android.widget.Button btnClose = dialog.findViewById(R.id.popupCloseBtn);

        String manager = getManagerForToc(tocName);
        if(manager.equalsIgnoreCase("gbr")) {
            logo.setImageResource(R.drawable.ic_national_rail);
        } else if (manager.equalsIgnoreCase("scot")){
            logo.setImageResource(R.drawable.ic_scotrail);
        } else {
            logo.setImageResource(getIconForToc(tocName));
        }

        name.setText(getManagerName(tocName));
        String description = Objects.equals(getManagerDescriptionForToc(tocName), "") ? getManagerDescription(tocName) : getManagerDescriptionForToc(tocName);
        desc.setText(description);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    public static String getTocCodeForTflId(String tflId) {
        if (tflId == null) return null;
        for (Toc t : allTocs) {
            if (tflId.equalsIgnoreCase(t.tflId) || tflId.equalsIgnoreCase(t.code)) {
                return t.code;
            }
        }
        return tflId;
    }

    public static String getContainedName(String text) {
        if (text == null || text.isEmpty()) return null;
        String lower = text.toLowerCase();
        for (Toc t : allTocs) {
            if (t.name != null && !t.name.isEmpty() && lower.contains(t.name.toLowerCase())) {
                return t.name;
            }
        }
        return null;
    }

    public static boolean containsTocName(String text) {
        return getContainedName(text) != null;
    }

    public static int brightenIfDark(int color, float minLightness) {
        float[] hsl = new float[3];
        androidx.core.graphics.ColorUtils.colorToHSL(color, hsl);
        if (hsl[2] < minLightness) {
            hsl[2] = minLightness;
        }
        return androidx.core.graphics.ColorUtils.HSLToColor(hsl);
    }
}