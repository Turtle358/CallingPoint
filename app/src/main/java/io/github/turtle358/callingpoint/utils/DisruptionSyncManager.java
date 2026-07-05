package io.github.turtle358.callingpoint.utils;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.NationalRailAPI;
import io.github.turtle358.callingpoint.models.DisruptionResponce;
import io.github.turtle358.callingpoint.models.IncidentResponse;
import io.github.turtle358.callingpoint.models.IncidentSearchRequest;
import io.github.turtle358.callingpoint.widget.AppWidgetProvider;
import retrofit2.Response;

public class DisruptionSyncManager {
    public static class CachedDisruptionData {
        public List<DisruptionResponce.ServiceIndicator> tracked;
        public List<DisruptionResponce.ServiceIndicator> others;
        public List<DisruptionResponce.ServiceIndicator> engineering;

        public CachedDisruptionData(List<DisruptionResponce.ServiceIndicator> tracked,
                                    List<DisruptionResponce.ServiceIndicator> others,
                                    List<DisruptionResponce.ServiceIndicator> engineering) {
            this.tracked = tracked;
            this.others = others;
            this.engineering = engineering;
        }
    }

    public interface SyncCallback {
        void onSuccess(List<DisruptionResponce.ServiceIndicator> tracked,
                       List<DisruptionResponce.ServiceIndicator> others,
                       List<DisruptionResponce.ServiceIndicator> engineering);
        void onFailure(String error);
    }

