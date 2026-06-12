package io.github.turtle358.callingpoint.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
    private boolean isArrivals = false;
    private int currentOffset = 0;

    private TextView tvDirectionHeading, tvStationTitle, tvStationSubtitle, tvEmptyState;
    private ImageButton btnPinStation;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerDepartures;
    private Button btnLaterServices;

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

        tvDirectionHeading = view.findViewById(R.id.tvDirectionHeading);
        tvStationTitle = view.findViewById(R.id.tvStationTitle);
        tvStationSubtitle = view.findViewById(R.id.tvStationSubtitle);
        btnPinStation = view.findViewById(R.id.btnPinStation);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshStation);
        recyclerDepartures = view.findViewById(R.id.recyclerDepartures);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        btnLaterServices = view.findViewById(R.id.btnLaterServices);

        pinnedDataManager = new PinnedDataManager(requireContext());
        api = APIClient.getClient().create(NationalRailAPI.class);

        setupHeader();
        setupPinButton();

        recyclerDepartures.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DeparturesAdapter(requireContext(), new ArrayList<>());
        recyclerDepartures.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(() -> fetchTrains(false));

        btnLaterServices.setOnClickListener(v -> {
            currentOffset += 119;
            btnLaterServices.setText("Loading...");
            btnLaterServices.setEnabled(false);
            fetchTrains(true);
        });

        fetchTrains(false);
    }

    private void setupHeader() {
        if (isArrivals) {
            tvDirectionHeading.setText("Trains arriving at");
        } else {
            tvDirectionHeading.setText("Trains departing from");
        }

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

            String prefix = isArrivals ? "from " : "to ";
            tvStationSubtitle.setText(prefix + toName);
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

    private void fetchTrains(boolean isAppending) {

        if (!isAppending) {
            currentOffset = 0;
            swipeRefreshLayout.setRefreshing(true);
            tvEmptyState.setVisibility(View.GONE);
        }

        String apiFilterCrs = toCrs.isEmpty() ? null : toCrs;

        String apiFilterType = null;
        if (apiFilterCrs != null) {
            apiFilterType = isArrivals ? "from" : "to";
        }

        Call<StationDepartureResponse> networkCall;
        if (isArrivals) {
            networkCall = api.getStationArrivals(fromCrs, 150, apiFilterCrs, apiFilterType, currentOffset, 120);
        } else {
            networkCall = api.getStationDepartures(fromCrs, 150, apiFilterCrs, apiFilterType, currentOffset, 120);
        }

        networkCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<StationDepartureResponse> call, @NonNull Response<StationDepartureResponse> response) {
                if (!isAdded()) return;
                swipeRefreshLayout.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    List<StationDepartureResponse.TrainService> allServices = response.body().trainServices;



                    if (allServices != null && !allServices.isEmpty()) {
                        if (isAppending) {
                            int newTrainsAdded = adapter.appendData(allServices);


                            if (newTrainsAdded == 0) {
                                Toast.makeText(requireContext(), "End of scheduled services.", Toast.LENGTH_SHORT).show();
                                if (btnLaterServices != null) btnLaterServices.setVisibility(View.GONE);
                            } else {
                                if (btnLaterServices != null) btnLaterServices.setVisibility(View.VISIBLE);
                            }
                        } else {
                            adapter.updateData(allServices, isArrivals, fromCrs, toCrs);
                            recyclerDepartures.setVisibility(View.VISIBLE);
                            if (btnLaterServices != null) btnLaterServices.setVisibility(View.VISIBLE);
                        }
                    } else {
                        if (btnLaterServices != null) btnLaterServices.setVisibility(View.GONE);
                        if (!isAppending) {
                            showEmptyState(isArrivals ? "No direct arrivals found right now." : "No direct trains found right now.");
                        } else {
                            Toast.makeText(requireContext(), "No later services found.", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    if (btnLaterServices != null) btnLaterServices.setVisibility(View.GONE);
                    if (!isAppending) showEmptyState("API Error: Please check your connection and CRS codes.");
                }

                if (btnLaterServices != null && btnLaterServices.getVisibility() == View.VISIBLE) {
                    btnLaterServices.setText("View later services");
                    btnLaterServices.setEnabled(true);
                }
            }

            @Override
            public void onFailure(@NonNull Call<StationDepartureResponse> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                swipeRefreshLayout.setRefreshing(false);

                android.util.Log.e("API_DEBUG", "Network Call Failed!", t);

                if (btnLaterServices != null) {
                    btnLaterServices.setText("View later services");
                    btnLaterServices.setEnabled(true);
                }

                if (!isAppending) showEmptyState("Network error. Please try again.");
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