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
import android.widget.TextView;
import android.widget.Toast;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.shimmer.ShimmerFrameLayout;

import java.util.ArrayList;
import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.ui.adapters.JourneyAdapter;
import io.github.turtle358.callingpoint.ui.adapters.StationAutoCompleteAdapter;
import io.github.turtle358.callingpoint.utils.GraphManager;
import io.github.turtle358.callingpoint.utils.JourneyPlannerEngine;
import io.github.turtle358.callingpoint.utils.StationHelper;
import io.github.turtle358.callingpoint.utils.TflJourneyPlannerEngine;

public class JourneySearchActivity extends AppCompatActivity {

    private AutoCompleteTextView etFromCrs, etToCrs;
    private RecyclerView recyclerSearchResults;
    private TextView tvStatusUpdate;
    private ProgressBar progressSearch;

    private JourneyAdapter adapter;
    private JourneyPlannerEngine plannerEngine;
    private TflJourneyPlannerEngine tflPlannerEngine;

    private StationAutoCompleteAdapter autoCompleteAdapter;
    private ShimmerFrameLayout shimmerView;

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
        shimmerView = findViewById(R.id.shimmer_journey_container);

        GraphManager graphManager = GraphManager.getInstance(this);

        plannerEngine = new JourneyPlannerEngine(graphManager);
        tflPlannerEngine = new TflJourneyPlannerEngine();

        setupAdaptersAndListeners(btnSearch);

        etFromCrs.setOnClickListener(v -> {
            if (etFromCrs.getText().length() > 0) etFromCrs.showDropDown();
        });
        etFromCrs.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && etFromCrs.getText().length() > 0) etFromCrs.showDropDown();
        });

        etToCrs.setOnClickListener(v -> {
            if (etToCrs.getText().length() > 0) etToCrs.showDropDown();
        });
        etToCrs.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && etToCrs.getText().length() > 0) etToCrs.showDropDown();
        });

        etFromCrs.requestFocus();

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                finish();
            });
        }

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

        Button btnSearch = findViewById(R.id.btnSearchJourney);
        setupAdaptersAndListeners(btnSearch);
    }

    private void setupAdaptersAndListeners(Button btnSearch) {
        List<String> stations = StationHelper.getStationNames();
        autoCompleteAdapter = new StationAutoCompleteAdapter(this, stations);

        etFromCrs.setAdapter(autoCompleteAdapter);
        etToCrs.setAdapter(autoCompleteAdapter);

        applyInlineAutocomplete(etFromCrs);
        applyInlineAutocomplete(etToCrs);

        setupAutoCompleteEnterBehavior(etFromCrs, autoCompleteAdapter, etToCrs, null);
        setupAutoCompleteEnterBehavior(etToCrs, autoCompleteAdapter, null, () -> {
            if (btnSearch != null) btnSearch.performClick();
        });
    }

    private void handleSearch(boolean useGoogleMaps) {
        etFromCrs.dismissDropDown();
        etToCrs.dismissDropDown();

        String fromName = etFromCrs.getText().toString().trim();
        String toName = etToCrs.getText().toString().trim();

        String fromCrs = StationHelper.getCrsFromName(fromName);
        String toCrs = StationHelper.getCrsFromName(toName);

        String fromNaptan = StationHelper.getNaptanFromName(fromName);
        String toNaptan = StationHelper.getNaptanFromName(toName);

        if (fromCrs == null && fromName.length() == 3) fromCrs = fromName.toUpperCase();
        if (toCrs == null && toName.length() == 3) toCrs = toName.toUpperCase();

        if ((fromCrs == null && fromNaptan == null) || (toCrs == null && toNaptan == null)) {
            Toast.makeText(this, "Please select valid stations", Toast.LENGTH_SHORT).show();
            return;
        }

        if (fromName.equals(toName) || (fromCrs != null && fromCrs.equals(toCrs))) {
            Toast.makeText(this, "Origin and Destination cannot be the same", Toast.LENGTH_SHORT).show();
            return;
        }

        hideKeyboard();

        if (useGoogleMaps) {
            launchMapsRouting(fromName, toName, fromCrs != null ? fromCrs : fromNaptan, toCrs != null ? toCrs : toNaptan);
        } else {
            if (fromNaptan != null && !fromNaptan.isEmpty() && toNaptan != null && !toNaptan.isEmpty()) {
                startTflRouting(fromNaptan, toNaptan, fromCrs, toCrs);
            } else if (fromCrs != null && toCrs != null) {
                startInternalRouting(fromCrs, toCrs);
            } else {
                Toast.makeText(this, "Invalid station routing combination.", Toast.LENGTH_SHORT).show();
            }
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
        tvStatusUpdate.setVisibility(View.VISIBLE);
        tvStatusUpdate.setText("Initialising search...");

        shimmerView.setVisibility(View.VISIBLE);
        shimmerView.startShimmer();

        if (plannerEngine != null) {
            findRoutes(fromCrs, toCrs);
        } else {
            tvStatusUpdate.setText("Routing engine unavailable. Check your connection.");
            shimmerView.stopShimmer();
            shimmerView.setVisibility(View.GONE);
        }
    }

    private void startTflRouting(String fromNaptan, String toNaptan, String fromCrs, String toCrs) {
        recyclerSearchResults.setVisibility(View.GONE);
        tvStatusUpdate.setVisibility(View.VISIBLE);
        tvStatusUpdate.setText("Initialising TfL search...");

        shimmerView.setVisibility(View.VISIBLE);
        shimmerView.startShimmer();

        if (tflPlannerEngine != null) {
            tflPlannerEngine.findRoutes(fromNaptan, toNaptan, new JourneyPlannerEngine.JourneyCallback() {
                @Override
                public void onJourneysFound(List<JourneyOption> journeys) {
                    if (isDestroyed()) return;
                    tvStatusUpdate.setVisibility(View.GONE);
                    shimmerView.stopShimmer();
                    shimmerView.setVisibility(View.GONE);

                    if (journeys != null && !journeys.isEmpty()) {
                        adapter.updateData(journeys);
                        recyclerSearchResults.setVisibility(View.VISIBLE);
                    } else {
                        if (fromCrs != null && toCrs != null) {
                            startInternalRouting(fromCrs, toCrs);
                        }
                    }
                }

                @Override
                public void onError(String message) {
                    if (isDestroyed()) return;
                    if (fromCrs != null && toCrs != null) {
                        startInternalRouting(fromCrs, toCrs);
                    } else {
                        tvStatusUpdate.setVisibility(View.VISIBLE);
                        tvStatusUpdate.setText(message);
                        shimmerView.stopShimmer();
                        shimmerView.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onStatusUpdate(String message) {
                    if (isDestroyed()) return;
                    tvStatusUpdate.setText(message);
                }
            });
        }
    }

    private void findRoutes(String fromCrs, String toCrs) {
        plannerEngine.findRoutes(fromCrs, toCrs, new JourneyPlannerEngine.JourneyCallback() {
            @Override
            public void onJourneysFound(List<JourneyOption> journeys) {
                if (isDestroyed()) return;
                tvStatusUpdate.setVisibility(View.GONE);
                shimmerView.stopShimmer();
                shimmerView.setVisibility(View.GONE);

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
                tvStatusUpdate.setVisibility(View.VISIBLE);
                tvStatusUpdate.setText(message);
                shimmerView.stopShimmer();
                shimmerView.setVisibility(View.GONE);
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