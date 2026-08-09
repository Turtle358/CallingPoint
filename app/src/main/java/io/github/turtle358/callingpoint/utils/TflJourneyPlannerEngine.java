package io.github.turtle358.callingpoint.utils;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.NationalRailAPI;
import io.github.turtle358.callingpoint.api.TflAPI;
import io.github.turtle358.callingpoint.models.JourneyLeg;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.models.StationDepartureResponse;
import io.github.turtle358.callingpoint.models.Toc;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TflJourneyPlannerEngine {

    private final TflAPI tflApi;
    private final NationalRailAPI nationalRailApi;
    private final Handler mainHandler;
    private final ExecutorService executor;

    public TflJourneyPlannerEngine() {
        this.tflApi = APIClient.getClient().create(TflAPI.class);
        this.nationalRailApi = APIClient.getClient().create(NationalRailAPI.class);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void findRoutes(String fromNaptan, String toNaptan, String date, String time, String timeIs, JourneyPlannerEngine.JourneyCallback callback) {
        mainHandler.post(() -> callback.onStatusUpdate("Searching TfL network..."));

        tflApi.getJourneyResults(fromNaptan, toNaptan, false, date, time, timeIs).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executor.execute(() -> {
                        try {
                            List<JourneyOption> options = parseTflResponse(response.body());

                            if (isToday(date)) {
                                mainHandler.post(() -> callback.onStatusUpdate("Matching National Rail services..."));
                                enrichNationalRailServices(options);
                            }

                            mainHandler.post(() -> {
                                if (options.isEmpty()) {
                                    callback.onError("No TfL routes found.");
                                } else {
                                    callback.onJourneysFound(options);
                                }
                            });
                        } catch (Exception e) {
                            mainHandler.post(() -> callback.onError("Error processing journey data."));
                        }
                    });
                } else {
                    mainHandler.post(() -> callback.onError("TfL Planner unavailable."));
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                mainHandler.post(() -> callback.onError("Network error contacting TfL."));
            }
        });
    }

    private boolean isToday(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return true;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.UK);
            String todayStr = sdf.format(new Date());
            return todayStr.equals(dateStr.trim());
        } catch (Exception e) {
            return true;
        }
    }

    private void enrichNationalRailServices(List<JourneyOption> options) {
        for (JourneyOption option : options) {
            if (option.legs == null) continue;
            for (JourneyLeg leg : option.legs) {
                if (!"TRAIN".equals(leg.transportMode)) continue;

                String fromCrs = resolveCrs(leg.fromStationName);
                String toCrs = resolveCrs(leg.toStationName);

                if (fromCrs != null && toCrs != null) {
                    try {
                        int timeOffset = calculateTimeOffset(leg.departureTime);

                        Response<StationDepartureResponse> res = nationalRailApi
                                .getStationDepartures(fromCrs, 40, toCrs, "to", timeOffset, 120)
                                .execute();

                        if (res.isSuccessful() && res.body() != null && res.body().trainServices != null) {
                            for (StationDepartureResponse.TrainService service : res.body().trainServices) {
                                if (service.std != null && isTimeMatching(service.std, leg.departureTime, 2)) {
                                    leg.serviceId = service.serviceID;

                                    if (service.operatorName != null && !service.operatorName.isEmpty()) {
                                        Toc matchedToc = TOCBrandHelper.getTocByName(service.operatorName);
                                        if (matchedToc != null) {
                                            leg.operatorName = matchedToc.name;
                                        }
                                    }
                                    break;
                                }
                            }
                        }

                    } catch (Exception e) {
                    }
                }
            }
        }
    }

    private String resolveCrs(String stationName) {
        if (stationName == null) return null;
        String crs = StationHelper.getCrsFromName(stationName);
        if (crs == null && stationName.startsWith("London ")) {
            crs = StationHelper.getCrsFromName(stationName.replace("London ", ""));
        }
        if (crs == null && !stationName.startsWith("London ")) {
            crs = StationHelper.getCrsFromName("London " + stationName);
        }
        return crs;
    }

    private int calculateTimeOffset(String departureTimeStr) {
        if (departureTimeStr == null || !departureTimeStr.contains(":")) return 0;
        try {
            String[] parts = departureTimeStr.split(":");
            int depMins = Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());

            Calendar now = Calendar.getInstance();
            int currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

            int diff = depMins - currentMins;

            int offset = diff - 10;
            return Math.max(-120, Math.min(120, offset));
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isTimeMatching(String time1, String time2, int toleranceMins) {
        try {
            String[] p1 = time1.trim().split(":");
            String[] p2 = time2.trim().split(":");

            int m1 = Integer.parseInt(p1[0].trim()) * 60 + Integer.parseInt(p1[1].trim());
            int m2 = Integer.parseInt(p2[0].trim()) * 60 + Integer.parseInt(p2[1].trim());

            return Math.abs(m1 - m2) <= toleranceMins;
        } catch (Exception e) {
            return time1.trim().equalsIgnoreCase(time2.trim());
        }
    }

    private List<JourneyOption> parseTflResponse(JsonObject root) {
        List<JourneyOption> options = new ArrayList<>();
        if (!root.has("journeys")) return options;

        JsonArray journeys = root.getAsJsonArray("journeys");
        for (JsonElement jElem : journeys) {
            JsonObject jObj = jElem.getAsJsonObject();
            if (!jObj.has("legs")) continue;

            String journeyStartDate = null;
            if (jObj.has("startDateTime")) {
                journeyStartDate = jObj.get("startDateTime").getAsString();
            }

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
                String specificLineId = modeId;
                String depTimeStr = legObj.has("departureTime") ? legObj.get("departureTime").getAsString() : "";
                String arrTimeStr = legObj.has("arrivalTime") ? legObj.get("arrivalTime").getAsString() : "";
                String depTime = extractTime(depTimeStr);
                String arrTime = extractTime(arrTimeStr);

                String dest = toName;

                if (legObj.has("routeOptions")) {
                    JsonArray routeOptions = legObj.getAsJsonArray("routeOptions");
                    if (routeOptions.size() > 0) {
                        JsonObject firstOption = routeOptions.get(0).getAsJsonObject();

                        if (firstOption.has("directions") && firstOption.getAsJsonArray("directions").size() > 0) {
                            dest = firstOption.getAsJsonArray("directions").get(0).getAsString();
                        }

                        if (firstOption.has("lineIdentifier")) {
                            JsonObject lineIdentifier = firstOption.getAsJsonObject("lineIdentifier");
                            if (lineIdentifier.has("id")) {
                                specificLineId = lineIdentifier.get("id").getAsString();
                            }
                        }
                    }
                }

                String operatorName;
                String transportMode;

                if ("bus".equalsIgnoreCase(modeId)) {
                    transportMode = "BUS";
                    operatorName = "Bus " + specificLineId.toUpperCase();
                } else if ("walking".equalsIgnoreCase(modeId)) {
                    transportMode = "TUBE/WALK";
                    operatorName = "Walking";
                } else if ("tube".equalsIgnoreCase(modeId) || "dlr".equalsIgnoreCase(modeId)
                        || "overground".equalsIgnoreCase(modeId) || "tram".equalsIgnoreCase(modeId)
                        || "elizabeth-line".equalsIgnoreCase(modeId) || "cable-car".equalsIgnoreCase(modeId)) {
                    transportMode = "TUBE";
                    operatorName = formatOperatorSlug(specificLineId) + " Line";
                } else {
                    transportMode = "TRAIN";
                    String tocCode = TOCBrandHelper.getTocCodeForTflId(specificLineId);
                    Toc toc = TOCBrandHelper.getTocByName(tocCode);
                    if (toc == null) toc = TOCBrandHelper.getTocByName(specificLineId);
                    operatorName = (toc != null) ? toc.name : formatOperatorSlug(specificLineId);
                }

                fromName = fromName.replace(" Underground Station", "").replace(" Rail Station", "");
                toName = toName.replace(" Underground Station", "").replace(" Rail Station", "");

                List<JourneyLeg.Stop> intermediateStops = new ArrayList<>();
                if (legObj.has("path")) {
                    JsonObject pathObj = legObj.getAsJsonObject("path");
                    if (pathObj.has("stopPoints")) {
                        JsonArray stopPointsArray = pathObj.getAsJsonArray("stopPoints");

                        for (JsonElement stopElem : stopPointsArray) {
                            JsonObject stopPointObj = stopElem.getAsJsonObject();
                            String stopName = stopPointObj.has("name") ? stopPointObj.get("name").getAsString() : "Unknown";
                            stopName = stopName.replace(" Underground Station", "").replace(" Rail Station", "");

                            if (!stopName.equalsIgnoreCase(fromName) && !stopName.equalsIgnoreCase(toName)) {
                                intermediateStops.add(new JourneyLeg.Stop(stopName, "--:--"));
                            }
                        }
                    }
                }

                JourneyLeg leg = new JourneyLeg(
                        depPoint.has("naptanId") ? depPoint.get("naptanId").getAsString() : "", fromName,
                        arrPoint.has("naptanId") ? arrPoint.get("naptanId").getAsString() : "", toName,
                        dest, depTime, arrTime, operatorName, null, transportMode, intermediateStops, null, depTime
                );
                parsedLegs.add(leg);
            }
            if (!parsedLegs.isEmpty()) {
                JourneyOption option = new JourneyOption(parsedLegs);
                option.departureDate = journeyStartDate;
                options.add(option);
            }
        }
        return options;
    }

    private String formatOperatorSlug(String slug) {
        if (slug == null || slug.isEmpty()) return "National Rail";
        String[] words = slug.replace("-", " ").split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.length() > 0) {
                sb.append(Character.toUpperCase(w.charAt(0)))
                        .append(w.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String extractTime(String isoDate) {
        if (isoDate == null || isoDate.length() < 16) return "--:--";
        try {
            return isoDate.substring(11, 16);
        } catch (Exception e) {
            return "--:--";
        }
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}