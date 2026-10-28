package io.github.turtle358.callingpoint.ui.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import com.google.gson.Gson;
import io.github.turtle358.callingpoint.utils.JourneySavedPlaces;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
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
import com.google.android.material.switchmaterial.SwitchMaterial;


import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.models.SavedRoute;
import io.github.turtle358.callingpoint.ui.adapters.JourneyAdapter;
import io.github.turtle358.callingpoint.models.JourneyLocation;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.GraphManager;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.JourneyPlannerEngine;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.SavedRoutesManager;
import io.github.turtle358.callingpoint.utils.StationHelper;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.TflJourneyPlannerEngine;

public class JourneySearchActivity extends AppCompatActivity {

    private AutoCompleteTextView etFromCrs, etToCrs;
    private RecyclerView recyclerSearchResults;
    private TextView tvTimeSelection;
    private TextView tvTravelOptionsSummary, tvModesSummary;

    private View cardFallbackWarning;
    private TextView cardTextFallbackWarning;
    private View btnTimeSelector;
    private ImageButton btnSaveRoute;

    private JourneyAdapter adapter;
    private JourneyPlannerEngine plannerEngine;
    private TflJourneyPlannerEngine tflPlannerEngine;
    private SavedRoutesManager savedRoutesManager;
    private SharedPreferences journeyPrefs;

    private JourneyLocationPickerController locationPicker;
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
        shimmerView = findViewById(R.id.shimmer_journey_container);

        btnTimeSelector = findViewById(R.id.btnTimeSelector);
        tvTimeSelection = findViewById(R.id.tvTimeSelection);
        cardFallbackWarning = findViewById(R.id.cardFallbackWarning);
        cardTextFallbackWarning = findViewById(R.id.cardTextFallbackWarning);
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

        locationPicker = new JourneyLocationPickerController(this, StationHelper.getStationNames());
        locationPicker.setOnSelectionChanged(this::updateSaveButtonState);
        setupDateTimePickers();

