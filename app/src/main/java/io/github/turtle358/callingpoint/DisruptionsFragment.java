package io.github.turtle358.callingpoint;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DisruptionsFragment extends Fragment {
    private TextView liveIndicator, goodServiceSubtext;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View cardTracked, cardOthers, cardEngineering, cardGoodService;
    private DisruptionAdapter adapterTracked, adapterOthers, adapterEngineering;
    private NationalRailAPI api;
    private String customerKey;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_disruptions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        liveIndicator = view.findViewById(R.id.liveIndicator);
        goodServiceSubtext = view.findViewById(R.id.goodServiceSubtext);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        cardTracked = view.findViewById(R.id.cardTracked);
        cardOthers = view.findViewById(R.id.cardOthers);
        cardEngineering = view.findViewById(R.id.cardEngineering);
        cardGoodService = view.findViewById(R.id.cardGoodService);

        RecyclerView recyclerTracked = view.findViewById(R.id.recyclerTracked);
        recyclerTracked.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapterTracked = new DisruptionAdapter(requireContext(), new ArrayList<>());
        recyclerTracked.setAdapter(adapterTracked);

        RecyclerView recyclerOthers = view.findViewById(R.id.recyclerOthers);
        recyclerOthers.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapterOthers = new DisruptionAdapter(requireContext(), new ArrayList<>());
        recyclerOthers.setAdapter(adapterOthers);

        RecyclerView recyclerEngineering = view.findViewById(R.id.recyclerEngineering);
        recyclerEngineering.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapterEngineering = new DisruptionAdapter(requireContext(), new ArrayList<>());
        recyclerEngineering.setAdapter(adapterEngineering);

        api = APIClient.getClient().create(NationalRailAPI.class);
        customerKey = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE).getString("CUSTOMER_KEY", "");

        swipeRefreshLayout.setOnRefreshListener(() -> fetchRailData(api, customerKey));
    }

    @Override
    public void onResume() {
        super.onResume();
        checkCacheAndLoad();
    }

    private void checkCacheAndLoad() {
        SharedPreferences prefs = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        String key = prefs.getString("CUSTOMER_KEY", "");

        if (key.isEmpty()) {
            liveIndicator.setText("◉ Offline - No API Key");
            return;
        }

        long lastSync = prefs.getLong("LAST_SYNC_TIME", 0);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastSync < 900000 && lastSync != 0) {
            String cachedJson = prefs.getString("CACHED_TOC_DATA_ALL", null);
            if (cachedJson != null) {
                try {
                    java.lang.reflect.Type type = new TypeToken<ArrayList<DisruptionResponce.ServiceIndicator>>(){}.getType();
                    List<DisruptionResponce.ServiceIndicator> cachedData = new Gson().fromJson(cachedJson, type);
                    List<DisruptionResponce.ServiceIndicator> t = new ArrayList<>();
                    List<DisruptionResponce.ServiceIndicator> o = new ArrayList<>();
                    List<DisruptionResponce.ServiceIndicator> e = new ArrayList<>();
                    int currentList = 0;

                    for (DisruptionResponce.ServiceIndicator si : cachedData) {
                        if ("SEPARATOR".equals(si.tocCode)) {
                            if ("OTHER OPERATORS".equals(si.tocName)) currentList = 1;
                            else if ("ENGINEERING WORKS".equals(si.tocName)) currentList = 2;
                            continue;
                        }
                        if (currentList == 0) t.add(si);
                        else if (currentList == 1) o.add(si);
                        else if (currentList == 2) e.add(si);
                    }

                    updateUI(t, o, e);
                    SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    String syncTime = timeFormat.format(new Date(lastSync));
                    liveIndicator.setText("◉ Cached (" + syncTime + ")");
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        fetchRailData(api, key);
    }

    private void fetchRailData(NationalRailAPI api, String key) {
        if (key == null || key.isEmpty()) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        swipeRefreshLayout.setRefreshing(true);
        liveIndicator.setText("◉ Syncing Real-Time Delays...");

        SharedPreferences prefs = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        String selectedTocs = prefs.getString("WIDGET_TRACKED_TOCS", "");
        Set<String> trackedSet = new HashSet<>();
        if (!selectedTocs.trim().isEmpty()) {
            for (String s : selectedTocs.split(",")) {
                if (!s.trim().isEmpty()) trackedSet.add(s.trim());
            }
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = sdf.format(new Date());
        IncidentSearchRequest request = new IncidentSearchRequest(today, today, new ArrayList<>());

        api.searchIncidents(key, request).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<IncidentResponse>> call, @NonNull Response<List<IncidentResponse>> response) {
                if (!isAdded()) return;
                swipeRefreshLayout.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    Map<String, DisruptionResponce.ServiceIndicator> trackedMap = new LinkedHashMap<>();
                    Map<String, DisruptionResponce.ServiceIndicator> othersMap = new LinkedHashMap<>();

                    for (IncidentResponse incident : response.body()) {
                        if (incident.affectedOperators == null || "Cleared".equalsIgnoreCase(incident.status))
                            continue;

                        for (IncidentResponse.AffectedOperator op : incident.affectedOperators) {
                            String currentSummary = incident.summary != null ? incident.summary.toLowerCase() : "";
                            String currentDesc = incident.description != null ? incident.description.toLowerCase() : "";
                            String simplifiedStatus = "Minor Disruption";
                            boolean isEngineering = (currentSummary.contains("engineering") || currentDesc.contains("engineering"))
                                    && !currentSummary.contains("overrun") && !currentDesc.contains("overrun");

                            if (currentSummary.contains("strike") || currentDesc.contains("strike") || currentSummary.contains("industrial action") || currentDesc.contains("industrial action")) {
                                simplifiedStatus = "Planned Industrial Action";
                            } else if (isEngineering) {
                                simplifiedStatus = "Planned Engineering Works";
                            } else if (currentSummary.contains("major") || currentSummary.contains("suspended") || currentSummary.contains("closed") || currentSummary.contains("blocked")) {
                                simplifiedStatus = "Major Disruption";
                            } else if (currentSummary.contains("bus") || currentSummary.contains("amended")) {
                                simplifiedStatus = "Planned Engineering Works";
                            }

                            Map<String, DisruptionResponce.ServiceIndicator> targetMap =
                                    trackedSet.contains(op.tocCode) ? trackedMap : othersMap;

                            if (!targetMap.containsKey(op.tocCode)) {
                                DisruptionResponce.ServiceIndicator si = new DisruptionResponce.ServiceIndicator();
                                si.tocCode = op.tocCode;
                                si.tocName = TOCBrandHelper.getNameForCode(op.tocCode);
                                si.status = simplifiedStatus;
                                if (simplifiedStatus.equals("Planned Engineering Works")) {
                                    si.plannedDescription = incident.description;
                                } else {
                                    si.description = incident.description;
                                }
                                targetMap.put(op.tocCode, si);
                            } else {
                                DisruptionResponce.ServiceIndicator existing = targetMap.get(op.tocCode);
                                if (simplifiedStatus.equals("Major Disruption")) {
                                    assert existing != null;
                                    existing.status = "Major Disruption";
                                }
                                else {
                                    if (simplifiedStatus.equals("Minor Disruption")) {
                                        assert existing != null;
                                        if (existing.status.equals("Planned Engineering Works"))
                                            existing.status = "Minor Disruption";
                                    }
                                }

                                if (simplifiedStatus.equals("Planned Engineering Works")) {
                                    assert existing != null;
                                    existing.plannedDescription = existing.plannedDescription.isEmpty() ? incident.description : existing.plannedDescription + "<br><br><hr><br>" + incident.description;
                                } else {
                                    assert existing != null;
                                    existing.description = existing.description.isEmpty() ? incident.description : existing.description + "<br><br><hr><br>" + incident.description;
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

                    updateUI(trackedLive, othersLive, plannedWorks);
                    liveIndicator.setText("◉ Live");

                    List<DisruptionResponce.ServiceIndicator> combinedAppCacheData = new ArrayList<>(trackedLive);
                    if (!trackedLive.isEmpty() && !othersLive.isEmpty()) {
                        DisruptionResponce.ServiceIndicator sep = new DisruptionResponce.ServiceIndicator();
                        sep.tocCode = "SEPARATOR";
                        sep.tocName = "OTHER OPERATORS";
                        combinedAppCacheData.add(sep);
                    }
                    combinedAppCacheData.addAll(othersLive);
                    if (!plannedWorks.isEmpty()) {
                        DisruptionResponce.ServiceIndicator sep = new DisruptionResponce.ServiceIndicator();
                        sep.tocCode = "SEPARATOR";
                        sep.tocName = "ENGINEERING WORKS";
                        combinedAppCacheData.add(sep);
                        combinedAppCacheData.addAll(plannedWorks);
                    }

                    String widgetJson = new Gson().toJson(new ArrayList<>(trackedLive));
                    String appJson = new Gson().toJson(combinedAppCacheData);

                    prefs.edit()
                            .putString("CACHED_TOC_DATA", widgetJson)
                            .putString("CACHED_TOC_DATA_ALL", appJson)
                            .putLong("LAST_SYNC_TIME", System.currentTimeMillis())
                            .apply();
                    nudgeWidget();

                } else {
                    liveIndicator.setText("◉ API Error: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<IncidentResponse>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                swipeRefreshLayout.setRefreshing(false);
                liveIndicator.setText("◉ Data Sync Failed");
            }
        });
    }

    private void updateUI(List<DisruptionResponce.ServiceIndicator> tracked, List<DisruptionResponce.ServiceIndicator> others, List<DisruptionResponce.ServiceIndicator> engineering) {
        adapterTracked.updateData(tracked);
        adapterOthers.updateData(others);
        adapterEngineering.updateData(engineering);
        cardTracked.setVisibility(tracked.isEmpty() ? View.GONE : View.VISIBLE);
        cardOthers.setVisibility(others.isEmpty() ? View.GONE : View.VISIBLE);
        cardEngineering.setVisibility(engineering.isEmpty() ? View.GONE : View.VISIBLE);
        goodServiceSubtext.setText(tracked.isEmpty() ? "On all lines" : "All other lines");
        cardGoodService.setVisibility(View.VISIBLE);
    }

    private void nudgeWidget() {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(requireContext());
        ComponentName thisWidget = new ComponentName(requireContext(), AppWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
        Intent intent = new Intent(requireContext(), AppWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        requireContext().sendBroadcast(intent);
    }
}