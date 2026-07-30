package io.github.turtle358.callingpoint.utils;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.TflAPI;
import io.github.turtle358.callingpoint.models.JourneyLeg;
import io.github.turtle358.callingpoint.models.JourneyOption;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TflJourneyPlannerEngine {

    private final TflAPI api;
    private final Handler mainHandler;

    public TflJourneyPlannerEngine() {
        this.api = APIClient.getClient().create(TflAPI.class);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void findRoutes(String fromNaptan, String toNaptan, JourneyPlannerEngine.JourneyCallback callback) {
        mainHandler.post(() -> callback.onStatusUpdate("Searching TfL network..."));

        api.getJourneyResults(fromNaptan, toNaptan, false).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        List<JourneyOption> options = parseTflResponse(response.body());
                        if (options.isEmpty()) {
                            callback.onError("No TfL routes found.");
                        } else {
                            callback.onJourneysFound(options);
                        }
                    } catch (Exception e) {
                        callback.onError("Error parsing TfL data.");
                    }
                } else {
                    callback.onError("TfL Planner unavailable.");
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                callback.onError("Network error contacting TfL.");
            }
        });
    }

    private List<JourneyOption> parseTflResponse(JsonObject root) {
        List<JourneyOption> options = new ArrayList<>();
        if (!root.has("journeys")) return options;

        JsonArray journeys = root.getAsJsonArray("journeys");
        for (JsonElement jElem : journeys) {
            JsonObject jObj = jElem.getAsJsonObject();
            if (!jObj.has("legs")) continue;

            JsonArray legsData = jObj.getAsJsonArray("legs");
            List<JourneyLeg> parsedLegs = new ArrayList<>();

            for (JsonElement legElem : legsData) {
                JsonObject legObj = legElem.getAsJsonObject();

                JsonObject depPoint = legObj.getAsJsonObject("departurePoint");
                JsonObject arrPoint = legObj.getAsJsonObject("arrivalPoint");
                JsonObject modeInfo = legObj.getAsJsonObject("mode");

                String fromName = depPoint.has("commonName") ? depPoint.get("commonName").getAsString() : "Unknown";
                String toName = arrPoint.has("commonName") ? arrPoint.get("commonName").getAsString() : "Unknown";

                String modeId = modeInfo.has("id") ? modeInfo.get("id").getAsString() : "unknown";
                String specificLineId = modeId; // Fallback to generic mode (e.g., "dlr" or "tram")

                String depTimeStr = legObj.has("departureTime") ? legObj.get("departureTime").getAsString() : "";
                String arrTimeStr = legObj.has("arrivalTime") ? legObj.get("arrivalTime").getAsString() : "";
                String depTime = extractTime(depTimeStr);
                String arrTime = extractTime(arrTimeStr);

                String dest = toName; // Fallback destination if TfL doesn't provide a direction

                if (legObj.has("routeOptions")) {
                    JsonArray routeOptions = legObj.getAsJsonArray("routeOptions");
                    if (routeOptions.size() > 0) {
                        JsonObject firstOption = routeOptions.get(0).getAsJsonObject();

                        // 1. Get the ACTUAL direction/destination (e.g., "Westbound" or "Uxbridge")
                        if (firstOption.has("directions") && firstOption.getAsJsonArray("directions").size() > 0) {
                            dest = firstOption.getAsJsonArray("directions").get(0).getAsString();
                        }

                        // 2. Get the SPECIFIC tube line ID (e.g., "metropolitan" instead of "tube")
                        if (firstOption.has("lineIdentifier")) {
                            JsonObject lineIdentifier = firstOption.getAsJsonObject("lineIdentifier");
                            if (lineIdentifier.has("id")) {
                                specificLineId = lineIdentifier.get("id").getAsString();
                            }
                        }
                    }
                }

                // Map the TfL ID to your 2-letter code (e.g., "northern" -> "NO")
                String tocCode = TOCBrandHelper.getTocCodeForTflId(specificLineId);

                // Get the Full Name (e.g., "Northern Line") so the adapter can map the color!
                String operatorName = TOCBrandHelper.getNameForCode(tocCode);
                if (operatorName == null || operatorName.isEmpty()) {
                    operatorName = specificLineId;
                }

                String transportMode = "walking".equals(modeId) ? "TUBE/WALK" : "TRAIN";

                fromName = fromName.replace(" Underground Station", "").replace(" Rail Station", "");
                toName = toName.replace(" Underground Station", "").replace(" Rail Station", "");

                // Pass operatorName so JourneyLegAdapter can find the correct brand colour
                JourneyLeg leg = new JourneyLeg(
                        depPoint.has("naptanId") ? depPoint.get("naptanId").getAsString() : "", fromName,
                        arrPoint.has("naptanId") ? arrPoint.get("naptanId").getAsString() : "", toName,
                        dest, depTime, arrTime, operatorName, null, transportMode, new ArrayList<>(), null, depTime
                );
                parsedLegs.add(leg);
            }
            if (!parsedLegs.isEmpty()) {
                options.add(new JourneyOption(parsedLegs));
            }
        }
        return options;
    }

    // Extracts "15:41" from TfL's "2026-07-30T15:41:00" format
    private String extractTime(String isoDate) {
        if (isoDate == null || isoDate.length() < 16) return "--:--";
        try {
            return isoDate.substring(11, 16);
        } catch (Exception e) {
            return "--:--";
        }
    }
}