    public static void syncAsync(Context context, SyncCallback callback) {
        new Thread(() -> {
            boolean success = syncSync(context, callback);
            if (!success && callback != null) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onFailure("Sync failed"));
            }
        }).start();
    }

    public static boolean syncSync(Context context, SyncCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);

        TOCBrandHelper.init(context);

        String selectedTocs = prefs.getString("WIDGET_TRACKED_TOCS", "");
        Set<String> trackedSet = new HashSet<>();
        if (!selectedTocs.trim().isEmpty()) {
            for (String s : selectedTocs.split(",")) {
                if (!s.trim().isEmpty()) trackedSet.add(s.trim());
            }
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        cal.add(Calendar.DAY_OF_YEAR, 1);
        String today = sdf.format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, 2);
        String tomorrow = sdf.format(cal.getTime());
        IncidentSearchRequest request = new IncidentSearchRequest(today, tomorrow, new ArrayList<>());
        NationalRailAPI api = APIClient.getClient().create(NationalRailAPI.class);

        try {
            Response<List<IncidentResponse>> response = api.searchIncidents(request).execute();

            if (response.isSuccessful() && response.body() != null) {
                Map<String, DisruptionResponce.ServiceIndicator> trackedMap = new LinkedHashMap<>();
                Map<String, DisruptionResponce.ServiceIndicator> othersMap = new LinkedHashMap<>();

                for (IncidentResponse incident : response.body()) {
                    if (incident.affectedOperators == null || "Cleared".equalsIgnoreCase(incident.status)) continue;

                    for (IncidentResponse.AffectedOperator op : incident.affectedOperators) {
                        String currentSummary = incident.summary != null ? incident.summary.toLowerCase() : "";
                        String currentDesc = incident.description != null ? incident.description.toLowerCase() : "";
                        String simplifiedStatus = "Minor Disruption";

                        boolean isEngineering = (currentSummary.contains("engineering work") || currentDesc.contains("engineering work")) && !(currentDesc.contains("overrun") || currentSummary.contains("overrunning"));

                        boolean isStrike = currentSummary.contains("industrial action") ||
                                currentDesc.contains("industrial action");

                        boolean hasSevereDelay = false;
                        java.util.regex.Pattern delayPattern = java.util.regex.Pattern.compile("(\\d+)\\s*(?:min|minute)");
                        java.util.regex.Matcher matcher = delayPattern.matcher(currentSummary + " " + currentDesc);
                        while (matcher.find()) {
                            try {
                                int minutes = Integer.parseInt(matcher.group(1));
                                if (minutes >= 45) {
                                    hasSevereDelay = true;
                                    break;
                                }
                            } catch (NumberFormatException ignored) {}
                        }

                        if (isStrike) {
                            simplifiedStatus = "Planned Industrial Action";
                        } else if (isEngineering) {
                            simplifiedStatus = "Planned Engineering Works";
                        } else if (hasSevereDelay ||
                                currentSummary.contains("major") ||
                                currentSummary.contains("severe") ||
                                currentSummary.contains("suspended") ||
                                currentSummary.contains("do not travel")) {
                            simplifiedStatus = "Major Disruption";
                        }
                        Map<String, DisruptionResponce.ServiceIndicator> targetMap;
                        if (trackedSet.isEmpty() || trackedSet.contains(op.tocCode)) {
                            targetMap = trackedMap;
                        } else {
                            targetMap = othersMap;
                        }

                        if (!targetMap.containsKey(op.tocCode)) {
                            DisruptionResponce.ServiceIndicator si = new DisruptionResponce.ServiceIndicator();
                            si.tocCode = op.tocCode;
                            si.tocName = TOCBrandHelper.getNameForCode(op.tocCode);
                            si.status = simplifiedStatus;

                            if (simplifiedStatus.equals("Planned Engineering Works")) {
                                si.plannedIncidents.add(incident);
                            } else {
                                si.activeIncidents.add(incident);
                            }
                            targetMap.put(op.tocCode, si);
                        } else {
                            DisruptionResponce.ServiceIndicator existing = targetMap.get(op.tocCode);

                            if (simplifiedStatus.equals("Major Disruption")) {
                                existing.status = "Major Disruption";
                            } else if (simplifiedStatus.equals("Minor Disruption") && existing.status.equals("Planned Engineering Works")) {
                                existing.status = "Minor Disruption";
                            }

                            if (simplifiedStatus.equals("Planned Engineering Works")) {
                                if (!existing.plannedIncidents.contains(incident)) {
                                    existing.plannedIncidents.add(incident);
                                }
                            } else {
                                if (!existing.activeIncidents.contains(incident)) {
                                    existing.activeIncidents.add(incident);
                                }
                            }
                        }
                    }
                }

                List<DisruptionResponce.ServiceIndicator> trackedLive = new ArrayList<>();
                List<DisruptionResponce.ServiceIndicator> othersLive = new ArrayList<>();
                List<DisruptionResponce.ServiceIndicator> plannedWorks = new ArrayList<>();

                for (DisruptionResponce.ServiceIndicator si : trackedMap.values()) {
                    if (si.status.equals("Planned Engineering Works")) plannedWorks.add(si);
                    else trackedLive.add(si);
                }
                for (DisruptionResponce.ServiceIndicator si : othersMap.values()) {
                    if (si.status.equals("Planned Engineering Works")) plannedWorks.add(si);
                    else othersLive.add(si);
                }

                trackedLive.sort((a, b) -> a.tocName.compareToIgnoreCase(b.tocName));
                othersLive.sort((a, b) -> a.tocName.compareToIgnoreCase(b.tocName));
                plannedWorks.sort((a, b) -> a.tocName.compareToIgnoreCase(b.tocName));
                CachedDisruptionData cacheContainer = new CachedDisruptionData(trackedLive, othersLive, plannedWorks);
                String appJson = new Gson().toJson(cacheContainer);
                String widgetJson = new Gson().toJson(trackedLive);

                prefs.edit()
                        .putString("CACHED_TOC_DATA", widgetJson)
                        .putString("CACHED_TOC_DATA_ALL", appJson)
                        .putLong("LAST_SYNC_TIME", System.currentTimeMillis())
                        .apply();

                triggerWidgetUpdate(context);

                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onSuccess(trackedLive, othersLive, plannedWorks)
                    );
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static void triggerWidgetUpdate(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, AppWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

        Intent updateIntent = new Intent(context, AppWidgetProvider.class);
        updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        context.sendBroadcast(updateIntent);
    }
}