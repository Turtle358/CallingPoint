package io.github.turtle358.callingpoint.ui.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import com.google.gson.Gson;

import io.github.turtle358.callingpoint.ui.adapters.JourneyLocationPickerController;
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
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.models.SavedRoute;
import io.github.turtle358.callingpoint.ui.adapters.JourneyAdapter;
import io.github.turtle358.callingpoint.models.JourneyLocation;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.JourneyPlannerEngine;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.SavedRoutesManager;
import io.github.turtle358.callingpoint.utils.StationHelper;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.TransitousJourneyPlannerEngine;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.TflJourneyPlannerEngine;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.GraphManager;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.FallbackJourneyPlannerEngine;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.JourneyEndpointAddressResolver;

public class JourneySearchActivity extends AppCompatActivity {

    public static final String EXTRA_EDIT_SAVED_PLACE = "EDIT_SAVED_PLACE";
    private boolean savedPlaceEditMode;

    private AutoCompleteTextView etFromCrs, etToCrs;
    private RecyclerView recyclerSearchResults;
    private TextView tvTimeSelection;
    private TextView tvTravelOptionsSummary;

    private View cardFallbackWarning;
    private TextView cardTextFallbackWarning;
    private View btnTimeSelector;
    private ImageButton btnSaveRoute;
    private View layoutEmptyState;
    private TextView tvSearchHintTitle, tvSearchHint, tvResultsHeading;

    private JourneyAdapter adapter;
    private JourneyEndpointAddressResolver endpointAddresses;
    private List<JourneyOption> displayedJourneys;

    private FallbackJourneyPlannerEngine fallbackPlannerEngine;
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
        tvResultsHeading = findViewById(R.id.tvJourneyResultsHeading);
        layoutEmptyState = findViewById(R.id.layoutJourneyEmptyState);
        tvSearchHintTitle = findViewById(R.id.tvJourneySearchHintTitle);
        tvSearchHint = findViewById(R.id.tvJourneySearchHint);

        findViewById(R.id.btnTravelOptions).setOnClickListener(v -> showJourneyOptionsDialog());
        cardFallbackWarning.setOnClickListener(v -> {
            if (locationPicker != null && locationPicker.hasBothLocations()) handleSearch();
        });
        updateSearchOptionsUI();

