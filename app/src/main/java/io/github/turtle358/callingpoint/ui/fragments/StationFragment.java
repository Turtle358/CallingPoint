package io.github.turtle358.callingpoint.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

import io.github.turtle358.callingpoint.ui.adapters.DeparturesAdapter;
import io.github.turtle358.callingpoint.data.PinnedDataManager;
import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.utils.StationHelper;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.NationalRailAPI;
import io.github.turtle358.callingpoint.models.StationDepartureResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StationFragment extends Fragment {

    private String fromCrs = "";
    private String toCrs = "";
    private String pinKey = "";

    private TextView tvStationTitle, tvStationSubtitle, tvEmptyState;
    private ImageButton btnPinStation;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerDepartures;

    private DeparturesAdapter adapter;
    private NationalRailAPI api;
    private PinnedDataManager pinnedDataManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_station, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            fromCrs = getArguments().getString("FROM_CRS", "");
            toCrs = getArguments().getString("TO_CRS", "");
        }

        pinKey = fromCrs + (toCrs.isEmpty() ? "" : ":" + toCrs);

        tvStationTitle = view.findViewById(R.id.tvStationTitle);
        tvStationSubtitle = view.findViewById(R.id.tvStationSubtitle);
        btnPinStation = view.findViewById(R.id.btnPinStation);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshStation);
        recyclerDepartures = view.findViewById(R.id.recyclerDepartures);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);

        pinnedDataManager = new PinnedDataManager(requireContext());
        api = APIClient.getClient().create(NationalRailAPI.class);

        setupHeader();
        setupPinButton();

        recyclerDepartures.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DeparturesAdapter(requireContext(), new ArrayList<>());
        recyclerDepartures.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::fetchDepartures);
        fetchDepartures();
    }

    private void setupHeader() {
        tvStationTitle.setText(fromCrs);
        for (String name : StationHelper.getStationNames()) {
            if (fromCrs.equalsIgnoreCase(StationHelper.getCrsFromName(name))) {
                tvStationTitle.setText(name);
                break;
            }
        }

        if (!toCrs.isEmpty()) {
            String toName = toCrs;
            for (String name : StationHelper.getStationNames()) {
                if (toCrs.equalsIgnoreCase(StationHelper.getCrsFromName(name))) {
                    toName = name;
                    break;
                }
            }
            tvStationSubtitle.setText("to " + toName);
            tvStationSubtitle.setVisibility(View.VISIBLE);
        }
    }

    private void setupPinButton() {
        updatePinIcon();

        btnPinStation.setOnClickListener(v -> {
            pinnedDataManager.toggleStationPin(pinKey);
            updatePinIcon();

            boolean isNowPinned = pinnedDataManager.isStationPinned(pinKey);
            Toast.makeText(requireContext(), isNowPinned ? "Station Pinned!" : "Pin Removed", Toast.LENGTH_SHORT).show();
        });
    }

    private void updatePinIcon() {
        boolean isPinned = pinnedDataManager.isStationPinned(pinKey);
        // Swap between a filled star and an outlined star
        btnPinStation.setImageResource(isPinned ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
    }

    private void fetchDepartures() {
        String key = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE).getString("LDBWS_KEY", "");

        if (key.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter your API Key in Settings", Toast.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        swipeRefreshLayout.setRefreshing(true);
        tvEmptyState.setVisibility(View.GONE);

        String apiFilterCrs = toCrs.isEmpty() ? null : toCrs;

        api.getStationDepartures(key, fromCrs, 150, 120, apiFilterCrs).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<StationDepartureResponse> call, @NonNull Response<StationDepartureResponse> response) {
                if (!isAdded()) return;
                swipeRefreshLayout.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    List<StationDepartureResponse.TrainService> allServices = response.body().trainServices;

                    if (allServices != null && !allServices.isEmpty()) {
                        adapter.updateData(allServices);
                        recyclerDepartures.setVisibility(View.VISIBLE);
                    } else {
                        showEmptyState("No direct trains found right now.");
                    }
                } else {
                    showEmptyState("API Error: Please check your CRS codes.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<StationDepartureResponse> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                swipeRefreshLayout.setRefreshing(false);
                showEmptyState("Network error. Please try again.");
            }
        });
    }

    private void showEmptyState(String message) {
        adapter.updateData(new ArrayList<>());
        recyclerDepartures.setVisibility(View.GONE);
        tvEmptyState.setText(message);
        tvEmptyState.setVisibility(View.VISIBLE);
    }
}