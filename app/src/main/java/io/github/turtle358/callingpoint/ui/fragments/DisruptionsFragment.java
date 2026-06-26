package io.github.turtle358.callingpoint.ui.fragments;

import android.content.Context;
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
import java.util.List;
import java.util.Locale;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.NationalRailAPI;
import io.github.turtle358.callingpoint.models.DisruptionResponce;
import io.github.turtle358.callingpoint.ui.adapters.DisruptionAdapter;

public class DisruptionsFragment extends Fragment {
    private TextView liveIndicator, goodServiceSubtext;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View cardTracked, cardOthers, cardEngineering, cardGoodService;
    private DisruptionAdapter adapterTracked, adapterOthers, adapterEngineering;
    private NationalRailAPI api;

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

        api = APIClient.getClient().create(NationalRailAPI.class);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            swipeRefreshLayout.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            fetchRailData(api);
        });
        RecyclerView recyclerTracked = view.findViewById(R.id.recyclerTracked);
        recyclerTracked.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapterTracked = new DisruptionAdapter(requireContext(), new ArrayList<>());
        recyclerTracked.setAdapter(adapterTracked);

        setupCollapsibleSection(
                view.findViewById(R.id.headerTracked),
                view.findViewById(R.id.iconExpandTracked),
                recyclerTracked,
                cardTracked,
                "TRACKED_EXPANDED"
        );

        RecyclerView recyclerOthers = view.findViewById(R.id.recyclerOthers);
        recyclerOthers.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapterOthers = new DisruptionAdapter(requireContext(), new ArrayList<>());
        recyclerOthers.setAdapter(adapterOthers);

        setupCollapsibleSection(
                view.findViewById(R.id.headerOthers),
                view.findViewById(R.id.iconExpandOthers),
                recyclerOthers,
                cardOthers,
                "OTHERS_EXPANDED"
        );
        RecyclerView recyclerEngineering = view.findViewById(R.id.recyclerEngineering);
        recyclerEngineering.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapterEngineering = new DisruptionAdapter(requireContext(), new ArrayList<>());
        recyclerEngineering.setAdapter(adapterEngineering);

        setupCollapsibleSection(
                view.findViewById(R.id.headerEngineering),
                view.findViewById(R.id.iconExpandEngineering),
                recyclerEngineering,
                cardEngineering,
                "ENGINEERING_EXPANDED"
        );
    }

    private void setupCollapsibleSection(View header, android.widget.ImageView icon, RecyclerView recycler, View cardView, String prefKey) {

        SharedPreferences prefs = requireContext().getSharedPreferences("DisruptionPrefs", Context.MODE_PRIVATE);
        boolean isExpanded = prefs.getBoolean(prefKey, true);

        recycler.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        icon.setRotation(isExpanded ? 180f : 0f);

        header.setOnClickListener(v -> {
            boolean currentlyExpanded = recycler.getVisibility() == View.VISIBLE;

            android.transition.TransitionManager.beginDelayedTransition((android.view.ViewGroup) cardView.getParent());

            if (currentlyExpanded) {
                recycler.setVisibility(View.GONE);
                icon.animate().rotation(0f).setDuration(200).start();
                prefs.edit().putBoolean(prefKey, false).apply();
            } else {
                recycler.setVisibility(View.VISIBLE);
                icon.animate().rotation(180f).setDuration(200).start();
                prefs.edit().putBoolean(prefKey, true).apply();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        checkCacheAndLoad();
    }

    private void checkCacheAndLoad() {
        SharedPreferences prefs = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);

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
        fetchRailData(api);
    }

    private void fetchRailData(NationalRailAPI api) {
        swipeRefreshLayout.setRefreshing(true);
        liveIndicator.setText("◉ Syncing Real-Time Delays...");

        io.github.turtle358.callingpoint.utils.DisruptionSyncManager.syncAsync(requireContext(),
                new io.github.turtle358.callingpoint.utils.DisruptionSyncManager.SyncCallback() {

                    @Override
                    public void onSuccess(List<DisruptionResponce.ServiceIndicator> tracked,
                                          List<DisruptionResponce.ServiceIndicator> others,
                                          List<DisruptionResponce.ServiceIndicator> engineering) {
                        if (!isAdded()) return;
                        swipeRefreshLayout.setRefreshing(false);
                        updateUI(tracked, others, engineering);
                        liveIndicator.setText("◉ Live");
                    }
                    @Override
                    public void onFailure(String error) {
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
}