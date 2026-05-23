package io.github.turtle358.callingpoint.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
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

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.ui.activities.JourneyDetailsActivity;
import io.github.turtle358.callingpoint.ui.adapters.JourneyAdapter;
import io.github.turtle358.callingpoint.ui.adapters.StationAutoCompleteAdapter;
import io.github.turtle358.callingpoint.utils.GraphManager;
import io.github.turtle358.callingpoint.utils.JourneyPlannerEngine;
import io.github.turtle358.callingpoint.utils.StationHelper;

public class JourneyPlannerFragment extends Fragment {

    private AutoCompleteTextView etFromCrs, etToCrs;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerJourney;
    private TextView tvEmptyState;
    private TextView tvStatusUpdate;
    private ProgressBar progressSearch;
    private JourneyAdapter adapter;
    private JourneyPlannerEngine plannerEngine;
    private GraphManager graphManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_journey_planner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etFromCrs = view.findViewById(R.id.etFromCrs);
        etToCrs = view.findViewById(R.id.etToCrs);
        ImageButton btnSwap = view.findViewById(R.id.btnSwapStations);
        Button btnSearch = view.findViewById(R.id.btnSearchJourney);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshJourney);
        recyclerJourney = view.findViewById(R.id.recyclerJourney);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        progressSearch = view.findViewById(R.id.progressSearch);

        // Bind the new status update text view
        tvStatusUpdate = view.findViewById(R.id.tvStatusUpdate);

        graphManager = GraphManager.getInstance(requireContext());
        String ldbwsKey = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE).getString("LDBWS_KEY", "");
        String serviceDetailsKey = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE).getString("SERVICE_DETAILS_KEY", "");

        if (ldbwsKey.isEmpty() || serviceDetailsKey.isEmpty()) {
            Toast.makeText(requireContext(), "Error: Both API Keys must be set in Settings", Toast.LENGTH_LONG).show();
        } else {
            plannerEngine = new JourneyPlannerEngine(graphManager, ldbwsKey, serviceDetailsKey);
        }

        List<String> stations = StationHelper.getStationNames();
        StationAutoCompleteAdapter autoCompleteAdapter = new StationAutoCompleteAdapter(requireContext(), stations);
        etFromCrs.setAdapter(autoCompleteAdapter);
        etToCrs.setAdapter(autoCompleteAdapter);

        recyclerJourney.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new JourneyAdapter(new ArrayList<>(), route -> {
            Intent intent = new Intent(requireContext(), JourneyDetailsActivity.class);
            intent.putExtra("JOURNEY_OPTION", route);
            startActivity(intent);
        });
        recyclerJourney.setAdapter(adapter);

        btnSwap.setOnClickListener(v -> {
            String tempText = etFromCrs.getText().toString();
            etFromCrs.setText(etToCrs.getText().toString());
            etToCrs.setText(tempText);
            etFromCrs.dismissDropDown();
            etToCrs.dismissDropDown();
        });

        btnSearch.setOnClickListener(v -> handleSearch());
        swipeRefreshLayout.setOnRefreshListener(this::handleSearch);
    }

    private void handleSearch() {
        String fromName = etFromCrs.getText().toString().trim();
        String toName = etToCrs.getText().toString().trim();

        String fromCrs = StationHelper.getCrsFromName(fromName);
        String toCrs = StationHelper.getCrsFromName(toName);

        if (fromCrs == null && fromName.length() == 3) fromCrs = fromName.toUpperCase();
        if (toCrs == null && toName.length() == 3) toCrs = toName.toUpperCase();

        if (fromCrs == null || fromCrs.length() != 3 || toCrs == null || toCrs.length() != 3) {
            Toast.makeText(requireContext(), "Please select valid From and To stations", Toast.LENGTH_SHORT).show();
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        if (fromCrs.equals(toCrs)) {
            Toast.makeText(requireContext(), "Origin and Destination cannot be the same", Toast.LENGTH_SHORT).show();
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        hideKeyboard();

        // Setup the UI for a fresh search
        tvEmptyState.setVisibility(View.GONE);
        recyclerJourney.setVisibility(View.GONE);
        progressSearch.setVisibility(View.VISIBLE);

        // Show the status text directly under the button
        tvStatusUpdate.setVisibility(View.VISIBLE);
        tvStatusUpdate.setText("Initializing search...");

        findRoutes(fromCrs, toCrs);
    }

    private void findRoutes(String fromCrs, String toCrs) {
        plannerEngine.findRoutes(fromCrs, toCrs, new JourneyPlannerEngine.JourneyCallback() {
            @Override
            public void onJourneysFound(List<JourneyOption> journeys) {
                if (!isAdded()) return;
                swipeRefreshLayout.setRefreshing(false);
                progressSearch.setVisibility(View.GONE);
                tvStatusUpdate.setVisibility(View.GONE); // Hide status text on success

                if (journeys != null && !journeys.isEmpty()) {
                    adapter.updateData(journeys);
                    recyclerJourney.setVisibility(View.VISIBLE);
                    tvEmptyState.setVisibility(View.GONE);
                } else {
                    tvEmptyState.setText("No routes found between these stations.");
                    tvEmptyState.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                swipeRefreshLayout.setRefreshing(false);
                progressSearch.setVisibility(View.GONE);
                tvStatusUpdate.setVisibility(View.GONE); // Hide status text on error
                tvEmptyState.setText(message);
                tvEmptyState.setVisibility(View.VISIBLE);
            }

            @Override
            public void onStatusUpdate(String message) {
                if (!isAdded()) return;
                // Keep the UI snappy by updating this specific text view
                tvStatusUpdate.setText(message);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (plannerEngine != null) {
            plannerEngine.shutdown();
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