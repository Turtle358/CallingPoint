package io.github.turtle358.callingpoint.ui.activities;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.NumberPicker;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.models.SavedRoute;
import io.github.turtle358.callingpoint.ui.adapters.JourneyAdapter;
import io.github.turtle358.callingpoint.ui.adapters.StationAutoCompleteAdapter;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.GraphManager;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.JourneyPlannerEngine;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.SavedRoutesManager;
import io.github.turtle358.callingpoint.utils.RecentStationsManager;
import io.github.turtle358.callingpoint.utils.StationHelper;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.TflJourneyPlannerEngine;

public class JourneySearchActivity extends AppCompatActivity {

    private AutoCompleteTextView etFromCrs, etToCrs;
    private RecyclerView recyclerSearchResults;
    private TextView tvStatusUpdate, tvTimeSelection;
    private TextView tvTravelOptionsSummary, tvModesSummary;

    private View cardFallbackWarning;
    private View btnTimeSelector;
    private ImageButton btnSaveRoute;

    private JourneyAdapter adapter;
    private JourneyPlannerEngine plannerEngine;
    private TflJourneyPlannerEngine tflPlannerEngine;
    private SavedRoutesManager savedRoutesManager;
    private SharedPreferences journeyPrefs;

    private StationAutoCompleteAdapter autoCompleteAdapter;
    private ShimmerFrameLayout shimmerView;

    private Calendar selectedCalendar = null;
    private boolean isArriving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        setContentView(R.layout.activity_journey_search);

        journeyPrefs = getSharedPreferences("JourneyPrefs", Context.MODE_PRIVATE);

        etFromCrs = findViewById(R.id.etFromCrs);
        etToCrs = findViewById(R.id.etToCrs);
        ImageButton btnSwap = findViewById(R.id.btnSwapStations);
        Button btnSearch = findViewById(R.id.btnSearchJourney);
        recyclerSearchResults = findViewById(R.id.recyclerSearchResults);
        tvStatusUpdate = findViewById(R.id.tvStatusUpdate);
        shimmerView = findViewById(R.id.shimmer_journey_container);

        btnTimeSelector = findViewById(R.id.btnTimeSelector);
        tvTimeSelection = findViewById(R.id.tvTimeSelection);
        cardFallbackWarning = findViewById(R.id.cardFallbackWarning);
        btnSaveRoute = findViewById(R.id.btnSaveRoute);

        tvTravelOptionsSummary = findViewById(R.id.tvTravelOptionsSummary);
        tvModesSummary = findViewById(R.id.tvModesSummary);
        View btnTravelOptions = findViewById(R.id.btnTravelOptions);
        View btnPreferredModes = findViewById(R.id.btnPreferredModes);

        if (btnTravelOptions != null) btnTravelOptions.setOnClickListener(v -> showTravelOptionsDialog());
        if (btnPreferredModes != null) btnPreferredModes.setOnClickListener(v -> showPreferredModesDialog());

        if (tvTravelOptionsSummary != null && tvModesSummary != null) {
            updateSearchOptionsUI();
        }

        GraphManager graphManager = GraphManager.getInstance(this);

        plannerEngine = new JourneyPlannerEngine(graphManager);
        tflPlannerEngine = new TflJourneyPlannerEngine();
        savedRoutesManager = new SavedRoutesManager(this);

        setupAdaptersAndListeners(btnSearch);
        setupDateTimePickers();

