package io.github.turtle358.callingpoint.ui.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.turtle358.callingpoint.utils.JourneySavedPlaces;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.GeoapifyAutocompleteAPI;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
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

    public static final String EXTRA_EDIT_SAVED_PLACE = "EDIT_SAVED_PLACE";
    private boolean savedPlaceEditMode;

    private AutoCompleteTextView etFromCrs, etToCrs;
    private RecyclerView recyclerSearchResults;
    private TextView tvTimeSelection;
    private TextView tvTravelOptionsSummary;
    private TextView tvSearchHint, tvResultsHeading;

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
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable pendingAutoSearch = this::searchIfChanged;
    private boolean automaticSearchReady = false;
    private String lastSearchSignature;
    private int searchVersion = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        setContentView(R.layout.activity_journey_search);

        journeyPrefs = getSharedPreferences("JourneyPrefs", Context.MODE_PRIVATE);
        etFromCrs = findViewById(R.id.etFromCrs);
        etToCrs = findViewById(R.id.etToCrs);
        ImageButton btnSwap = findViewById(R.id.btnSwapStations);
        recyclerSearchResults = findViewById(R.id.recyclerSearchResults);
        shimmerView = findViewById(R.id.shimmer_journey_container);
        btnTimeSelector = findViewById(R.id.btnTimeSelector);
        tvTimeSelection = findViewById(R.id.tvTimeSelection);
        cardFallbackWarning = findViewById(R.id.cardFallbackWarning);
        cardTextFallbackWarning = findViewById(R.id.cardTextFallbackWarning);
        btnSaveRoute = findViewById(R.id.btnSaveRoute);
        tvTravelOptionsSummary = findViewById(R.id.tvTravelOptionsSummary);
        tvSearchHint = findViewById(R.id.tvJourneySearchHint);
        tvResultsHeading = findViewById(R.id.tvJourneyResultsHeading);

        findViewById(R.id.btnTravelOptions).setOnClickListener(v -> showJourneyOptionsDialog());
        cardFallbackWarning.setOnClickListener(v -> {
            if (locationPicker != null && locationPicker.hasBothLocations()) handleSearch();
        });
        updateSearchOptionsUI();

        GraphManager graphManager = GraphManager.getInstance(this);
        plannerEngine = new JourneyPlannerEngine(graphManager);
        tflPlannerEngine = new TflJourneyPlannerEngine();
        savedRoutesManager = new SavedRoutesManager(this);

        locationPicker = new JourneyLocationPickerController(this, StationHelper.getStationNames());
        locationPicker.setOnSelectionChanged(this::onLocationsChanged);
        setupDateTimePickers();

        etFromCrs.setOnEditorActionListener((v, actionId, event) -> {
            if (savedPlaceEditMode) {
                Toast.makeText(this, "Select a location from the suggestions", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (locationPicker.getFrom() != null) etToCrs.requestFocus();
            else Toast.makeText(this, "Choose a starting point from the suggestions", Toast.LENGTH_SHORT).show();
            return true;
        });
        etToCrs.setOnEditorActionListener((v, actionId, event) -> {
            if (locationPicker.hasBothLocations()) handleSearch();
            else Toast.makeText(this, "Choose a location from the suggestions", Toast.LENGTH_SHORT).show();
            return true;
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            hideKeyboard();
            finish();
        });

        recyclerSearchResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new JourneyAdapter(new ArrayList<>(), route -> {
            Intent detail = new Intent(this, JourneyDetailsActivity.class);
            detail.putExtra("JOURNEY_OPTION", route);
            startActivity(detail);
        });
        recyclerSearchResults.setAdapter(adapter);

        btnSwap.setOnClickListener(v -> locationPicker.swap());
        btnSaveRoute.setOnClickListener(v -> toggleRouteSavedState());

        Intent intent = getIntent();
        String editingPlace = intent == null ? null : intent.getStringExtra(EXTRA_EDIT_SAVED_PLACE);
        if (JourneySavedPlaces.HOME.equals(editingPlace) || JourneySavedPlaces.WORK.equals(editingPlace)) {
            configureSavedPlaceEditor(editingPlace);
            return;
        }
        if (intent != null && intent.hasExtra("AUTO_SAVED_ROUTE_JSON")) {
            try {
                SavedRoute route = new Gson().fromJson(
                        intent.getStringExtra("AUTO_SAVED_ROUTE_JSON"), SavedRoute.class);
                if (route == null) throw new IllegalArgumentException("Missing saved route");
                restoreSavedRoute(route);
            } catch (RuntimeException e) {
                Toast.makeText(this, "Unable to open saved route", Toast.LENGTH_LONG).show();
            }
        } else if (intent != null && intent.hasExtra("AUTO_FROM") && intent.hasExtra("AUTO_TO")) {
            String from = intent.getStringExtra("AUTO_FROM");
            String to = intent.getStringExtra("AUTO_TO");
            if (from != null && to != null && !from.trim().isEmpty() && !to.trim().isEmpty()) {
                locationPicker.setFrom(JourneyLocation.station(from, StationHelper.getNaptanFromName(from)));
                locationPicker.setTo(JourneyLocation.station(to, StationHelper.getNaptanFromName(to)));
            }
        } else {
            locationPicker.prefillCurrentOriginIfPermitted();
            etToCrs.post(() -> {
                if (!isFinishing() && !isDestroyed() && locationPicker.getTo() == null) {
                    etToCrs.requestFocus();
                }
            });
        }

        automaticSearchReady = true;
        updateSaveButtonState();
        scheduleAutomaticSearch();
    }

    private void configureSavedPlaceEditor(@androidx.annotation.NonNull String slot) {
        savedPlaceEditMode = true;
        boolean home = JourneySavedPlaces.HOME.equals(slot);
        etFromCrs.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        ((TextView) findViewById(R.id.tvJourneySearchTitle)).setText(home ? "Set Home" : "Set Work");
        btnSaveRoute.setVisibility(View.GONE);
        findViewById(R.id.layoutJourneyEndpointMarker).setVisibility(View.GONE);
        findViewById(R.id.dividerJourneyEndpoints).setVisibility(View.GONE);
        findViewById(R.id.tilToCrs).setVisibility(View.GONE);
        findViewById(R.id.btnSwapStations).setVisibility(View.GONE);
        findViewById(R.id.layoutJourneySearchOptions).setVisibility(View.GONE);
        tvResultsHeading.setVisibility(View.GONE);
        tvSearchHint.setVisibility(View.GONE);
        recyclerSearchResults.setVisibility(View.GONE);
        shimmerView.setVisibility(View.GONE);
        cardFallbackWarning.setVisibility(View.GONE);
        findViewById(R.id.tvSavedPlaceEditorHint).setVisibility(View.VISIBLE);
        automaticSearchReady = false;
        locationPicker.beginSavedPlaceEditor(slot, () -> {
            setResult(RESULT_OK);
            hideKeyboard();
            finish();
        });
    }

    private void updateSearchOptionsUI() {
        if (tvTravelOptionsSummary == null) return;
        String preference = journeyPrefs.getString("journeyPreference", "leasttime");
        boolean stepFree = journeyPrefs.getBoolean("accessibilityRequired", false);
        Set<String> selectedModes = parsePreferredModes(
                journeyPrefs.getString("preferredModes", null));

        StringBuilder label = new StringBuilder("Options");
        if ("leastinterchange".equals(preference)) label.append(" · Fewest changes");
        else if ("leastwalking".equals(preference)) label.append(" · Least walking");
        if (stepFree) label.append(" · Step-free");

        boolean train = hasTrainMode(selectedModes);
        boolean underground = hasUndergroundMode(selectedModes);
        boolean bus = selectedModes.contains("bus");
        boolean tram = selectedModes.contains("tram");
        int selectedCount = (train ? 1 : 0) + (underground ? 1 : 0)
                + (bus ? 1 : 0) + (tram ? 1 : 0);
        boolean hasOtherMode = false;
        for (String mode : selectedModes) {
            if (!("national-rail".equals(mode) || "overground".equals(mode)
                    || "elizabeth-line".equals(mode) || "tube".equals(mode)
                    || "dlr".equals(mode) || "bus".equals(mode) || "tram".equals(mode))) {
                hasOtherMode = true;
                break;
            }
        }
        if (selectedCount == 1 && !hasOtherMode) {
            label.append(" · ").append(train ? "Train"
                    : underground ? "Underground" : bus ? "Bus" : "Tram");
        } else if (!selectedModes.isEmpty()) {
            label.append(" · Modes");
        }
        tvTravelOptionsSummary.setText(label.toString());
    }

    private static Set<String> parsePreferredModes(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value != null) {
            for (String token : value.split(",")) {
                if (!token.trim().isEmpty()) result.add(token.trim().toLowerCase(Locale.UK));
            }
        }
        return result;
    }

    private static boolean hasTrainMode(Set<String> modes) {
        return modes.contains("national-rail") || modes.contains("overground")
                || modes.contains("elizabeth-line");
    }

    private static boolean hasUndergroundMode(Set<String> modes) {
        return modes.contains("tube") || modes.contains("dlr");
    }

    private void onLocationsChanged() {
        updateSaveButtonState();
        if (!automaticSearchReady) return;
        if (!locationPicker.hasBothLocations()) {
            searchHandler.removeCallbacks(pendingAutoSearch);
            searchVersion++;
            lastSearchSignature = null;
            if (adapter != null) adapter.updateData(new ArrayList<>());
            recyclerSearchResults.setVisibility(View.GONE);
            shimmerView.stopShimmer();
            shimmerView.setVisibility(View.GONE);
            tvResultsHeading.setVisibility(View.GONE);
            tvSearchHint.setText(locationPicker.getTo() == null
                    ? "Choose a destination to see journeys"
                    : "Choose a starting point to see journeys");
            tvSearchHint.setVisibility(View.VISIBLE);
            fadeOutWarningCard();
        } else {
            scheduleAutomaticSearch();
        }
    }

    private void scheduleAutomaticSearch() {
        if (!automaticSearchReady || locationPicker == null || !locationPicker.hasBothLocations()) return;
        searchHandler.removeCallbacks(pendingAutoSearch);
        searchHandler.postDelayed(pendingAutoSearch, 180);
    }

    private void searchIfChanged() {
        if (isFinishing() || isDestroyed() || !locationPicker.hasBothLocations()) return;
        String signature = searchSignature();
        if (signature.equals(lastSearchSignature)) return;
        handleSearch();
    }

    private String searchSignature() {
        JourneyLocation from = locationPicker.getFrom();
        JourneyLocation to = locationPicker.getTo();
        if (from == null || to == null) return "";
        return from.stableKey() + "|" + to.stableKey() + "|"
                + (selectedCalendar == null ? "now" : selectedCalendar.getTimeInMillis()) + "|"
                + isArriving + "|"
                + journeyPrefs.getString("journeyPreference", "leasttime") + "|"
                + journeyPrefs.getString("preferredModes", "all") + "|"
                + journeyPrefs.getBoolean("accessibilityRequired", false);
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
            scheduleAutomaticSearch();
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
            scheduleAutomaticSearch();
        });

        dialog.show();
    }

    private void showJourneyOptionsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_journey_search_options);
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) bottomSheet.setBackground(new ColorDrawable(Color.TRANSPARENT));

        SwitchMaterial accessibility = dialog.findViewById(R.id.switchAccessibility);
        RadioGroup preferences = dialog.findViewById(R.id.radioGroupPreference);
        SwitchMaterial train = dialog.findViewById(R.id.switchTrain);
        SwitchMaterial tram = dialog.findViewById(R.id.switchTram);
        SwitchMaterial tube = dialog.findViewById(R.id.switchTube);
        SwitchMaterial bus = dialog.findViewById(R.id.switchBus);

        accessibility.setChecked(journeyPrefs.getBoolean("accessibilityRequired", false));
        String currentPref = journeyPrefs.getString("journeyPreference", "leasttime");
        if ("leastinterchange".equals(currentPref)) preferences.check(R.id.radioChanges);
        else if ("leastwalking".equals(currentPref)) preferences.check(R.id.radioWalking);
        else preferences.check(R.id.radioFastest);

        Set<String> selectedModes = parsePreferredModes(
                journeyPrefs.getString("preferredModes", null));
        train.setChecked(hasTrainMode(selectedModes));
        tube.setChecked(hasUndergroundMode(selectedModes));
        bus.setChecked(selectedModes.contains("bus"));
        tram.setChecked(selectedModes.contains("tram"));

        dialog.setOnDismissListener(ignored -> {
            SharedPreferences.Editor editor = journeyPrefs.edit();
            editor.putBoolean("accessibilityRequired", accessibility.isChecked());
            int selected = preferences.getCheckedRadioButtonId();
            if (selected == R.id.radioChanges) editor.putString("journeyPreference", "leastinterchange");
            else if (selected == R.id.radioWalking) editor.putString("journeyPreference", "leastwalking");
            else editor.putString("journeyPreference", "leasttime");

            List<String> modes = new ArrayList<>();
            if (train.isChecked()) {
                modes.add("national-rail");
                modes.add("overground");
                modes.add("elizabeth-line");
            }
            if (tram.isChecked()) modes.add("tram");
            if (tube.isChecked()) {
                modes.add("tube");
                modes.add("dlr");
            }
            if (bus.isChecked()) modes.add("bus");

            if (modes.isEmpty()) editor.remove("preferredModes");
            else editor.putString("preferredModes", TextUtils.join(",", modes));
            editor.apply();
            updateSearchOptionsUI();
            scheduleAutomaticSearch();
        });
        dialog.findViewById(R.id.btnDoneJourneyOptions).setOnClickListener(v -> dialog.dismiss());
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
        if (savedPlaceEditMode) {
            if (btnSaveRoute != null) btnSaveRoute.setVisibility(View.GONE);
            return;
        }
        if (btnSaveRoute == null || savedRoutesManager == null || locationPicker == null) return;
        boolean valid = canSaveRoute();
        btnSaveRoute.setEnabled(valid);
        btnSaveRoute.setVisibility(valid ? View.VISIBLE : View.INVISIBLE);
        btnSaveRoute.setContentDescription(valid ? "Save or remove route" : "Select two different locations");
        boolean isSaved = valid && savedRoutesManager.isRouteSaved(buildSavedRoute());
        btnSaveRoute.setImageResource(isSaved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
        btnSaveRoute.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.brand_primary));
    }

    private void restoreSavedRoute(SavedRoute route) {
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
            locationPicker.restoreCurrentLocation(fromCurrent, this::scheduleAutomaticSearch);
        } else {
            scheduleAutomaticSearch();
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
        } else {
            tvTimeSelection.setText((isArriving ? "Arrive " : "Leave ")
                    + formatCalendarFriendly(selectedCalendar));
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

        searchHandler.removeCallbacks(pendingAutoSearch);
        lastSearchSignature = searchSignature();
        final int requestVersion = ++searchVersion;
        fadeOutWarningCard();
        tvSearchHint.setVisibility(View.GONE);
        tvResultsHeading.setVisibility(View.GONE);
        locationPicker.close();
        hideKeyboard();
        shimmerView.stopShimmer();
        shimmerView.setVisibility(View.GONE);
        updateSaveButtonState();

        boolean bothStations = from.type == JourneyLocation.Type.STATION
                && to.type == JourneyLocation.Type.STATION;
        String fromCrs = bothStations ? StationHelper.getCrsFromName(from.displayName()) : null;
        String toCrs = bothStations ? StationHelper.getCrsFromName(to.displayName()) : null;
        String fromNaptan = bothStations ? preferredStationId(from) : null;
        String toNaptan = bothStations ? preferredStationId(to) : null;

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

        if (bothStations) {
            startTflRouting(nonBlank(fromNaptan, fromTfl), nonBlank(toNaptan, toTfl),
                    fromCrs, toCrs, dateParam, timeParam, timeIsParam, requestVersion);
        } else {
            startTflRouting(fromTfl, toTfl, null, null,
                    dateParam, timeParam, timeIsParam, requestVersion);
        }
    }

    @SuppressLint("SetTextI18n")
    private void startInternalRouting(String fromCrs, String toCrs, int requestVersion) {
        recyclerSearchResults.setVisibility(View.GONE);

        shimmerView.setVisibility(View.VISIBLE);
        shimmerView.startShimmer();

        if (plannerEngine != null) {
            findRoutes(fromCrs, toCrs, requestVersion);
        } else {
            cardTextFallbackWarning.setText("Routing engine unavailable. Check your connection.");
            fadeInWarningCard();
            shimmerView.stopShimmer();
            shimmerView.setVisibility(View.GONE);
        }
    }

    private static String nonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.trim().isEmpty() ? preferred : fallback;
    }

    private static String preferredStationId(JourneyLocation station) {
        String name = JourneyLocationPickerController.canonicalStationName(station.displayName());
        String localId = StationHelper.getNaptanFromName(name);
        return nonBlank(localId, station.stationId);
    }

    private static String stationSearchName(JourneyLocation loc) {
        if (loc == null || loc.type != JourneyLocation.Type.STATION) return null;
        String name = JourneyLocationPickerController.canonicalStationName(loc.displayName());
        return name.isEmpty() ? null : name + " Station";
    }

    private static boolean isLocationResolutionError(String message) {
        if (message == null) return false;
        String msg = message.toLowerCase(Locale.UK);
        return msg.contains("identify") || msg.contains("disambiguat")
                || msg.contains("not recognis") || msg.contains("not recogniz")
                || msg.contains("unknown location") || msg.contains("location not found")
                || msg.contains("invalid location") || msg.contains("could not find")
                || msg.contains("couldn't find") || msg.contains("one of the locations");
    }

    private static final class RoutePair {
        final String from, to;
        RoutePair(String from, String to) { this.from = from; this.to = to; }
    }

    private static void addRoutePair(List<RoutePair> attempts, Set<String> seen,
                                     String from, String to) {
        if (from == null || to == null || from.trim().isEmpty() || to.trim().isEmpty()) return;
        if (seen.add(from + "\u0000" + to)) attempts.add(new RoutePair(from, to));
    }

    private void startTflRouting(String fromValue, String toValue,
                                 String fromCrs, String toCrs, String date, String time,
                                 String timeIs, int requestVersion) {
        fadeOutWarningCard();
        recyclerSearchResults.setVisibility(View.GONE);
        shimmerView.setVisibility(View.VISIBLE);
        shimmerView.startShimmer();

        List<RoutePair> attempts = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addRoutePair(attempts, seen, fromValue, toValue);

        JourneyLocation from = locationPicker.getFrom();
        JourneyLocation to = locationPicker.getTo();
        String fromName = stationSearchName(from);
        String toName = stationSearchName(to);

        if (toName != null) addRoutePair(attempts, seen, fromValue, toName);
        if (fromName != null) addRoutePair(attempts, seen, fromName, toValue);
        if (fromName != null && toName != null)
            addRoutePair(attempts, seen, fromName, toName);

        tryTflRouteAttempts(attempts, 0, true, fromValue, toValue,
                fromCrs, toCrs, date, time, timeIs, requestVersion, null);
    }

    private void tryTflRouteAttempts(List<RoutePair> attempts, int index,
                                     boolean allowAddressLookup,
                                     String originalFrom, String originalTo,
                                     String fromCrs, String toCrs, String date,
                                     String time, String timeIs, int requestVersion,
                                     String previousError) {
        if (isDestroyed() || requestVersion != searchVersion) return;
        if (index >= attempts.size()) {
            if (allowAddressLookup) {
                tryGeoapifyFallback(originalFrom, originalTo,
                        fromCrs, toCrs, date, time, timeIs, requestVersion,
                        previousError);
            } else {
                finishTflSearchError(previousError, fromCrs, toCrs, date, requestVersion);
            }
            return;
        }

        RoutePair pair = attempts.get(index);
        String journeyPref = journeyPrefs.getString("journeyPreference", "leasttime");
        String modes = journeyPrefs.getString("preferredModes", null);
        String accessPref = journeyPrefs.getBoolean("accessibilityRequired", false)
                ? "noSolidStairs" : null;
        fadeOutWarningCard();

        if (tflPlannerEngine == null) {
            finishTflSearchError("TfL journey planner unavailable", fromCrs, toCrs, date, requestVersion);
            return;
        }
        tflPlannerEngine.findRoutes(pair.from, pair.to, date, time, timeIs,
                journeyPref, modes, accessPref, new JourneyPlannerEngine.JourneyCallback() {
            @Override public void onJourneysFound(List<JourneyOption> journeys) {
                if (isDestroyed() || requestVersion != searchVersion) return;
                shimmerView.stopShimmer();
                shimmerView.setVisibility(View.GONE);
                if (journeys != null && !journeys.isEmpty()) {
                    fadeOutWarningCard();
                    adapter.updateData(journeys);
                    recyclerSearchResults.setVisibility(View.VISIBLE);
                    tvResultsHeading.setVisibility(View.VISIBLE);
                } else if (date == null && fromCrs != null && toCrs != null) {
                    startInternalRouting(fromCrs, toCrs, requestVersion);
                } else {
                    cardTextFallbackWarning.setText("No routes found for the selected time.");
                    fadeInWarningCard();
                }
            }

            @Override public void onError(String message) {
                if (isDestroyed() || requestVersion != searchVersion) return;
                if (isLocationResolutionError(message)) {
                    tryTflRouteAttempts(attempts, index + 1, allowAddressLookup,
                            originalFrom, originalTo, fromCrs, toCrs,
                            date, time, timeIs, requestVersion, message);
                } else {
                    finishTflSearchError(message, fromCrs, toCrs, date, requestVersion);
                }
            }

            @Override public void onStatusUpdate(String message) {
                if (isDestroyed() || requestVersion != searchVersion) return;
            }
        });
    }

    private void finishTflSearchError(String message, String fromCrs, String toCrs,
                                      String date, int requestVersion) {
        if (isDestroyed() || requestVersion != searchVersion) return;
        if (date == null && fromCrs != null && toCrs != null) {
            startInternalRouting(fromCrs, toCrs, requestVersion);
        } else {
            cardTextFallbackWarning.setText("Search failed: "
                    + (message == null || message.isEmpty()
                    ? "Could not resolve the station. Try selecting a different suggestion."
                    : message));
            fadeInWarningCard();
            shimmerView.stopShimmer();
            shimmerView.setVisibility(View.GONE);
        }
    }

    private interface LocationCoordinateCallback {
        void onResult(String coordinate);
    }

    private void resolveStationCoordinates(JourneyLocation loc, int requestVersion,
                                           LocationCoordinateCallback callback) {
        String stationName = stationSearchName(loc);
        if (stationName == null) { callback.onResult(null); return; }
        APIClient.getClient().create(GeoapifyAutocompleteAPI.class)
                .suggestions(stationName).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call,
                                             Response<JsonObject> response) {
                if (isDestroyed() || requestVersion != searchVersion) return;
                String coords = null;
                JsonObject body = response.body();
                JsonArray results = body != null && body.has("results")
                        && body.get("results").isJsonArray()
                        ? body.getAsJsonArray("results") : null;
                if (response.isSuccessful() && results != null) {
                    String requiredName = JourneyLocationPickerController.canonicalStationName(
                            loc.displayName()).toLowerCase(Locale.UK);
                    for (JsonElement elem : results) {
                        if (!elem.isJsonObject()) continue;
                        JsonObject item = elem.getAsJsonObject();
                        String label = safeJsonString(item, "formatted") + " "
                                + safeJsonString(item, "address_line1");
                        String lower = label.toLowerCase(Locale.UK);
                        if (!lower.contains(requiredName)
                                || !(lower.contains("station") || lower.contains("railway")
                                || lower.contains("underground") || lower.contains("transport"))) continue;
                        try {
                            if (!item.has("lat") || !item.has("lon")) continue;
                            double latitude = item.get("lat").getAsDouble();
                            double longitude = item.get("lon").getAsDouble();
                            if (latitude < -90 || latitude > 90
                                    || longitude < -180 || longitude > 180) continue;
                            coords = String.format(Locale.US, "%.6f,%.6f", latitude, longitude);
                            break;
                        } catch (RuntimeException ignored) { }
                    }
                }
                callback.onResult(coords);
            }

            @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                if (isDestroyed() || requestVersion != searchVersion) return;
                callback.onResult(null);
            }
        });
    }

    private static String safeJsonString(JsonObject item, String key) {
        JsonElement e = item.get(key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return "";
        try { return e.getAsString(); }
        catch (RuntimeException ignored) { return ""; }
    }

    private void tryGeoapifyFallback(String originalFrom, String originalTo,
                                     String fromCrs, String toCrs,
                                     String date, String time, String timeIs,
                                     int requestVersion, String lastError) {
        JourneyLocation origin = locationPicker.getFrom();
        JourneyLocation destination = locationPicker.getTo();
        resolveStationCoordinates(destination, requestVersion, geoTo ->
                resolveStationCoordinates(origin, requestVersion, geoFrom -> {
                    List<RoutePair> recovered = new ArrayList<>();
                    Set<String> seen = new LinkedHashSet<>();
                    if (geoTo != null) addRoutePair(recovered, seen, originalFrom, geoTo);
                    if (geoFrom != null) addRoutePair(recovered, seen, geoFrom, originalTo);
                    if (geoFrom != null && geoTo != null)
                        addRoutePair(recovered, seen, geoFrom, geoTo);
                    if (recovered.isEmpty()) {
                        finishTflSearchError(lastError, fromCrs, toCrs, date, requestVersion);
                    } else {
                        tryTflRouteAttempts(recovered, 0, false, originalFrom, originalTo,
                                fromCrs, toCrs, date, time, timeIs,
                                requestVersion, lastError);
                    }
                }));
    }

    private void findRoutes(String fromCrs, String toCrs, int requestVersion) {
        plannerEngine.findRoutes(fromCrs, toCrs, new JourneyPlannerEngine.JourneyCallback() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onJourneysFound(List<JourneyOption> journeys) {
                if (isDestroyed() || requestVersion != searchVersion) return;
                shimmerView.stopShimmer();
                shimmerView.setVisibility(View.GONE);

                if (journeys != null && !journeys.isEmpty()) {
                    adapter.updateData(journeys);
                    recyclerSearchResults.setVisibility(View.VISIBLE);
                    tvResultsHeading.setVisibility(View.VISIBLE);
                } else {
                    cardTextFallbackWarning.setText("No routes found between these stations.");
                    fadeInWarningCard();
                }
            }

            @Override
            public void onError(String message) {
                if (isDestroyed() || requestVersion != searchVersion) return;
                cardTextFallbackWarning.setText(message);
                fadeInWarningCard();
                shimmerView.stopShimmer();
                shimmerView.setVisibility(View.GONE);
            }

            @Override
            public void onStatusUpdate(String message) {
                if (isDestroyed() || requestVersion != searchVersion) return;
                cardTextFallbackWarning.setText(message);
            }
        });
    }

    @Override
    protected void onDestroy() {
        searchHandler.removeCallbacksAndMessages(null);
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