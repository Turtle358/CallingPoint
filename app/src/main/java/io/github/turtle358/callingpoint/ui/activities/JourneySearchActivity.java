package io.github.turtle358.callingpoint.ui.activities;


import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
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
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.ui.adapters.JourneyAdapter;
import io.github.turtle358.callingpoint.ui.adapters.StationAutoCompleteAdapter;
import io.github.turtle358.callingpoint.utils.GraphManager;
import io.github.turtle358.callingpoint.utils.JourneyPlannerEngine;
import io.github.turtle358.callingpoint.utils.RecentStationsManager;
import io.github.turtle358.callingpoint.utils.StationHelper;
import io.github.turtle358.callingpoint.utils.TflJourneyPlannerEngine;

public class JourneySearchActivity extends AppCompatActivity {

    private AutoCompleteTextView etFromCrs, etToCrs;
    private RecyclerView recyclerSearchResults;
    private TextView tvStatusUpdate, tvTimeSelection;
    private ProgressBar progressSearch;

    private View cardFallbackWarning;
    private View btnTimeSelector;
    private ImageButton btnResetTime;

    private JourneyAdapter adapter;
    private JourneyPlannerEngine plannerEngine;
    private TflJourneyPlannerEngine tflPlannerEngine;

    private StationAutoCompleteAdapter autoCompleteAdapter;
    private ShimmerFrameLayout shimmerView;

    private Calendar selectedCalendar = null;
    private boolean isArriving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        setContentView(R.layout.activity_journey_search);

        etFromCrs = findViewById(R.id.etFromCrs);
        etToCrs = findViewById(R.id.etToCrs);
        ImageButton btnSwap = findViewById(R.id.btnSwapStations);
        Button btnSearch = findViewById(R.id.btnSearchJourney);
        recyclerSearchResults = findViewById(R.id.recyclerSearchResults);
        progressSearch = findViewById(R.id.progressSearch);
        tvStatusUpdate = findViewById(R.id.tvStatusUpdate);
        shimmerView = findViewById(R.id.shimmer_journey_container);

        btnTimeSelector = findViewById(R.id.btnTimeSelector);
        tvTimeSelection = findViewById(R.id.tvTimeSelection);
        btnResetTime = findViewById(R.id.btnResetTime);
        cardFallbackWarning = findViewById(R.id.cardFallbackWarning);

        GraphManager graphManager = GraphManager.getInstance(this);

        plannerEngine = new JourneyPlannerEngine(graphManager);
        tflPlannerEngine = new TflJourneyPlannerEngine();

        setupAdaptersAndListeners(btnSearch);
        setupDateTimePickers();

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
            btnBack.setOnClickListener(v -> finish());
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