        etFromCrs.setOnEditorActionListener((v, actionId, event) -> {
            if (locationPicker.getFrom() != null) {
                etToCrs.requestFocus();
            } else {
                Toast.makeText(this, "Choose a suggested starting point", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        etToCrs.setOnEditorActionListener((v, actionId, event) -> {
            if (locationPicker.hasBothLocations()) handleSearch();
            else Toast.makeText(this, "Choose both locations from the suggestions", Toast.LENGTH_SHORT).show();
            return true;
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
            locationPicker.swap();
            updateSaveButtonState();
        });

        btnSearch.setOnClickListener(v -> handleSearch());

        if (btnSaveRoute != null) {
            btnSaveRoute.setOnClickListener(v -> {
                toggleRouteSavedState();
            });
        }

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("AUTO_SAVED_ROUTE_JSON")) {
            try {
                SavedRoute route = new Gson().fromJson(
                        intent.getStringExtra("AUTO_SAVED_ROUTE_JSON"), SavedRoute.class);
                if (route == null) throw new IllegalArgumentException("Missing saved route");
                restoreSavedRoute(route, btnSearch);
            } catch (RuntimeException e) {
                Toast.makeText(this, "Unable to open saved route", Toast.LENGTH_LONG).show();
            }
        } else if (intent != null && intent.hasExtra("AUTO_FROM") && intent.hasExtra("AUTO_TO")) {
            String from = intent.getStringExtra("AUTO_FROM");
            String to = intent.getStringExtra("AUTO_TO");
            if (from != null && to != null && !from.trim().isEmpty() && !to.trim().isEmpty()) {
                locationPicker.setFrom(JourneyLocation.station(from, StationHelper.getNaptanFromName(from)));
                locationPicker.setTo(JourneyLocation.station(to, StationHelper.getNaptanFromName(to)));
                updateSaveButtonState();
                btnSearch.post(this::handleSearch);
            }
        } else {
            etFromCrs.post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    etFromCrs.requestFocus();
                }
            });
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
    private boolean canSaveRoute() {
        if (locationPicker == null || !locationPicker.hasBothLocations()) return false;
        JourneyLocation from = locationPicker.getFrom();
        JourneyLocation to = locationPicker.getTo();
        if (from.stableKey().equals(to.stableKey())) return false;
        String fs = locationPicker.getFromSavedSlot();
        String ts = locationPicker.getToSavedSlot();
        if (fs != null && fs.equals(ts)) return false;
        return true;
    }

    @androidx.annotation.NonNull
    private SavedRoute buildSavedRoute() {
        JourneyLocation from = locationPicker.getFrom();
        JourneyLocation to = locationPicker.getTo();
        String fromSource = bookmarkSource(from, locationPicker.getFromSavedSlot());
        String toSource = bookmarkSource(to, locationPicker.getToSavedSlot());
        JourneyLocation persistedFrom = "FIXED".equals(fromSource) ? from : null;
        JourneyLocation persistedTo = "FIXED".equals(toSource) ? to : null;
        String fromName = bookmarkName(from, fromSource);
        String toName = bookmarkName(to, toSource);
        String fromCrs = from.type == JourneyLocation.Type.STATION ? StationHelper.getCrsFromName(from.displayName()) : null;
        String toCrs = to.type == JourneyLocation.Type.STATION ? StationHelper.getCrsFromName(to.displayName()) : null;
        String fromNaptan = from.type == JourneyLocation.Type.STATION ? from.stationId : null;
        String toNaptan = to.type == JourneyLocation.Type.STATION ? to.stationId : null;
        return new SavedRoute(fromName, toName, fromCrs, toCrs,
                fromNaptan, toNaptan, persistedFrom, persistedTo, fromSource, toSource);
    }

    private static String bookmarkSource(JourneyLocation location, String savedSlot) {
        if (location.type == JourneyLocation.Type.CURRENT_LOCATION) return "CURRENT";
        if (JourneySavedPlaces.HOME.equals(savedSlot)) return "HOME";
        if (JourneySavedPlaces.WORK.equals(savedSlot)) return "WORK";
        return "FIXED";
    }

    private static String bookmarkName(JourneyLocation location, String source) {
        switch (source) {
            case "CURRENT": return "Current location";
            case "HOME": return "Home";
            case "WORK": return "Work";
            default: return location.displayName();
        }
    }

    private void toggleRouteSavedState() {
        if (!canSaveRoute()) {
            Toast.makeText(this, "Select two different locations to save a route", Toast.LENGTH_SHORT).show();
            return;
        }
        SavedRoute route = buildSavedRoute();
        if (savedRoutesManager.isRouteSaved(route)) {
            savedRoutesManager.removeRoute(route);
            Toast.makeText(this, "Saved route removed", Toast.LENGTH_SHORT).show();
        } else {
            savedRoutesManager.saveRoute(route);
            Toast.makeText(this, "Route saved!", Toast.LENGTH_SHORT).show();
        }
        updateSaveButtonState();
    }

    private void updateSaveButtonState() {
        if (btnSaveRoute == null || savedRoutesManager == null || locationPicker == null) return;
        boolean valid = canSaveRoute();
        btnSaveRoute.setEnabled(valid);
        btnSaveRoute.setAlpha(valid ? 1f : 0.4f);
        btnSaveRoute.setContentDescription(valid ? "Save or remove route" : "Select two different locations");
        boolean isSaved = valid && savedRoutesManager.isRouteSaved(buildSavedRoute());
        btnSaveRoute.setImageResource(isSaved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
        btnSaveRoute.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.brand_primary));
    }

    private void restoreSavedRoute(SavedRoute route, Button btnSearch) {
        JourneySavedPlaces places = new JourneySavedPlaces(this);
        JourneyLocation from = endpointForSavedRoute(route.getFromSource(), route.getFromLocation(),
                route.getFromName(), route.getFromNaptan(), places);
        JourneyLocation to = endpointForSavedRoute(route.getToSource(), route.getToLocation(),
                route.getToName(), route.getToNaptan(), places);
        boolean fromCurrent = "CURRENT".equalsIgnoreCase(route.getFromSource());
        boolean toCurrent = "CURRENT".equalsIgnoreCase(route.getToSource());
        if (fromCurrent && toCurrent) {
            Toast.makeText(this, "Both ends cannot be Current location", Toast.LENGTH_LONG).show();
            return;
        }
        if ((!fromCurrent && from == null) || (!toCurrent && to == null)) {
            Toast.makeText(this, "Set Home or Work again before using this saved route", Toast.LENGTH_LONG).show();
            return;
        }
        if (from != null) locationPicker.setFrom(from, slotForSource(route.getFromSource()));
        if (to != null) locationPicker.setTo(to, slotForSource(route.getToSource()));
        updateSaveButtonState();
        if (fromCurrent || toCurrent) {
            locationPicker.restoreCurrentLocation(fromCurrent, () -> btnSearch.post(this::handleSearch));
        } else {
            btnSearch.post(this::handleSearch);
        }
    }

    private static String slotForSource(String source) {
        if ("HOME".equalsIgnoreCase(source)) return JourneySavedPlaces.HOME;
        if ("WORK".equalsIgnoreCase(source)) return JourneySavedPlaces.WORK;
        return null;
    }

