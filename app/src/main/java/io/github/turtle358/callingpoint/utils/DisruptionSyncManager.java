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
import io.github.turtle358.callingpoint.api.TflAPI;
import io.github.turtle358.callingpoint.models.DisruptionResponce;
import io.github.turtle358.callingpoint.models.IncidentResponse;
import io.github.turtle358.callingpoint.models.IncidentSearchRequest;
import io.github.turtle358.callingpoint.models.TflLineStatusResponse;
import io.github.turtle358.callingpoint.widget.AppWidgetProvider;
import io.github.turtle358.callingpoint.utils.DisruptionSeverityManager.Severity;
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

                        if ("LO".equalsIgnoreCase(op.tocCode) || "XR".equalsIgnoreCase(op.tocCode)) {
                            continue;
                        }

                        Severity severity = DisruptionSeverityManager.classify(
                                incident.summary,
                                incident.description
                        );

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
                            si.status = severity.getStatusString();

                            if (severity == Severity.ENGINEERING_WORKS) {
                                si.plannedIncidents.add(incident);
                            } else {
                                si.activeIncidents.add(incident);
                            }
                            targetMap.put(op.tocCode, si);
                        } else {
                            DisruptionResponce.ServiceIndicator existing = targetMap.get(op.tocCode);
                            Severity existingSeverity = Severity.fromString(existing.status);

                            if (severity.getPriority() > existingSeverity.getPriority()) {
                                existing.status = severity.getStatusString();
                            }

                            if (severity == Severity.ENGINEERING_WORKS) {
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

                TflAPI tflApi = APIClient.getClient().create(TflAPI.class);
                try {
                    Response<List<TflLineStatusResponse>> tflResponse = tflApi.getLineStatus("tube,overground,dlr,elizabeth-line,tram").execute();

                    if (tflResponse.isSuccessful() && tflResponse.body() != null) {
                        for (TflLineStatusResponse line : tflResponse.body()) {
                            if (line.lineStatuses == null || line.lineStatuses.isEmpty()) continue;

                            TflLineStatusResponse.LineStatus status = line.lineStatuses.get(0);

                            if (status.statusSeverity == 10) continue;

                            String opCode = TOCBrandHelper.getTocCodeForTflId(line.id);
                            String opName = TOCBrandHelper.getNameForCode(opCode);

                            Map<String, DisruptionResponce.ServiceIndicator> targetMap =
                                    (trackedSet.isEmpty() || trackedSet.contains(opCode)) ? trackedMap : othersMap;

                            DisruptionResponce.ServiceIndicator si = targetMap.get(opCode);
                            if (si == null) {
                                si = new DisruptionResponce.ServiceIndicator();
                                si.tocCode = opCode;
                                si.tocName = opName.isEmpty() ? line.name : opName;
                                si.status = status.statusSeverityDescription;
                                targetMap.put(opCode, si);
                            } else {
                                si.status = status.statusSeverityDescription;
                            }

                            IncidentResponse incident = new IncidentResponse();
                            incident.summary = status.statusSeverityDescription;
                            incident.description = status.reason != null ? status.reason : "No further details available.";
                            incident.status = "Active";
                            incident.affectedOperators = new ArrayList<>();

                            IncidentResponse.AffectedOperator op = new IncidentResponse.AffectedOperator();
                            op.tocCode = opCode;
                            incident.affectedOperators.add(op);

                            boolean isPlanned = DisruptionSeverityManager.isTflPlanned(status.statusSeverity);

                            if (isPlanned) {
                                si.plannedIncidents.add(incident);
                            } else {
                                si.activeIncidents.add(incident);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                List<DisruptionResponce.ServiceIndicator> trackedLive = new ArrayList<>();
                List<DisruptionResponce.ServiceIndicator> othersLive = new ArrayList<>();
                List<DisruptionResponce.ServiceIndicator> plannedWorks = new ArrayList<>();

                for (DisruptionResponce.ServiceIndicator si : trackedMap.values()) {
                    if (si.activeIncidents.isEmpty() && !si.plannedIncidents.isEmpty()) {
                        plannedWorks.add(si);
                    } else {
                        trackedLive.add(si);
                    }
                }

                for (DisruptionResponce.ServiceIndicator si : othersMap.values()) {
                    if (si.activeIncidents.isEmpty() && !si.plannedIncidents.isEmpty()) {
                        plannedWorks.add(si);
                    } else {
                        othersLive.add(si);
                    }
                }

                trackedLive = groupAndFormatList(trackedLive);
                othersLive = groupAndFormatList(othersLive);
                plannedWorks = groupAndFormatList(plannedWorks);

                CachedDisruptionData cacheContainer = new CachedDisruptionData(trackedLive, othersLive, plannedWorks);
                String appJson = new Gson().toJson(cacheContainer);

                List<DisruptionResponce.ServiceIndicator> widgetTracked = new ArrayList<>();
                for (DisruptionResponce.ServiceIndicator si : trackedLive) {
                    if (!"SEPARATOR".equals(si.tocCode)) {
                        widgetTracked.add(si);
                    }
                }
                String widgetJson = new Gson().toJson(widgetTracked);

                prefs.edit()
                        .putString("CACHED_TOC_DATA", widgetJson)
                        .putString("CACHED_TOC_DATA_ALL", appJson)
                        .putLong("LAST_SYNC_TIME", System.currentTimeMillis())
                        .apply();

                triggerWidgetUpdate(context);

                if (callback != null) {
                    List<DisruptionResponce.ServiceIndicator> finalTrackedLive = trackedLive;
                    List<DisruptionResponce.ServiceIndicator> finalOthersLive = othersLive;
                    List<DisruptionResponce.ServiceIndicator> finalPlannedWorks = plannedWorks;
                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onSuccess(finalTrackedLive, finalOthersLive, finalPlannedWorks)
                    );
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean isTflService(DisruptionResponce.ServiceIndicator si) {
        if (si == null || si.tocName == null) return false;
        String manager = TOCBrandHelper.getManagerForToc(si.tocName);
        return "tfl".equalsIgnoreCase(manager) || "tfl-national".equalsIgnoreCase(manager) || "london buses".equalsIgnoreCase(manager);
    }

    private static List<DisruptionResponce.ServiceIndicator> groupAndFormatList(List<DisruptionResponce.ServiceIndicator> inputList) {
        if (inputList == null || inputList.isEmpty()) return new ArrayList<>();

        List<DisruptionResponce.ServiceIndicator> nationalRail = new ArrayList<>();
        List<DisruptionResponce.ServiceIndicator> tflServices = new ArrayList<>();

        for (DisruptionResponce.ServiceIndicator si : inputList) {
            if ("SEPARATOR".equals(si.tocCode)) continue;
            if (isTflService(si)) {
                tflServices.add(si);
            } else {
                nationalRail.add(si);
            }
        }

        nationalRail.sort((a, b) -> a.tocName.compareToIgnoreCase(b.tocName));
        tflServices.sort((a, b) -> a.tocName.compareToIgnoreCase(b.tocName));

        List<DisruptionResponce.ServiceIndicator> result = new ArrayList<>();
        result.addAll(nationalRail);

        if (!nationalRail.isEmpty() && !tflServices.isEmpty()) {
            DisruptionResponce.ServiceIndicator sep = new DisruptionResponce.ServiceIndicator();
            sep.tocCode = "SEPARATOR";
            sep.tocName = "Transport for London";
            result.add(sep);
        }

        result.addAll(tflServices);
        return result;
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