        btnSearch.setOnClickListener(v -> handleSearch());
    }

    private void setupDateTimePickers() {
        btnTimeSelector.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, btnTimeSelector);
            popup.getMenu().add(0, 0, 0, "Now");
            popup.getMenu().add(0, 1, 1, "Leaving at...");
            popup.getMenu().add(0, 2, 2, "Arriving by...");

            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 0:
                        selectedCalendar = null;
                        isArriving = false;
                        updateDateTimeUI();
                        break;
                    case 1:
                        isArriving = false;
                        showCustomTimePicker();
                        break;
                    case 2:
                        isArriving = true;
                        showCustomTimePicker();
                        break;
                }
                return true;
            });
            popup.show();
        });

        btnResetTime.setOnClickListener(v -> {
            selectedCalendar = null;
            isArriving = false;
            updateDateTimeUI();
            Toast.makeText(this, "Reset to Leaving now", Toast.LENGTH_SHORT).show();
        });
    }
    private void showCustomTimePicker() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_time_picker);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        TextView tvTitle = dialog.findViewById(R.id.tvPickerTitle);
        tvTitle.setText(isArriving ? "Set arrival time" : "Set departure time");

        NumberPicker pickerDate = dialog.findViewById(R.id.pickerDate);
        NumberPicker pickerHour = dialog.findViewById(R.id.pickerHour);
        NumberPicker pickerMinute = dialog.findViewById(R.id.pickerMinute);

        String[] dateDisplayValues = new String[30];
        Calendar calIter = Calendar.getInstance();
        for (int i = 0; i < 30; i++) {
            if (i == 0) dateDisplayValues[i] = "Today";
            else if (i == 1) dateDisplayValues[i] = "Tomorrow";
            else {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEE, dd MMM", Locale.UK);
                dateDisplayValues[i] = sdf.format(calIter.getTime());
            }
            calIter.add(Calendar.DAY_OF_YEAR, 1);
        }

        pickerDate.setMinValue(0);
        pickerDate.setMaxValue(29);
        pickerDate.setDisplayedValues(dateDisplayValues);

        pickerHour.setMinValue(0);
        pickerHour.setMaxValue(23);

        pickerMinute.setMinValue(0);
        pickerMinute.setMaxValue(59);

        Calendar currentSelection = selectedCalendar != null ? selectedCalendar : Calendar.getInstance();

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);
        Calendar sel = (Calendar) currentSelection.clone();
        sel.set(Calendar.HOUR_OF_DAY, 0); sel.set(Calendar.MINUTE, 0); sel.set(Calendar.SECOND, 0); sel.set(Calendar.MILLISECOND, 0);

        long diff = (sel.getTimeInMillis() - today.getTimeInMillis()) / (1000 * 60 * 60 * 24);
        int dayOffset = Math.max(0, Math.min(29, (int) diff));

        pickerDate.setValue(dayOffset);
        pickerHour.setValue(currentSelection.get(Calendar.HOUR_OF_DAY));
        pickerMinute.setValue(currentSelection.get(Calendar.MINUTE));

        dialog.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btnSet).setOnClickListener(v -> {
            Calendar result = Calendar.getInstance();
            result.add(Calendar.DAY_OF_YEAR, pickerDate.getValue());
            result.set(Calendar.HOUR_OF_DAY, pickerHour.getValue());
            result.set(Calendar.MINUTE, pickerMinute.getValue());

            selectedCalendar = result;
            updateDateTimeUI();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateDateTimeUI() {
        if (selectedCalendar == null) {
            tvTimeSelection.setText("Leaving now");
            btnResetTime.setVisibility(View.GONE);
            cardFallbackWarning.setVisibility(View.GONE);
        } else {
            String prefix = isArriving ? "Arriving " : "Leaving ";
            tvTimeSelection.setText(prefix + formatCalendarFriendly(selectedCalendar));
            btnResetTime.setVisibility(View.VISIBLE);
            cardFallbackWarning.setVisibility(View.VISIBLE);
        }
    }

    private String formatCalendarFriendly(Calendar cal) {
        Calendar today = Calendar.getInstance();
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);

        java.text.SimpleDateFormat timeFmt = new java.text.SimpleDateFormat("HH:mm", Locale.UK);

        if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
            return "today at " + timeFmt.format(cal.getTime());
        } else if (cal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR)) {
            return "tomorrow at " + timeFmt.format(cal.getTime());
        } else {
            java.text.SimpleDateFormat dateFmt = new java.text.SimpleDateFormat("EEE, dd MMM 'at' HH:mm", Locale.UK);
            return dateFmt.format(cal.getTime());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Button btnSearch = findViewById(R.id.btnSearchJourney);
        setupAdaptersAndListeners(btnSearch);
    }

    private void setupAdaptersAndListeners(Button btnSearch) {
        List<String> stations = StationHelper.getStationNames();
        // FIX: Create separate adapters so they don't share search states!
        StationAutoCompleteAdapter fromAdapter = new StationAutoCompleteAdapter(this, stations);
        StationAutoCompleteAdapter toAdapter = new StationAutoCompleteAdapter(this, stations);

        etFromCrs.setAdapter(fromAdapter);
        etToCrs.setAdapter(toAdapter);

        io.github.turtle358.callingpoint.utils.RecentStationsManager recentManager =
                new io.github.turtle358.callingpoint.utils.RecentStationsManager(this);

        etFromCrs.setOnItemClickListener((parent, v, position, id) -> {
            recentManager.addStation(fromAdapter.getItem(position));
            etToCrs.requestFocus();
        });

        etToCrs.setOnItemClickListener((parent, v, position, id) -> {
            recentManager.addStation(toAdapter.getItem(position));
            hideKeyboard();
            if (btnSearch != null) btnSearch.performClick();
        });

        etFromCrs.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) etFromCrs.showDropDown();
        });
        etToCrs.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) etToCrs.showDropDown();
        });

        setupAutoCompleteEnterBehavior(etFromCrs, fromAdapter, etToCrs, null);
        setupAutoCompleteEnterBehavior(etToCrs, toAdapter, null, () -> {
            if (btnSearch != null) btnSearch.performClick();
        });
    }

    private void handleSearch() {
        etFromCrs.dismissDropDown();
        etToCrs.dismissDropDown();
        tvStatusUpdate.setVisibility(View.GONE);
        tvStatusUpdate.setText("");
        shimmerView.stopShimmer();
        shimmerView.setVisibility(View.GONE);

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

        boolean sameName = fromName.equalsIgnoreCase(toName);
        boolean sameCrs = fromCrs != null && !fromCrs.isEmpty() && fromCrs.equalsIgnoreCase(toCrs);
        boolean sameNaptan = fromNaptan != null && !fromNaptan.isEmpty() && fromNaptan.equalsIgnoreCase(toNaptan);

        if (sameName || sameCrs || sameNaptan) {
            Toast.makeText(this, "Origin and Destination cannot be the same", Toast.LENGTH_SHORT).show();
            return;
        }

        hideKeyboard();

        String dateParam = null;
        String timeParam = null;
        String timeIsParam = null;

        if (selectedCalendar != null) {
            java.text.SimpleDateFormat dateFmt = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.UK);
            java.text.SimpleDateFormat timeFmt = new java.text.SimpleDateFormat("HHmm", java.util.Locale.UK);
            dateParam = dateFmt.format(selectedCalendar.getTime());
            timeParam = timeFmt.format(selectedCalendar.getTime());
            timeIsParam = isArriving ? "arriving" : "departing";
        }

        if (fromNaptan != null && !fromNaptan.isEmpty() && toNaptan != null && !toNaptan.isEmpty()) {
            startTflRouting(fromNaptan, toNaptan, fromCrs, toCrs, dateParam, timeParam, timeIsParam);
        } else if (fromCrs != null && toCrs != null && selectedCalendar == null) {
            startInternalRouting(fromCrs, toCrs);
        } else {
            Toast.makeText(this, "National Rail routing is only available for 'Now'. Switch to 'Now' or use TfL stations.", Toast.LENGTH_LONG).show();
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
        if (selectedCalendar == null) {
            Toast.makeText(this, "Maps app unavailable. Using in-app routing...", Toast.LENGTH_LONG).show();
            startInternalRouting(originCrs, destCrs);
        } else {
            Toast.makeText(this, "Maps app unavailable. Internal fallback cannot be used for future times.", Toast.LENGTH_LONG).show();
        }
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

    private void startTflRouting(String fromNaptan, String toNaptan, String fromCrs, String toCrs, String date, String time, String timeIs) {
        recyclerSearchResults.setVisibility(View.GONE);
        tvStatusUpdate.setVisibility(View.VISIBLE);
        tvStatusUpdate.setText("Initialising TfL search...");

        shimmerView.setVisibility(View.VISIBLE);
        shimmerView.startShimmer();

        if (tflPlannerEngine != null) {
            tflPlannerEngine.findRoutes(fromNaptan, toNaptan, date, time, timeIs, new JourneyPlannerEngine.JourneyCallback() {
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
                        if (date == null && fromCrs != null && toCrs != null) {
                            startInternalRouting(fromCrs, toCrs);
                        } else {
                            tvStatusUpdate.setVisibility(View.VISIBLE);
                            tvStatusUpdate.setText("No routes found for the selected time.");
                        }
                    }
                }

                @Override
                public void onError(String message) {
                    if (isDestroyed()) return;
                    if (date == null && fromCrs != null && toCrs != null) {
                        startInternalRouting(fromCrs, toCrs);
                    } else {
                        tvStatusUpdate.setVisibility(View.VISIBLE);
                        tvStatusUpdate.setText("Search failed: " + message);
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