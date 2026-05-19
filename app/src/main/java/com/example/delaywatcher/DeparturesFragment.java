package com.example.delaywatcher;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DeparturesFragment extends Fragment {

    private AutoCompleteTextView etFromCrs;
    private AutoCompleteTextView etToCrs;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerDepartures;
    private TextView tvEmptyState;

    private DeparturesAdapter adapter;
    private NationalRailAPI api;

    private String currentFromCrs = "";
    private String currentToCrs = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_departures, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        etFromCrs = view.findViewById(R.id.etFromCrs);
        etToCrs = view.findViewById(R.id.etToCrs);
        ImageButton btnSwap = view.findViewById(R.id.btnSwapStations);
        Button btnSearch = view.findViewById(R.id.btnSearchDepartures);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshDepartures);
        recyclerDepartures = view.findViewById(R.id.recyclerDepartures);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        List<String> stations = StationHelper.getStationNames();
        ArrayAdapter<String> autoCompleteAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, stations);
        etFromCrs.setAdapter(autoCompleteAdapter);
        etToCrs.setAdapter(autoCompleteAdapter);
        recyclerDepartures.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DeparturesAdapter(requireContext(), new ArrayList<>());
        recyclerDepartures.setAdapter(adapter);
        api = APIClient.getClient().create(NationalRailAPI.class);
        btnSwap.setOnClickListener(v -> {
            String tempText = etFromCrs.getText().toString();
            etFromCrs.setText(etToCrs.getText().toString());
            etToCrs.setText(tempText);
            etFromCrs.dismissDropDown();
            etToCrs.dismissDropDown();
        });

        // Search Button Logic
        btnSearch.setOnClickListener(v -> {
            String fromName = etFromCrs.getText().toString().trim();
            String toName = etToCrs.getText().toString().trim();
            String fromCrs = StationHelper.getCrsFromName(fromName);
            String toCrs = toName.isEmpty() ? "" : StationHelper.getCrsFromName(toName);
            if (fromCrs == null && fromName.length() == 3) fromCrs = fromName.toUpperCase();
            if (toCrs == null && toName.length() == 3) toCrs = toName.toUpperCase();
            if (fromCrs != null && fromCrs.length() == 3) {
                currentFromCrs = fromCrs;
                currentToCrs = toCrs == null ? "" : toCrs;

                hideKeyboard();
                fetchDepartures(currentFromCrs, currentToCrs);
            } else {
                Toast.makeText(requireContext(), "Please select a valid departing station", Toast.LENGTH_SHORT).show();
            }
        });
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (!currentFromCrs.isEmpty()) {
                fetchDepartures(currentFromCrs, currentToCrs);
            } else {
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void fetchDepartures(String fromCrs, String toCrs) {
        String key = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE).getString("LDBWS_KEY", "");

        if (key.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter your Live Departures (LDBWS) API Key in Settings", Toast.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        swipeRefreshLayout.setRefreshing(true);
        tvEmptyState.setVisibility(View.GONE);
        api.getStationDepartures(key, fromCrs).enqueue(new Callback<StationDepartureResponse>() {
            @Override
            public void onResponse(Call<StationDepartureResponse> call, Response<StationDepartureResponse> response) {
                if (!isAdded()) return;
                swipeRefreshLayout.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    List<StationDepartureResponse.TrainService> allServices = response.body().trainServices;

                    if (allServices != null && !allServices.isEmpty()) {
                        List<StationDepartureResponse.TrainService> filteredServices = new ArrayList<>();

                        if (toCrs.isEmpty()) {
                            filteredServices.addAll(allServices);
                        } else {
                            for (StationDepartureResponse.TrainService service : allServices) {
                                boolean matchesDestination = false;
                                if (service.destination != null) {
                                    for (StationDepartureResponse.Location loc : service.destination) {
                                        if (toCrs.equalsIgnoreCase(loc.crs)) {
                                            matchesDestination = true;
                                            break;
                                        }
                                    }
                                }
                                if (!matchesDestination && service.subsequentCallingPoints != null) {
                                    for (StationDepartureResponse.SubsequentCallingPointList list : service.subsequentCallingPoints) {
                                        if (list.callingPoint != null) {
                                            for (StationDepartureResponse.CallingPoint cp : list.callingPoint) {
                                                if (toCrs.equalsIgnoreCase(cp.crs)) {
                                                    matchesDestination = true;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }

                                if (matchesDestination) {
                                    filteredServices.add(service);
                                }
                            }
                        }

                        if (!filteredServices.isEmpty()) {
                            adapter.updateData(filteredServices);
                            recyclerDepartures.setVisibility(View.VISIBLE);
                        } else {
                            showEmptyState("No direct trains found from " + fromCrs + " to " + toCrs + " right now.");
                        }

                    } else {
                        showEmptyState("No live departures found for this station right now.");
                    }
                } else {
                    showEmptyState("API Error: Please check your CRS codes and API subscription.");
                }
            }

            @Override
            public void onFailure(Call<StationDepartureResponse> call, Throwable t) {
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

    private void hideKeyboard() {
        View view = requireActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}