        fallbackPlannerEngine = new FallbackJourneyPlannerEngine(
                new TflJourneyPlannerEngine(), new TransitousJourneyPlannerEngine(),
                new JourneyPlannerEngine(GraphManager.getInstance(this)));
        endpointAddresses = new JourneyEndpointAddressResolver();
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
        layoutEmptyState.setVisibility(View.GONE);
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
        if (stepFree) label.append(" · Accessible");

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
        if (!searchSignature().equals(lastSearchSignature)) {
            searchVersion++;
            if (fallbackPlannerEngine != null) fallbackPlannerEngine.cancel();
            if (endpointAddresses != null) endpointAddresses.cancel();
            displayedJourneys = null;
        }
        updateSaveButtonState();
        if (!automaticSearchReady) return;
        if (!locationPicker.hasBothLocations()) {
            searchHandler.removeCallbacks(pendingAutoSearch);
            lastSearchSignature = null;
            if (adapter != null) adapter.updateData(new ArrayList<>());
            recyclerSearchResults.setVisibility(View.GONE);
            shimmerView.stopShimmer();
            shimmerView.setVisibility(View.GONE);
            tvResultsHeading.setVisibility(View.GONE);
            boolean missingDestination = locationPicker.getTo() == null;
            tvSearchHintTitle.setText(missingDestination
                    ? "Choose a destination"
                    : "Choose a starting point");
            tvSearchHint.setText("Search for a station, address or postcode to see journeys.");
            layoutEmptyState.setVisibility(View.VISIBLE);
            fadeOutWarningCard();
        } else {
            scheduleAutomaticSearch();
        }
    }

    private void scheduleAutomaticSearch() {
        if (!automaticSearchReady || locationPicker == null || !locationPicker.hasBothLocations()) return;
        searchHandler.removeCallbacks(pendingAutoSearch);
        searchHandler.postDelayed(pendingAutoSearch, 500);
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
        return from.stableKey() + "|" + from.latitude + "," + from.longitude + "|"
                + to.stableKey() + "|" + to.latitude + "," + to.longitude + "|"
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
        Calendar calIter = Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/London"));
        for (int i = 0; i < 30; i++) {
            if (i == 0) dateDisplayValues[i] = "Today";
            else if (i == 1) dateDisplayValues[i] = "Tomorrow";
            else {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEE, dd MMM", Locale.UK);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("Europe/London"));
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

        Calendar currentSelection = selectedCalendar != null ? selectedCalendar : Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/London"));

        Calendar today = Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/London"));
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

            Calendar result = Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/London"));
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
        Calendar today = Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/London"));
        Calendar tomorrow = Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/London"));
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);

        java.text.SimpleDateFormat timeFmt = new java.text.SimpleDateFormat("HH:mm", Locale.UK);
        timeFmt.setTimeZone(java.util.TimeZone.getTimeZone("Europe/London"));

        if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
            return "today at " + timeFmt.format(cal.getTime());
        } else if (cal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR)) {
            return "tomorrow at " + timeFmt.format(cal.getTime());
        } else {
            java.text.SimpleDateFormat dateFmt = new java.text.SimpleDateFormat("EEE, dd MMM 'at' HH:mm", Locale.UK);
            dateFmt.setTimeZone(java.util.TimeZone.getTimeZone("Europe/London"));
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
        searchHandler.removeCallbacks(pendingAutoSearch);
        lastSearchSignature = searchSignature();
        final int requestVersion = ++searchVersion;
        displayedJourneys = null;
        endpointAddresses.resolve(from, to, () -> {
            if (isDestroyed() || requestVersion != searchVersion) return;
            if (endpointAddresses.apply(displayedJourneys)) adapter.updateData(displayedJourneys);
        });
        fadeOutWarningCard();
        layoutEmptyState.setVisibility(View.GONE);
        tvResultsHeading.setVisibility(View.GONE);
        locationPicker.close();
        hideKeyboard();
        shimmerView.stopShimmer();
        shimmerView.setVisibility(View.GONE);
        updateSaveButtonState();

        String dateParam = null;
        String timeParam = null;
        if (selectedCalendar != null) {
            java.text.SimpleDateFormat dateFmt = new java.text.SimpleDateFormat("yyyyMMdd", Locale.UK);
            java.text.SimpleDateFormat timeFmt = new java.text.SimpleDateFormat("HHmm", Locale.UK);
            dateFmt.setTimeZone(java.util.TimeZone.getTimeZone("Europe/London"));
            timeFmt.setTimeZone(java.util.TimeZone.getTimeZone("Europe/London"));
            dateParam = dateFmt.format(selectedCalendar.getTime());
            timeParam = timeFmt.format(selectedCalendar.getTime());
        }

        recyclerSearchResults.setVisibility(View.GONE);
        shimmerView.setVisibility(View.VISIBLE);
        shimmerView.startShimmer();
        fallbackPlannerEngine.findRoutes(from, to, dateParam, timeParam, isArriving,
                journeyPrefs.getString("journeyPreference", "leasttime"),
                journeyPrefs.getString("preferredModes", null),
                journeyPrefs.getBoolean("accessibilityRequired", false),
                new JourneyPlannerEngine.JourneyCallback() {
                    @Override public void onJourneysFound(List<JourneyOption> journeys) {
                        if (isDestroyed() || requestVersion != searchVersion) return;
                        shimmerView.stopShimmer();
                        shimmerView.setVisibility(View.GONE);
                        fadeOutWarningCard();
                        layoutEmptyState.setVisibility(View.GONE);
                        displayedJourneys = journeys;
                        endpointAddresses.apply(journeys);
                        adapter.updateData(journeys);
                        recyclerSearchResults.setVisibility(View.VISIBLE);
                        tvResultsHeading.setVisibility(View.VISIBLE);
                    }
                    @Override public void onError(String message) {
                        if (isDestroyed() || requestVersion != searchVersion) return;
                        endpointAddresses.cancel();
                        shimmerView.stopShimmer();
                        shimmerView.setVisibility(View.GONE);
                        layoutEmptyState.setVisibility(View.GONE);
                        cardTextFallbackWarning.setText(message);
                        fadeInWarningCard();
                    }
                    @Override public void onStatusUpdate(String message) {
                        if (isDestroyed() || requestVersion != searchVersion) return;
                        tvSearchHintTitle.setText("Finding routes");
                        tvSearchHint.setText(message);
                        layoutEmptyState.setVisibility(View.VISIBLE);
                    }
                });
    }

    @Override
    protected void onDestroy() {
        searchHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        if (fallbackPlannerEngine != null) fallbackPlannerEngine.shutdown();
        if (endpointAddresses != null) endpointAddresses.cancel();
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