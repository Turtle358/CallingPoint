package io.github.turtle358.callingpoint.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.ui.adapters.JourneyAdapter;
import io.github.turtle358.callingpoint.ui.adapters.StationAutoCompleteAdapter;
import io.github.turtle358.callingpoint.utils.GraphManager;
import io.github.turtle358.callingpoint.utils.JourneyPlannerEngine;
import io.github.turtle358.callingpoint.utils.StationHelper;

public class JourneySearchActivity extends AppCompatActivity {

    private AutoCompleteTextView etFromCrs, etToCrs;
    private RecyclerView recyclerSearchResults;
    private TextView tvStatusUpdate;
    private ProgressBar progressSearch;

    private JourneyAdapter adapter;
    private JourneyPlannerEngine plannerEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_journey_search);

        etFromCrs = findViewById(R.id.etFromCrs);
        etToCrs = findViewById(R.id.etToCrs);
        ImageButton btnSwap = findViewById(R.id.btnSwapStations);
        Button btnSearch = findViewById(R.id.btnSearchJourney);
        recyclerSearchResults = findViewById(R.id.recyclerSearchResults);
        progressSearch = findViewById(R.id.progressSearch);
        tvStatusUpdate = findViewById(R.id.tvStatusUpdate);


        GraphManager graphManager = GraphManager.getInstance(this);
        String ldbwsKey = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE).getString("LDBWS_KEY", "");
        String serviceDetailsKey = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE).getString("SERVICE_DETAILS_KEY", "");

        if (ldbwsKey.isEmpty() || serviceDetailsKey.isEmpty()) {
            Toast.makeText(this, "Error: Both API Keys must be set in Settings", Toast.LENGTH_LONG).show();
        } else {
            plannerEngine = new JourneyPlannerEngine(graphManager, ldbwsKey, serviceDetailsKey);
        }

        List<String> stations = StationHelper.getStationNames();
        StationAutoCompleteAdapter autoCompleteAdapter = new StationAutoCompleteAdapter(this, stations);
        etFromCrs.setAdapter(autoCompleteAdapter);
        etToCrs.setAdapter(autoCompleteAdapter);

        etFromCrs.requestFocus();

        recyclerSearchResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new JourneyAdapter(new ArrayList<>(), route -> {
            Intent intent = new Intent(this, JourneyDetailsActivity.class);
            intent.putExtra("JOURNEY_OPTION", route);
            startActivity(intent);
        });
        recyclerSearchResults.setAdapter(adapter);

        btnSwap.setOnClickListener(v -> {
            String tempText = etFromCrs.getText().toString();
            etFromCrs.setText(etToCrs.getText().toString());
            etToCrs.setText(tempText);
            etFromCrs.dismissDropDown();
            etToCrs.dismissDropDown();
        });

        btnSearch.setOnClickListener(v -> handleSearch());
    }

    private void handleSearch() {
        String fromName = etFromCrs.getText().toString().trim();
        String toName = etToCrs.getText().toString().trim();

        String fromCrs = StationHelper.getCrsFromName(fromName);
        String toCrs = StationHelper.getCrsFromName(toName);

        if (fromCrs == null && fromName.length() == 3) fromCrs = fromName.toUpperCase();
        if (toCrs == null && toName.length() == 3) toCrs = toName.toUpperCase();

        if (fromCrs == null || fromCrs.length() != 3 || toCrs == null || toCrs.length() != 3) {
            Toast.makeText(this, "Please select valid stations", Toast.LENGTH_SHORT).show();
            return;
        }

        if (fromCrs.equals(toCrs)) {
            Toast.makeText(this, "Origin and Destination cannot be the same", Toast.LENGTH_SHORT).show();
            return;
        }

        hideKeyboard();

        recyclerSearchResults.setVisibility(View.GONE);
        progressSearch.setVisibility(View.VISIBLE);
        tvStatusUpdate.setVisibility(View.VISIBLE);
        tvStatusUpdate.setText("Initializing search...");

        if (plannerEngine != null) {
            findRoutes(fromCrs, toCrs);
        }
    }

    private void findRoutes(String fromCrs, String toCrs) {
        plannerEngine.findRoutes(fromCrs, toCrs, new JourneyPlannerEngine.JourneyCallback() {
            @Override
            public void onJourneysFound(List<JourneyOption> journeys) {
                if (isDestroyed()) return;
                progressSearch.setVisibility(View.GONE);
                tvStatusUpdate.setVisibility(View.GONE);

                if (journeys != null && !journeys.isEmpty()) {
                    adapter.updateData(journeys);
                    recyclerSearchResults.setVisibility(View.VISIBLE);
                } else {
                    tvStatusUpdate.setVisibility(View.VISIBLE);
                    tvStatusUpdate.setText("No routes found between these stations.");
                }
            }

            @Override
            public void onError(String message) {
                if (isDestroyed()) return;
                progressSearch.setVisibility(View.GONE);
                tvStatusUpdate.setVisibility(View.VISIBLE);
                tvStatusUpdate.setText(message);
            }

            @Override
            public void onStatusUpdate(String message) {
                if (isDestroyed()) return;
                tvStatusUpdate.setText(message);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (plannerEngine != null) {
            plannerEngine.shutdown();
        }
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}