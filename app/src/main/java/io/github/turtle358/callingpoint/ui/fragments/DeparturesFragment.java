package io.github.turtle358.callingpoint.ui.fragments;

import static io.github.turtle358.callingpoint.utils.InlineAutocompleteManager.applyInlineAutocomplete;

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
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

import io.github.turtle358.callingpoint.ui.adapters.DeparturesAdapter;
import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.utils.StationHelper;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.NationalRailAPI;
import io.github.turtle358.callingpoint.models.StationDepartureResponse;
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

    private ArrayAdapter<String> autoCompleteAdapter;

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
        autoCompleteAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, stations);
        etFromCrs.setAdapter(autoCompleteAdapter);
        etToCrs.setAdapter(autoCompleteAdapter);
        applyInlineAutocomplete(etFromCrs);
        applyInlineAutocomplete(etToCrs);
        setupAutoCompleteEnterBehavior(etFromCrs, autoCompleteAdapter, etToCrs, null);
        setupAutoCompleteEnterBehavior(etToCrs, autoCompleteAdapter, null, () -> btnSearch.performClick());

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

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && autoCompleteAdapter != null) {
            autoCompleteAdapter.setNotifyOnChange(false);
            autoCompleteAdapter.clear();
            autoCompleteAdapter.addAll(StationHelper.getStationNames());
            autoCompleteAdapter.notifyDataSetChanged();
        }
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

        String apiFilterCrs = toCrs.isEmpty() ? null : toCrs;

        api.getStationDepartures(key, fromCrs, 150, 120, apiFilterCrs).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<StationDepartureResponse> call, @NonNull Response<StationDepartureResponse> response) {
                if (!isAdded()) return;
                swipeRefreshLayout.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    List<StationDepartureResponse.TrainService> allServices = response.body().trainServices;

                    if (allServices != null && !allServices.isEmpty()) {
                        adapter.updateData(allServices, false, fromCrs);
                        recyclerDepartures.setVisibility(View.VISIBLE);
                    } else {
                        if (apiFilterCrs != null) {
                            showEmptyState("No direct trains found from " + fromCrs + " to " + toCrs + " right now.");
                        } else {
                            showEmptyState("No live departures found for this station right now.");
                        }
                    }
                } else {
                    showEmptyState("API Error: Please check your CRS codes and API subscription.");
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
        adapter.updateData(new ArrayList<>(), false, currentFromCrs);
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

    private void setupAutoCompleteEnterBehavior(AutoCompleteTextView autoCompleteTextView, ArrayAdapter<String> adapter, View nextFocus, Runnable onDone) {
        autoCompleteTextView.setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnterPressed = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;

            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH || isEnterPressed) {

                String currentText = autoCompleteTextView.getText().toString();
                if (!currentText.isEmpty() && adapter.getCount() > 0) {
                    String firstMatch = adapter.getItem(0);
                    if (firstMatch != null) {
                        autoCompleteTextView.setText(firstMatch);
                        autoCompleteTextView.dismissDropDown();
                        autoCompleteTextView.setSelection(firstMatch.length());
                    }
                }

                if (nextFocus != null) {
                    nextFocus.requestFocus();
                } else if (onDone != null) {
                    onDone.run();
                }
                return true;
            }
            return false;
        });
    }

}