        etFromCrs.setOnClickListener(v -> {
            if (etFromCrs.getText().length() > 0) etFromCrs.showDropDown();
        });
        etFromCrs.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && etFromCrs.getText().length() > 0) etFromCrs.showDropDown();
            if (!hasFocus) updateSaveButtonState();
        });

        etToCrs.setOnClickListener(v -> {
            if (etToCrs.getText().length() > 0) etToCrs.showDropDown();
        });
        etToCrs.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && etToCrs.getText().length() > 0) etToCrs.showDropDown();
            if (!hasFocus) updateSaveButtonState();
        });

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                hideKeyboard();
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
            updateSaveButtonState();
        });

        btnSearch.setOnClickListener(v -> handleSearch());

        if (btnSaveRoute != null) {
            btnSaveRoute.setOnClickListener(v -> {
                toggleRouteSavedState();
            });
        }

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("AUTO_FROM") && intent.hasExtra("AUTO_TO")) {
            etFromCrs.setText(intent.getStringExtra("AUTO_FROM"));
            etToCrs.setText(intent.getStringExtra("AUTO_TO"));

            etFromCrs.dismissDropDown();
            etToCrs.dismissDropDown();
            updateSaveButtonState();

            btnSearch.post(this::handleSearch);
        } else {
            etFromCrs.requestFocus();
        }
    }

    private void updateSearchOptionsUI() {
        String pref = journeyPrefs.getString("journeyPreference", "leasttime");
        boolean isAccessible = journeyPrefs.getBoolean("accessibilityRequired", false);

        String routeText = "Fastest route";
        if (pref.equals("leastinterchange")) routeText = "Fewest changes";
        if (pref.equals("leastwalking")) routeText = "Least walking";

        if (isAccessible) routeText += " • Step-free";
        tvTravelOptionsSummary.setText(routeText);

        String modes = journeyPrefs.getString("preferredModes", null);
        if (modes == null || modes.isEmpty()) {
            tvModesSummary.setText("All modes");
        } else {
            boolean hasTrain = modes.contains("national-rail");
            boolean hasTram = modes.contains("tram");
            boolean hasTube = modes.contains("tube");
            boolean hasBus = modes.contains("bus");

            int activeToggles = (hasTrain ? 1 : 0) + (hasTram ? 1 : 0) +
                    (hasTube ? 1 : 0) + (hasBus ? 1 : 0);

            if (activeToggles == 0 || activeToggles == 4) {
                tvModesSummary.setText("All modes");
            } else if (activeToggles == 1) {
                if (hasTrain) tvModesSummary.setText("Train");
                else if (hasTram) tvModesSummary.setText("Tram");
                else if (hasTube) tvModesSummary.setText("Underground");
                else if (hasBus) tvModesSummary.setText("Bus");
            } else {
                tvModesSummary.setText(activeToggles + " selected");
            }
        }
    }

    private void setupDateTimePickers() {
        btnTimeSelector.setOnClickListener(v -> showCustomTimePicker());
    }

    private void showCustomTimePicker() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_time_picker);

        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) bottomSheet.setBackground(new ColorDrawable(Color.TRANSPARENT));

        RadioGroup toggleGroup = dialog.findViewById(R.id.toggleTimeType);
        View slidingPill = dialog.findViewById(R.id.slidingPill);

        if (isArriving) {
            toggleGroup.check(R.id.radioArrivingBy);
        } else {
            toggleGroup.check(R.id.radioLeavingAt);
        }

        toggleGroup.setOnCheckedChangeListener((group, checkedId) -> {
            View selectedButton = group.findViewById(checkedId);
            if (selectedButton != null && slidingPill != null) {
                slidingPill.animate()
                        .translationX(selectedButton.getX())
                        .setDuration(200)
                        .setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                        .start();
            }
        });

        toggleGroup.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (toggleGroup.getWidth() > 0 && slidingPill != null) {
                    toggleGroup.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    View selectedButton = toggleGroup.findViewById(toggleGroup.getCheckedRadioButtonId());
                    if (selectedButton != null) {
                        android.view.ViewGroup.LayoutParams params = slidingPill.getLayoutParams();
                        params.width = selectedButton.getWidth();
                        slidingPill.setLayoutParams(params);
                        slidingPill.setTranslationX(selectedButton.getX());
                    }
                }
            }
        });

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

        dialog.findViewById(R.id.btnResetNow).setOnClickListener(v -> {
            selectedCalendar = null;
            isArriving = false;
            updateDateTimeUI();
            dialog.dismiss();
            Toast.makeText(this, "Reset to Leaving now", Toast.LENGTH_SHORT).show();
        });

        dialog.findViewById(R.id.btnSet).setOnClickListener(v -> {
            isArriving = toggleGroup.getCheckedRadioButtonId() == R.id.radioArrivingBy;

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

    private void showTravelOptionsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_travel_options);

        View bottomSheet = (View) dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) bottomSheet.setBackground(new ColorDrawable(Color.TRANSPARENT));

        SwitchMaterial switchAccessibility = dialog.findViewById(R.id.switchAccessibility);
        RadioGroup radioGroup = dialog.findViewById(R.id.radioGroupPreference);

        switchAccessibility.setChecked(journeyPrefs.getBoolean("accessibilityRequired", false));
        String currentPref = journeyPrefs.getString("journeyPreference", "leasttime");

        if (currentPref.equals("leastinterchange")) radioGroup.check(R.id.radioChanges);
        else if (currentPref.equals("leastwalking")) radioGroup.check(R.id.radioWalking);
        else radioGroup.check(R.id.radioFastest);

        dialog.setOnDismissListener(d -> {
            SharedPreferences.Editor editor = journeyPrefs.edit();
            editor.putBoolean("accessibilityRequired", switchAccessibility.isChecked());

            int selectedId = radioGroup.getCheckedRadioButtonId();
            if (selectedId == R.id.radioChanges) editor.putString("journeyPreference", "leastinterchange");
            else if (selectedId == R.id.radioWalking) editor.putString("journeyPreference", "leastwalking");
            else editor.putString("journeyPreference", "leasttime");

            editor.apply();
            updateSearchOptionsUI();
        });

        dialog.show();
    }

    private void showPreferredModesDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_preferred_modes);

        View bottomSheet = (View) dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) bottomSheet.setBackground(new ColorDrawable(Color.TRANSPARENT));

        SwitchMaterial switchTrain = dialog.findViewById(R.id.switchTrain);
        SwitchMaterial switchTram = dialog.findViewById(R.id.switchTram);
        SwitchMaterial switchTube = dialog.findViewById(R.id.switchTube);
        SwitchMaterial switchBus = dialog.findViewById(R.id.switchBus);

        String currentModes = journeyPrefs.getString("preferredModes", null);
        if (currentModes == null) {
            switchTrain.setChecked(true);
            switchTram.setChecked(true);
            switchTube.setChecked(true);
            switchBus.setChecked(true);
        } else {
            switchTrain.setChecked(currentModes.contains("national-rail"));
            switchTram.setChecked(currentModes.contains("tram"));
            switchTube.setChecked(currentModes.contains("tube"));
            switchBus.setChecked(currentModes.contains("bus"));
        }

        dialog.setOnDismissListener(d -> {
            List<String> selectedModes = new ArrayList<>();
            if (switchTrain.isChecked()) {
                selectedModes.add("national-rail");
                selectedModes.add("overground");
                selectedModes.add("elizabeth-line");
            }
            if (switchTram.isChecked()) selectedModes.add("tram");
            if (switchTube.isChecked()) {
                selectedModes.add("tube");
                selectedModes.add("dlr");
            }
            if (switchBus.isChecked()) selectedModes.add("bus");

            SharedPreferences.Editor editor = journeyPrefs.edit();

            if (selectedModes.size() >= 6 || selectedModes.isEmpty()) {
                editor.putString("preferredModes", null);
            } else {
                editor.putString("preferredModes", TextUtils.join(",", selectedModes));
            }

            editor.apply();
            updateSearchOptionsUI();
        });

        dialog.show();
    }

    private void toggleRouteSavedState() {
        String fromName = etFromCrs.getText().toString().trim();
        String toName = etToCrs.getText().toString().trim();

        if (fromName.isEmpty() || toName.isEmpty() || fromName.equalsIgnoreCase(toName)) {
            Toast.makeText(this, "Enter valid, distinct stations to save", Toast.LENGTH_SHORT).show();
            return;
        }

        if (savedRoutesManager.isRouteSaved(fromName, toName)) {
            savedRoutesManager.removeRoute(fromName, toName);
            Toast.makeText(this, "Saved route removed", Toast.LENGTH_SHORT).show();
        } else {
            String fromCrs = StationHelper.getCrsFromName(fromName);
            String toCrs = StationHelper.getCrsFromName(toName);
            String fromNaptan = StationHelper.getNaptanFromName(fromName);
            String toNaptan = StationHelper.getNaptanFromName(toName);

            SavedRoute route = new SavedRoute(fromName, toName, fromCrs, toCrs, fromNaptan, toNaptan);
            savedRoutesManager.saveRoute(route);
            Toast.makeText(this, "Route saved!", Toast.LENGTH_SHORT).show();
        }
        updateSaveButtonState();
    }

    private void updateSaveButtonState() {
        if (btnSaveRoute == null) return;

        String fromName = etFromCrs.getText().toString().trim();
        String toName = etToCrs.getText().toString().trim();

        boolean isSaved = !fromName.isEmpty() && !toName.isEmpty() &&
                !fromName.equalsIgnoreCase(toName) &&
                savedRoutesManager.isRouteSaved(fromName, toName);

        if (isSaved) {
            btnSaveRoute.setImageResource(R.drawable.ic_bookmark);
        } else {
            btnSaveRoute.setImageResource(R.drawable.ic_bookmark_outline);
        }

        btnSaveRoute.setColorFilter(
                androidx.core.content.ContextCompat.getColor(this, R.color.brand_primary)
        );
    }

    private void updateDateTimeUI() {
        if (selectedCalendar == null) {
            tvTimeSelection.setText("Leaving now");
            cardFallbackWarning.setVisibility(View.GONE);
        } else {
            String prefix = isArriving ? "Arriving " : "Leaving ";
            tvTimeSelection.setText(prefix + formatCalendarFriendly(selectedCalendar));
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
        updateSaveButtonState();
    }

    private void setupAdaptersAndListeners(Button btnSearch) {
        List<String> stations = StationHelper.getStationNames();
        StationAutoCompleteAdapter fromAdapter = new StationAutoCompleteAdapter(this, stations);
        StationAutoCompleteAdapter toAdapter = new StationAutoCompleteAdapter(this, stations);

        etFromCrs.setAdapter(fromAdapter);
        etToCrs.setAdapter(toAdapter);

        RecentStationsManager recentManager = new RecentStationsManager(this);

        etFromCrs.setOnItemClickListener((parent, v, position, id) -> {
            recentManager.addStation(fromAdapter.getItem(position));
            updateSaveButtonState();
            etToCrs.requestFocus();
        });

        etToCrs.setOnItemClickListener((parent, v, position, id) -> {
            recentManager.addStation(toAdapter.getItem(position));
            updateSaveButtonState();
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
        updateSaveButtonState();

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

        String journeyPref = journeyPrefs.getString("journeyPreference", "leasttime");
        String modes = journeyPrefs.getString("preferredModes", null);
        boolean requiresAccessibility = journeyPrefs.getBoolean("accessibilityRequired", false);
        String accessPref = requiresAccessibility ? "noSolidStairs" : null;

        if (tflPlannerEngine != null) {
            tflPlannerEngine.findRoutes(fromNaptan, toNaptan, date, time, timeIs, journeyPref, modes, accessPref, new JourneyPlannerEngine.JourneyCallback() {
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

                updateSaveButtonState();

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