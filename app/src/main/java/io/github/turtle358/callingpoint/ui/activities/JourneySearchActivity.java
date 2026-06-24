package io.github.turtle358.callingpoint.ui.activities;

import static io.github.turtle358.callingpoint.utils.InlineAutocompleteManager.applyInlineAutocomplete;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.text.Editable;
import android.text.TextWatcher;

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

    private StationAutoCompleteAdapter autoCompleteAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_journey_search);

        etFromCrs = findViewById(R.id.etFromCrs);
        etToCrs = findViewById(R.id.etToCrs);
        ImageButton btnSwap = findViewById(R.id.btnSwapStations);
        Button btnSearch = findViewById(R.id.btnSearchJourney);
        Button btnMaps = findViewById(R.id.btnSearchMaps);
        recyclerSearchResults = findViewById(R.id.recyclerSearchResults);
        progressSearch = findViewById(R.id.progressSearch);
        tvStatusUpdate = findViewById(R.id.tvStatusUpdate);


        GraphManager graphManager = GraphManager.getInstance(this);

        plannerEngine = new JourneyPlannerEngine(graphManager);

        List<String> stations = StationHelper.getStationNames();
        autoCompleteAdapter = new StationAutoCompleteAdapter(this, stations);
        etFromCrs.setAdapter(autoCompleteAdapter);
        etToCrs.setAdapter(autoCompleteAdapter);
        applyInlineAutocomplete(etFromCrs);
        applyInlineAutocomplete(etToCrs);
        setupAutoCompleteEnterBehavior(etFromCrs, autoCompleteAdapter, etToCrs, null);
        setupAutoCompleteEnterBehavior(etToCrs, autoCompleteAdapter, null, () -> btnSearch.performClick());

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

        btnSearch.setOnClickListener(v -> handleSearch(false));
        btnMaps.setOnClickListener(v -> handleSearch(true));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (autoCompleteAdapter != null) {
            autoCompleteAdapter.updateStations(StationHelper.getStationNames());
        }
    }

    private void handleSearch(boolean useGoogleMaps) {
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

        if (useGoogleMaps) {
            launchMapsRouting(fromName, toName, fromCrs, toCrs);
        } else {
            startInternalRouting(fromCrs, toCrs);
        }
    }

    private void launchMapsRouting(String originName, String destName, String originCrs, String destCrs) {
        try {
            String originSearch = java.net.URLEncoder.encode(originName + " Station, UK", "UTF-8");
            String destSearch = java.net.URLEncoder.encode(destName + " Station, UK", "UTF-8");

            String uriString = "https://www.google.com/maps/dir/?api=1" +
                    "&origin=" + originSearch +
                    "&destination=" + destSearch +
                    "&travelmode=transit";

            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uriString));
            intent.setPackage("com.google.android.apps.maps");

            startActivity(intent);

        } catch (android.content.ActivityNotFoundException e) {
            triggerMapsFallback(originCrs, destCrs);
        } catch (Exception e) {
            triggerMapsFallback(originCrs, destCrs);
        }
    }

    private void triggerMapsFallback(String originCrs, String destCrs) {
        Toast.makeText(this, "Maps app unavailable. Using in-app routing...", Toast.LENGTH_LONG).show();
        startInternalRouting(originCrs, destCrs);
    }

    private void startInternalRouting(String fromCrs, String toCrs) {
        recyclerSearchResults.setVisibility(View.GONE);
        progressSearch.setVisibility(View.VISIBLE);
        tvStatusUpdate.setVisibility(View.VISIBLE);
        tvStatusUpdate.setText("Initialising search...");

        if (plannerEngine != null) {
            findRoutes(fromCrs, toCrs);
        } else {
            tvStatusUpdate.setText("Routing engine unavailable. Check your connection.");
            progressSearch.setVisibility(View.GONE);
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