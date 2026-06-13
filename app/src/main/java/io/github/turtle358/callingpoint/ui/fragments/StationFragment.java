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
            if (btnLaterServices != null) btnLaterServices.setVisibility(View.GONE);
        }

        String apiFilterCrs = toCrs.isEmpty() ? null : toCrs;
        String apiFilterType = null;
        if (apiFilterCrs != null) {
            apiFilterType = isArrivals ? "from" : "to";
        }

        Call<StationDepartureResponse> networkCall;
        if (isArrivals) {
            networkCall = api.getStationArrivals(fromCrs, 10, apiFilterCrs, apiFilterType, currentOffset, 120);
        } else {
            networkCall = api.getStationDepartures(fromCrs, 10, apiFilterCrs, apiFilterType, currentOffset, 120);
        }

        networkCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<StationDepartureResponse> call, @NonNull Response<StationDepartureResponse> response) {
                if (!isAdded()) return;

                if (response.isSuccessful() && response.body() != null) {
                    List<StationDepartureResponse.TrainService> allServices = response.body().trainServices;

                    if (allServices != null && !allServices.isEmpty()) {
                        int newTrainsAdded = isAppending ? adapter.appendData(allServices) : allServices.size();

                        if (newTrainsAdded == 0) {
                            handleEmptyOrOverlapResult(allServices, isAppending);
                        } else {
                            swipeRefreshLayout.setRefreshing(false);

                            if (!isAppending) {
                                adapter.updateData(allServices, isArrivals, fromCrs, toCrs);
                                recyclerDepartures.setVisibility(View.VISIBLE);
                            }

                            currentOffset = calculateNextOffset(allServices);

                            if (btnLaterServices != null && currentOffset < 119) {
                                btnLaterServices.setVisibility(View.VISIBLE);
                                btnLaterServices.setText("View later services");
                                btnLaterServices.setEnabled(true);
                            } else if (btnLaterServices != null) {
                                btnLaterServices.setVisibility(View.GONE);
                            }
                        }
                    } else {
                        handleEmptyOrOverlapResult(null, isAppending);
                    }
                } else {
                    swipeRefreshLayout.setRefreshing(false);
                    if (btnLaterServices != null) btnLaterServices.setVisibility(View.GONE);
                    if (!isAppending) showEmptyState("Connection Error: Please check your connection.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<StationDepartureResponse> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                swipeRefreshLayout.setRefreshing(false);

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

    private void handleEmptyOrOverlapResult(List<StationDepartureResponse.TrainService> fetchedServices, boolean isAppending) {
        if (currentOffset >= 119) {
            swipeRefreshLayout.setRefreshing(false);

            if (adapter.getItemCount() == 0) {
                if (btnLaterServices != null) btnLaterServices.setVisibility(View.GONE);
                showEmptyState(isArrivals ? "No arrivals found within the next 2 hours." : "No direct trains found within the next 2 hours.");
            } else {
                if (btnLaterServices != null) btnLaterServices.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "End of scheduled services.", Toast.LENGTH_SHORT).show();
            }
        } else {
            if (adapter.getItemCount() == 0) {
                tvEmptyState.setVisibility(View.VISIBLE);
                tvEmptyState.setText("Scanning forward for services...");
            } else if (btnLaterServices != null) {
                btnLaterServices.setText("Scanning...");
                btnLaterServices.setEnabled(false);
            }

            currentOffset = calculateNextOffset(fetchedServices);
            fetchTrains(true);
        }
    }

    private int calculateNextOffset(List<StationDepartureResponse.TrainService> fetchedServices) {
        if (fetchedServices == null || fetchedServices.isEmpty()) {
            return Math.min(119, currentOffset + 30);
        }

        StationDepartureResponse.TrainService lastService = fetchedServices.get(fetchedServices.size() - 1);
        String timeStr = lastService.std != null ? lastService.std : lastService.sta;

        if (timeStr == null || !timeStr.contains(":")) return Math.min(119, currentOffset + 10);

        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK);
            java.util.Date trainTime = sdf.parse(timeStr);

            java.util.Calendar nowCal = java.util.Calendar.getInstance();
            int currentH = nowCal.get(java.util.Calendar.HOUR_OF_DAY);
            int currentM = nowCal.get(java.util.Calendar.MINUTE);
            String nowStr = String.format(java.util.Locale.UK, "%02d:%02d", currentH, currentM);
            java.util.Date nowTime = sdf.parse(nowStr);

            long diffMins = (trainTime.getTime() - nowTime.getTime()) / 60000;
            if (diffMins < 0) diffMins += 24 * 60;

            int nextOffset = (int) diffMins;
            if (nextOffset <= currentOffset) {
                nextOffset = currentOffset + 5;
            }

            return Math.min(119, nextOffset);

        } catch (Exception e) {
            return Math.min(119, currentOffset + 10);
        }
    }
}