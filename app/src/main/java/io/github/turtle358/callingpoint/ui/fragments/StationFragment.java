package io.github.turtle358.callingpoint.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RadioGroup;
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
    private boolean isArrivals = false;

    private TextView tvStationTitle, tvStationSubtitle, tvEmptyState;
    private ImageButton btnPinStation;
    private RadioGroup toggleBoardType;
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
            isArrivals = getArguments().getBoolean("IS_ARRIVALS", false);
        }

        pinKey = fromCrs + (toCrs.isEmpty() ? "" : ":" + toCrs);

        tvStationTitle = view.findViewById(R.id.tvStationTitle);
        tvStationSubtitle = view.findViewById(R.id.tvStationSubtitle);
        btnPinStation = view.findViewById(R.id.btnPinStation);
        toggleBoardType = view.findViewById(R.id.toggleBoardType);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshStation);
        recyclerDepartures = view.findViewById(R.id.recyclerDepartures);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);

        pinnedDataManager = new PinnedDataManager(requireContext());
        api = APIClient.getClient().create(NationalRailAPI.class);

        setupHeader();
        setupPinButton();

        toggleBoardType.check(isArrivals ? R.id.radioArrivals : R.id.radioDepartures);
        toggleBoardType.setOnCheckedChangeListener((group, checkedId) -> {
            isArrivals = (checkedId == R.id.radioArrivals);
            fetchTrains();
        });

        recyclerDepartures.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DeparturesAdapter(requireContext(), new ArrayList<>());
        recyclerDepartures.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::fetchTrains);
        fetchTrains();
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
        btnPinStation.setImageResource(isPinned ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
    }

    private void fetchTrains() {
        String key;
        if (isArrivals) {
            key = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE).getString("ARRIVALS_KEY", "");
        } else {
            key = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE).getString("LDBWS_KEY", "");
        }

        if (key.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter your API Key in Settings", Toast.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        swipeRefreshLayout.setRefreshing(true);
        tvEmptyState.setVisibility(View.GONE);

        String apiFilterCrs = toCrs.isEmpty() ? null : toCrs;

        Call<StationDepartureResponse> networkCall;
        if (isArrivals) {
            networkCall = api.getStationArrivals(key, fromCrs, 150, 120, apiFilterCrs);
        } else {
            networkCall = api.getStationDepartures(key, fromCrs, 150, 120, apiFilterCrs);
        }

        networkCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<StationDepartureResponse> call, @NonNull Response<StationDepartureResponse> response) {
                if (!isAdded()) return;
                swipeRefreshLayout.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    List<StationDepartureResponse.TrainService> allServices = response.body().trainServices;

                    if (allServices != null && !allServices.isEmpty()) {
                        adapter.updateData(allServices, isArrivals, fromCrs, toCrs);
                        recyclerDepartures.setVisibility(View.VISIBLE);
                    } else {
                        showEmptyState(isArrivals ? "No direct arrivals found right now." : "No direct trains found right now.");
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
        adapter.updateData(new ArrayList<>(), isArrivals, fromCrs, toCrs);
        recyclerDepartures.setVisibility(View.GONE);
        tvEmptyState.setText(message);
        tvEmptyState.setVisibility(View.VISIBLE);
    }
}