    @androidx.annotation.Nullable
    private static JourneyLocation endpointForSavedRoute(String source, JourneyLocation fixed,
                                                         String name, String naptan,
                                                         JourneySavedPlaces places) {
        if ("CURRENT".equalsIgnoreCase(source)) return null;
        if ("HOME".equalsIgnoreCase(source)) return places.get(JourneySavedPlaces.HOME);
        if ("WORK".equalsIgnoreCase(source)) return places.get(JourneySavedPlaces.WORK);
        if (fixed != null) return fixed;
        return JourneyLocation.station(name, naptan);
    }

    private void updateDateTimeUI() {
        if (selectedCalendar == null) {
            tvTimeSelection.setText("Leaving now");
            fadeOutWarningCard();
        } else {
            String prefix = isArriving ? "Arriving " : "Leaving ";
            tvTimeSelection.setText(prefix + formatCalendarFriendly(selectedCalendar));
            cardTextFallbackWarning.setText("Internal fallback routing is unavailable for future dates. Results will rely strictly on TfL.");
            fadeInWarningCard();
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
        updateSaveButtonState();
    }

    private void handleSearch() {
        if (locationPicker == null || !locationPicker.hasBothLocations()) {
            Toast.makeText(this, "Select an origin and destination from the suggestions", Toast.LENGTH_SHORT).show();
            return;
        }
        JourneyLocation from = locationPicker.getFrom();
        JourneyLocation to = locationPicker.getTo();
        if (from.stableKey().equals(to.stableKey())
                || (from.type == JourneyLocation.Type.STATION && to.type == JourneyLocation.Type.STATION
                && from.displayName().equalsIgnoreCase(to.displayName()))) {
            Toast.makeText(this, "Origin and destination cannot be the same", Toast.LENGTH_SHORT).show();
            return;
        }
        final String fromTfl;
        final String toTfl;
        try {
            fromTfl = from.tflValue();
            toTfl = to.tflValue();
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, "Invalid journey location", Toast.LENGTH_SHORT).show();
            return;
        }

        locationPicker.close();
        hideKeyboard();
        shimmerView.stopShimmer();
        shimmerView.setVisibility(View.GONE);
        updateSaveButtonState();

        boolean bothStations = from.type == JourneyLocation.Type.STATION
                && to.type == JourneyLocation.Type.STATION;
        String fromCrs = bothStations ? StationHelper.getCrsFromName(from.displayName()) : null;
        String toCrs = bothStations ? StationHelper.getCrsFromName(to.displayName()) : null;
        String fromNaptan = bothStations ? (from.stationId != null ? from.stationId
                : StationHelper.getNaptanFromName(from.displayName())) : null;
        String toNaptan = bothStations ? (to.stationId != null ? to.stationId
                : StationHelper.getNaptanFromName(to.displayName())) : null;

        String dateParam = null;
        String timeParam = null;
        String timeIsParam = null;
        if (selectedCalendar != null) {
            java.text.SimpleDateFormat dateFmt = new java.text.SimpleDateFormat("yyyyMMdd", Locale.UK);
            java.text.SimpleDateFormat timeFmt = new java.text.SimpleDateFormat("HHmm", Locale.UK);
            dateParam = dateFmt.format(selectedCalendar.getTime());
            timeParam = timeFmt.format(selectedCalendar.getTime());
            timeIsParam = isArriving ? "arriving" : "departing";
        }

        if (!bothStations) {
            startTflRouting(fromTfl, toTfl, null, null, dateParam, timeParam, timeIsParam);
        } else if (fromNaptan != null && !fromNaptan.isEmpty()
                && toNaptan != null && !toNaptan.isEmpty()) {
            startTflRouting(fromNaptan, toNaptan, fromCrs, toCrs, dateParam, timeParam, timeIsParam);
        } else if (fromCrs != null && toCrs != null && selectedCalendar == null) {
            startInternalRouting(fromCrs, toCrs);
        } else {
            Toast.makeText(this, "National Rail-only routing is available for 'Now'. For a future journey, choose TfL-supported stops.", Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("SetTextI18n")
    private void startInternalRouting(String fromCrs, String toCrs) {
        recyclerSearchResults.setVisibility(View.GONE);

        shimmerView.setVisibility(View.VISIBLE);
        shimmerView.startShimmer();

        if (plannerEngine != null) {
            findRoutes(fromCrs, toCrs);
        } else {
            cardTextFallbackWarning.setText("Routing engine unavailable. Check your connection.");
            fadeInWarningCard();
            shimmerView.stopShimmer();
            shimmerView.setVisibility(View.GONE);
        }
    }

    private void startTflRouting(String fromNaptan, String toNaptan, String fromCrs, String toCrs, String date, String time, String timeIs) {
        fadeOutWarningCard();
        recyclerSearchResults.setVisibility(View.GONE);

        shimmerView.setVisibility(View.VISIBLE);
        shimmerView.startShimmer();

        String journeyPref = journeyPrefs.getString("journeyPreference", "leasttime");
        String modes = journeyPrefs.getString("preferredModes", null);
        boolean requiresAccessibility = journeyPrefs.getBoolean("accessibilityRequired", false);
        String accessPref = requiresAccessibility ? "noSolidStairs" : null;

        if (tflPlannerEngine != null) {
            tflPlannerEngine.findRoutes(fromNaptan, toNaptan, date, time, timeIs, journeyPref, modes, accessPref, new JourneyPlannerEngine.JourneyCallback() {
                @SuppressLint("SetTextI18n")
                @Override
                public void onJourneysFound(List<JourneyOption> journeys) {
                    if (isDestroyed()) return;
                    shimmerView.stopShimmer();
                    shimmerView.setVisibility(View.GONE);

                    if (journeys != null && !journeys.isEmpty()) {
                        adapter.updateData(journeys);
                        recyclerSearchResults.setVisibility(View.VISIBLE);
                    } else {
                        if (date == null && fromCrs != null && toCrs != null) {
                            startInternalRouting(fromCrs, toCrs);
                        } else {
                            cardTextFallbackWarning.setText("No routes found for the selected time.");
                            fadeInWarningCard();
                        }
                    }
                }

                @SuppressLint("SetTextI18n")
                @Override
                public void onError(String message) {
                    if (isDestroyed()) return;
                    if (date == null && fromCrs != null && toCrs != null) {
                        cardTextFallbackWarning.setText("TfL routing is not available right now, falling back to internal routing\n This may take a moment");
                        fadeInWarningCard();

                        startInternalRouting(fromCrs, toCrs);
                    } else {
                        cardTextFallbackWarning.setText("Search failed: " + message);
                        fadeInWarningCard();
                        shimmerView.stopShimmer();
                        shimmerView.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onStatusUpdate(String message) {
                    if (isDestroyed()) return;
                    cardTextFallbackWarning.setText(message);
                    fadeInWarningCard();
                }
            });
        }
    }

    private void findRoutes(String fromCrs, String toCrs) {
        plannerEngine.findRoutes(fromCrs, toCrs, new JourneyPlannerEngine.JourneyCallback() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onJourneysFound(List<JourneyOption> journeys) {
                if (isDestroyed()) return;
                shimmerView.stopShimmer();
                shimmerView.setVisibility(View.GONE);

                if (journeys != null && !journeys.isEmpty()) {
                    adapter.updateData(journeys);
                    recyclerSearchResults.setVisibility(View.VISIBLE);
                } else {
                    cardTextFallbackWarning.setText("No routes found between these stations.");
                    fadeInWarningCard();
                }
            }

            @Override
            public void onError(String message) {
                if (isDestroyed()) return;
                cardTextFallbackWarning.setText(message);
                fadeInWarningCard();
                shimmerView.stopShimmer();
                shimmerView.setVisibility(View.GONE);
            }

            @Override
            public void onStatusUpdate(String message) {
                if (isDestroyed()) return;
                cardTextFallbackWarning.setText(message);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (plannerEngine != null) plannerEngine.shutdown();
        if (tflPlannerEngine != null) tflPlannerEngine.shutdown();
        if (locationPicker != null) locationPicker.destroy();
    }

    @Override
    public void onBackPressed() {
        if (locationPicker != null && locationPicker.isOpen()) {
            locationPicker.close();
            return;
        }
        super.onBackPressed();
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void fadeInWarningCard() {
        if (cardFallbackWarning.getVisibility() == View.VISIBLE && cardFallbackWarning.getAlpha() == 1f) return;

        cardFallbackWarning.setAlpha(0f);
        cardFallbackWarning.setVisibility(View.VISIBLE);
        cardFallbackWarning.animate()
                .alpha(1f)
                .setDuration(300)
                .setListener(null);
    }

    private void fadeOutWarningCard() {
        if (cardFallbackWarning.getVisibility() == View.GONE) return;

        cardFallbackWarning.animate()
                .alpha(0f)
                .setDuration(300)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        cardFallbackWarning.setVisibility(View.GONE);
                    }
                });
    }
}