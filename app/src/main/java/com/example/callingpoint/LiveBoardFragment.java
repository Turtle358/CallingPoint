package com.example.callingpoint;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LiveBoardFragment extends Fragment {

    private AutoCompleteTextView etFromCrs;
    private AutoCompleteTextView etToCrs;

    private RecyclerView recyclerPinnedStations;
    private RecyclerView recyclerPinnedServices;
    private TextView tvEmptyPinnedStations;
    private TextView tvEmptyPinnedServices;

    private PinnedDataManager pinnedDataManager;

    private PinnedStationAdapter stationAdapter;
    private PinnedServiceAdapter serviceAdapter;
    private final List<String> currentPinnedStations = new ArrayList<>();
    private final List<ServiceDetailsResponse> liveServicesData = new ArrayList<>();

    private Handler autoRefreshHandler;
    private Runnable autoRefreshRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_live_board, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etFromCrs = view.findViewById(R.id.etFromCrs);
        etToCrs = view.findViewById(R.id.etToCrs);
        ImageButton btnSwap = view.findViewById(R.id.btnSwapStations);
        Button btnSearch = view.findViewById(R.id.btnSearchDepartures);

        recyclerPinnedStations = view.findViewById(R.id.recyclerPinnedStations);
        recyclerPinnedServices = view.findViewById(R.id.recyclerPinnedServices);
        tvEmptyPinnedStations = view.findViewById(R.id.tvEmptyPinnedStations);
        tvEmptyPinnedServices = view.findViewById(R.id.tvEmptyPinnedServices);

        pinnedDataManager = new PinnedDataManager(requireContext());

        List<String> stations = StationHelper.getStationNames();
        ArrayAdapter<String> autoCompleteAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, stations);
        etFromCrs.setAdapter(autoCompleteAdapter);
        etToCrs.setAdapter(autoCompleteAdapter);

        recyclerPinnedStations.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerPinnedStations.setNestedScrollingEnabled(false);
        stationAdapter = new PinnedStationAdapter(requireContext(), currentPinnedStations, (fromCrs, toCrs) -> {
            launchStationFragment(fromCrs, toCrs);
        });
        recyclerPinnedStations.setAdapter(stationAdapter);

        recyclerPinnedServices.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerPinnedServices.setNestedScrollingEnabled(false);
        serviceAdapter = new PinnedServiceAdapter(requireContext(), liveServicesData);
        recyclerPinnedServices.setAdapter(serviceAdapter);

        btnSwap.setOnClickListener(v -> {
            String tempText = etFromCrs.getText().toString();
            etFromCrs.setText(etToCrs.getText().toString());
            etToCrs.setText(tempText);
            etFromCrs.dismissDropDown();
            etToCrs.dismissDropDown();
        });

        btnSearch.setOnClickListener(v -> {
            String fromName = etFromCrs.getText().toString().trim();
            String toName = etToCrs.getText().toString().trim();
            String fromCrs = StationHelper.getCrsFromName(fromName);
            String toCrs = toName.isEmpty() ? "" : StationHelper.getCrsFromName(toName);

            if (fromCrs == null && fromName.length() == 3) fromCrs = fromName.toUpperCase();
            if (toCrs == null && toName.length() == 3) toCrs = toName.toUpperCase();

            if (fromCrs != null && fromCrs.length() == 3) {
                hideKeyboard();
                launchStationFragment(fromCrs, toCrs);
            } else {
                Toast.makeText(requireContext(), "Please select a valid departing station", Toast.LENGTH_SHORT).show();
            }
        });

        autoRefreshHandler = new Handler(Looper.getMainLooper());
        autoRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                loadPinnedData();
                autoRefreshHandler.postDelayed(this, 30000);
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        autoRefreshRunnable.run();
    }

    @Override
    public void onPause() {
        super.onPause();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    private void launchStationFragment(String fromCrs, String toCrs) {
        StationFragment fragment = new StationFragment();
        Bundle args = new Bundle();
        args.putString("FROM_CRS", fromCrs);
        args.putString("TO_CRS", toCrs);
        fragment.setArguments(args);

        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void loadPinnedData() {
        List<String> pinnedStations = pinnedDataManager.getPinnedStations();
        List<String> pinnedServices = pinnedDataManager.getPinnedServices();

        tvEmptyPinnedStations.setVisibility(pinnedStations.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerPinnedStations.setVisibility(pinnedStations.isEmpty() ? View.GONE : View.VISIBLE);

        tvEmptyPinnedServices.setVisibility(pinnedServices.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerPinnedServices.setVisibility(pinnedServices.isEmpty() ? View.GONE : View.VISIBLE);

        currentPinnedStations.clear();
        currentPinnedStations.addAll(pinnedStations);
        stationAdapter.notifyDataSetChanged();

        if (!pinnedServices.isEmpty()) {
            NationalRailAPI api = APIClient.getClient().create(NationalRailAPI.class);
            String apiKey = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE).getString("SERVICE_DETAILS_KEY", "");

            List<ServiceDetailsResponse> tempServicesData = new ArrayList<>();
            final int totalRequests = pinnedServices.size();
            final int[] completedRequests = {0};

            for (String serviceId : pinnedServices) {
                api.getServiceDetails(apiKey, serviceId).enqueue(new Callback<ServiceDetailsResponse>() {
                    @Override
                    public void onResponse(Call<ServiceDetailsResponse> call, Response<ServiceDetailsResponse> response) {
                        if (!isAdded()) return;
                        completedRequests[0]++;

                        if (response.isSuccessful() && response.body() != null) {
                            ServiceDetailsResponse data = response.body();
                            data.customServiceID = serviceId;
                            tempServicesData.add(data);
                        } else {
                            if (response.code() != 429) {
                                pinnedDataManager.toggleServicePin(serviceId);
                            }
                        }

                        updateUIWhenFinished(completedRequests[0], totalRequests, tempServicesData, pinnedServices);
                    }

                    @Override
                    public void onFailure(Call<ServiceDetailsResponse> call, Throwable t) {
                        if (!isAdded()) return;
                        completedRequests[0]++;
                        updateUIWhenFinished(completedRequests[0], totalRequests, tempServicesData, pinnedServices);
                    }
                });
            }
        } else {
            liveServicesData.clear();
            serviceAdapter.notifyDataSetChanged();
        }
    }

    private void updateUIWhenFinished(int completed, int total, List<ServiceDetailsResponse> tempServices, List<String> originalPinnedOrder) {
        if (completed == total) {
            tempServices.sort((a, b) -> {
                int indexA = originalPinnedOrder.indexOf(a.customServiceID);
                int indexB = originalPinnedOrder.indexOf(b.customServiceID);
                return Integer.compare(indexA, indexB);
            });

            liveServicesData.clear();
            liveServicesData.addAll(tempServices);
            serviceAdapter.notifyDataSetChanged();
        }
    }

    private void hideKeyboard() {
        View view = requireActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}