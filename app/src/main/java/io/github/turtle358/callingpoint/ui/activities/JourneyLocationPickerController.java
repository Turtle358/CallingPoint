package io.github.turtle358.callingpoint.ui.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.location.Location;
import android.os.SystemClock;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.ResponseBody;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.GeoapifyAutocompleteAPI;
import io.github.turtle358.callingpoint.api.TflAPI;
import io.github.turtle358.callingpoint.models.JourneyLocation;
import io.github.turtle358.callingpoint.ui.adapters.JourneyLocationSuggestionsAdapter;
import io.github.turtle358.callingpoint.utils.JourneySavedPlaces;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.JourneyLocationCache;
import io.github.turtle358.callingpoint.utils.RecentStationsManager;
import io.github.turtle358.callingpoint.utils.StationHelper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class JourneyLocationPickerController {
    private final AppCompatActivity activity;
    private final AutoCompleteTextView fromField, toField;
    private final View cardSearchInput, popupView;
    private final RecyclerView listView;
    private final TextView header, empty, status, homeLabel, workLabel;
    private final View quickLocations;
    private final View currentOriginChip;
    private boolean originChipEditing;
    private final JourneyLocationSuggestionsAdapter adapter;
    private final JourneySavedPlaces saved;
    private final List<String> stations;
    private final GeoapifyAutocompleteAPI addressApi;
    private final TflAPI tflApi;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FusedLocationProviderClient fusedLocation;
    private final ActivityResultLauncher<String[]> locationPermission;
    private final PopupWindow popup;
    private List<JourneyLocation> localStations = new ArrayList<>();
    private List<JourneyLocation> remoteStations = new ArrayList<>();
    private List<JourneyLocation> remoteAddresses = new ArrayList<>();
    private JourneyLocation postcodeMatch;
    private AutoCompleteTextView active;
    private JourneyLocation from, to;
    private String fromSavedSlot, toSavedSlot;
    private AutoCompleteTextView restoringCurrentField;
    private Runnable onRestoredCurrent;
    private String pendingSave;
    @Nullable private String editorSavedPlaceSlot;
    @Nullable private Runnable onEditorPlaceSaved;
    private Runnable onSelectionChanged;
    private boolean suppressTextEvents;
    private boolean locatingNow;
    private AutoCompleteTextView locationTarget;
    private int serial;
    private Runnable pendingLookup;
    private Call<JsonObject> activeRequest, tflRequest;
    private int displayedCount;
    private boolean awaitingAddresses, awaitingTfl;
    private String addressError = "";
    private String tflError = "";
    private static final Pattern UK_POSTCODE = Pattern.compile(
            "^([A-Z]{1,2}[0-9][0-9A-Z]?)([0-9][A-Z]{2})$", Pattern.CASE_INSENSITIVE);

    public JourneyLocationPickerController(@NonNull AppCompatActivity host,
                                           @NonNull List<String> stationNames) {
        activity = host;
        stations = new ArrayList<>(stationNames);
        fromField = host.findViewById(R.id.etFromCrs);
        toField = host.findViewById(R.id.etToCrs);
        cardSearchInput = host.findViewById(R.id.cardSearchInput);
        currentOriginChip = host.findViewById(R.id.chipCurrentOrigin);
        currentOriginChip.setOnClickListener(v -> {
            originChipEditing = true;
            updateCurrentOriginChip();
            fromField.requestFocus();
            fromField.selectAll();
            open(fromField);
            showSearch("");
        });
        popupView = LayoutInflater.from(host).inflate(R.layout.popup_journey_location_suggestions, null);
        quickLocations = popupView.findViewById(R.id.layoutJourneyQuickLocations);
        header = popupView.findViewById(R.id.tvJourneyLocationHeader);
        empty = popupView.findViewById(R.id.tvJourneyLocationEmpty);
        status = popupView.findViewById(R.id.tvJourneyLocationStatus);
        homeLabel = popupView.findViewById(R.id.tvJourneyQuickHome);
        workLabel = popupView.findViewById(R.id.tvJourneyQuickWork);
        listView = popupView.findViewById(R.id.recyclerJourneyLocationSuggestions);
        listView.setLayoutManager(new LinearLayoutManager(host));
        adapter = new JourneyLocationSuggestionsAdapter(this::select);
        listView.setAdapter(adapter);
        popup = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(18));
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popup.setOnDismissListener(() -> { cancelPending(); active = null; pendingSave = null; });
        saved = new JourneySavedPlaces(host);
        addressApi = APIClient.getClient().create(GeoapifyAutocompleteAPI.class);
        tflApi = APIClient.getClient().create(TflAPI.class);
        fusedLocation = LocationServices.getFusedLocationProviderClient(host);
        locationPermission = host.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), grant -> {
                    boolean allowed = Boolean.TRUE.equals(grant.get(Manifest.permission.ACCESS_FINE_LOCATION))
                            || Boolean.TRUE.equals(grant.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (allowed) {
                        if (restoringCurrentField != null) resolveCurrentForSavedRoute();
                        else loadCurrentLocation();
                    } else {
                        restoringCurrentField = null;
                        onRestoredCurrent = null;
                        Toast.makeText(activity, "Location permission denied", Toast.LENGTH_SHORT).show();
                    }
                });
        attachField(fromField);
        attachField(toField);
        popupView.findViewById(R.id.btnJourneyUseLocation).setOnClickListener(v -> requestCurrentLocation());
        popupView.findViewById(R.id.btnJourneyQuickHome).setOnClickListener(v -> clickSaved(JourneySavedPlaces.HOME));
        popupView.findViewById(R.id.btnJourneyQuickWork).setOnClickListener(v -> clickSaved(JourneySavedPlaces.WORK));
        popupView.findViewById(R.id.btnJourneyQuickHome).setOnLongClickListener(v -> { editSaved(JourneySavedPlaces.HOME); return true; });
        popupView.findViewById(R.id.btnJourneyQuickWork).setOnLongClickListener(v -> { editSaved(JourneySavedPlaces.WORK); return true; });
        updateQuickLabels();
    }

    @Nullable public JourneyLocation getFrom() { return from; }
    @Nullable public JourneyLocation getTo() { return to; }
    @Nullable public String getFromTflValue() { return from == null ? null : from.tflValue(); }
    @Nullable public String getToTflValue() { return to == null ? null : to.tflValue(); }
    public void setFrom(@NonNull JourneyLocation loc) { setChoice(fromField, loc, null); }
    public void setTo(@NonNull JourneyLocation loc) { setChoice(toField, loc, null); }
    public void setFrom(@NonNull JourneyLocation loc, @Nullable String savedSlot) { setChoice(fromField, loc, savedSlot); }
    public void setTo(@NonNull JourneyLocation loc, @Nullable String savedSlot) { setChoice(toField, loc, savedSlot); }
    @Nullable public String getFromSavedSlot() { return fromSavedSlot; }
    @Nullable public String getToSavedSlot() { return toSavedSlot; }
    public boolean hasBothLocations() { return from != null && to != null; }
    public boolean isOpen() { return popup.isShowing(); }
    public void setOnSelectionChanged(@Nullable Runnable listener) { onSelectionChanged = listener; }

    public void beginSavedPlaceEditor(@NonNull String slot, @Nullable Runnable whenSaved) {
        if (!JourneySavedPlaces.HOME.equals(slot) && !JourneySavedPlaces.WORK.equals(slot))
            throw new IllegalArgumentException("Only Home or Work can be edited");
        editorSavedPlaceSlot = slot;
        onEditorPlaceSaved = whenSaved;
        fromField.setHint("Search for " + slot + " address or station");
        suppressTextEvents = true;
        try { fromField.setText(""); }
        finally { suppressTextEvents = false; }
        from = null;
        fromSavedSlot = null;
        updateCurrentOriginChip();
        fromField.post(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                fromField.requestFocus();
                open(fromField);
            }
        });
    }
    private void notifySelectionChanged() { if (onSelectionChanged != null) onSelectionChanged.run(); }

    public void swap() {
        JourneyLocation previous = from;
        from = to;
        to = previous;
        String previousSlot = fromSavedSlot;
        fromSavedSlot = toSavedSlot;
        toSavedSlot = previousSlot;
        suppressTextEvents = true;
        try {
            fromField.setText(from == null ? "" : from.displayName());
            toField.setText(to == null ? "" : to.displayName());
        } finally { suppressTextEvents = false; }
        updateCurrentOriginChip();
        notifySelectionChanged();
        close();
    }

    private void attachField(AutoCompleteTextView field) {
        field.setAdapter(null);
        field.setThreshold(Integer.MAX_VALUE);
        field.setOnClickListener(v -> open(field));
        field.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                if (field == fromField && from != null
                        && from.type == JourneyLocation.Type.CURRENT_LOCATION) {
                    originChipEditing = true;
                    updateCurrentOriginChip();
                    field.selectAll();
                }
                open(field);
            } else if (active == field) close();
            if (!focused && field == fromField) {
                originChipEditing = false;
                updateCurrentOriginChip();
            }
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (suppressTextEvents) return;
                if (field == fromField) {
                    from = null;
                    fromSavedSlot = null;
                    originChipEditing = false;
                    updateCurrentOriginChip();
                }
                else { to = null; toSavedSlot = null; }
                notifySelectionChanged();
                if (field.hasFocus()) {
                    if (!popup.isShowing()) open(field);
                    else { active = field; showSearch(s.toString().trim()); }
                }
            }
            @Override public void afterTextChanged(Editable e) { }
        });
    }

    private void open(@NonNull AutoCompleteTextView field) {
        if (activity.isFinishing() || activity.isDestroyed()
                || !cardSearchInput.isAttachedToWindow()
                || cardSearchInput.getWindowToken() == null
                || cardSearchInput.getWidth() <= 0) {
            return;
        }
        if (popup.isShowing() && active != field) popup.dismiss();
        active = field;
        updateQuickLabels();
        if (!popup.isShowing()) {
            popup.setWidth(cardSearchInput.getWidth());
            popup.showAsDropDown(cardSearchInput, 0, dp(8), Gravity.START);
        }
        showSearch(field.getText().toString().trim());
        field.postDelayed(this::resizePopup, 180);
    }

    public void close() {
        cancelPending();
        if (popup.isShowing()) popup.dismiss();
        active = null;
        pendingSave = null;
    }

    private void cancelPending() {
        serial++;
        if (pendingLookup != null) handler.removeCallbacks(pendingLookup);
        if (activeRequest != null) activeRequest.cancel();
        if (tflRequest != null) tflRequest.cancel();
        pendingLookup = null;
    }

    private void select(@NonNull JourneyLocation loc) { select(loc, null); }

    private void select(@NonNull JourneyLocation loc, @Nullable String selectedSavedSlot) {
        if (active == null) return;
        final boolean choseOrigin = active == fromField;
        final boolean choseDestination = active == toField;
        String slotToSave = editorSavedPlaceSlot != null ? editorSavedPlaceSlot : pendingSave;
        if (slotToSave != null) {
            if (loc.type == JourneyLocation.Type.CURRENT_LOCATION) {
                Toast.makeText(activity, "Choose an address or station for Home/Work", Toast.LENGTH_SHORT).show();
                return;
            }
            saved.save(slotToSave, loc);
            Toast.makeText(activity, (JourneySavedPlaces.HOME.equals(slotToSave) ? "Home" : "Work") + " saved", Toast.LENGTH_SHORT).show();
            selectedSavedSlot = slotToSave;
            pendingSave = null;
            updateQuickLabels();
        }
        saved.remember(loc);
        setChoice(active, loc, selectedSavedSlot);
        close();
        if (editorSavedPlaceSlot != null) {
            editorSavedPlaceSlot = null;
            Runnable callback = onEditorPlaceSaved;
            onEditorPlaceSaved = null;
            if (callback != null) callback.run();
            return;
        }
        if (choseOrigin && to == null) {
            toField.post(() -> toField.requestFocus());
        } else if (choseDestination && from == null) {
            fromField.post(() -> fromField.requestFocus());
        }
    }

    private void setChoice(@NonNull AutoCompleteTextView field, @NonNull JourneyLocation loc,
                           @Nullable String savedSlot) {
        suppressTextEvents = true;
        try { field.setText(loc.displayName()); field.setSelection(field.length()); }
        finally { suppressTextEvents = false; }
        if (field == fromField) {
            from = loc;
            fromSavedSlot = savedSlot;
            originChipEditing = false;
            updateCurrentOriginChip();
        } else { to = loc; toSavedSlot = savedSlot; }
        notifySelectionChanged();
    }

    private void updateCurrentOriginChip() {
        boolean show = !originChipEditing && from != null
                && from.type == JourneyLocation.Type.CURRENT_LOCATION;
        currentOriginChip.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void clickSaved(String slot) {
        JourneyLocation place = saved.get(slot);
        if (place != null) select(place, slot);
        else editSaved(slot);
    }

    private void editSaved(String slot) {
        pendingSave = slot;
        if (active == null) active = fromField;
        showSearch(active.getText().toString().trim());
        Toast.makeText(activity, "Choose a suggestion to save as " +
                (JourneySavedPlaces.HOME.equals(slot) ? "Home" : "Work"), Toast.LENGTH_SHORT).show();
    }

    private void updateQuickLabels() {
        homeLabel.setText(saved.get(JourneySavedPlaces.HOME) == null ? "Set Home" : "Home");
        workLabel.setText(saved.get(JourneySavedPlaces.WORK) == null ? "Set Work" : "Work");
    }

    @Nullable private static String normalizedPostcode(String input) {
        String compact = input.toUpperCase(Locale.UK).replaceAll("\\s+", "");
        if ("GIR0AA".equals(compact)) return "GIR 0AA";
        Matcher matcher = UK_POSTCODE.matcher(compact);
        return matcher.matches() ? matcher.group(1) + " " + matcher.group(2) : null;
    }

    private void showSearch(@NonNull String typed) {
        quickLocations.setVisibility(editorSavedPlaceSlot == null && typed.isEmpty() ? View.VISIBLE : View.GONE);
        cancelPending();
        final int token = serial;
        localStations = new ArrayList<>();
        remoteStations = new ArrayList<>();
        remoteAddresses = new ArrayList<>();
        postcodeMatch = null;
        awaitingAddresses = false;
        awaitingTfl = false;
        addressError = "";
        tflError = "";
        String selectionSlot = editorSavedPlaceSlot != null ? editorSavedPlaceSlot : pendingSave;
        if (typed.isEmpty()) {
            header.setText(selectionSlot == null ? "RECENT DESTINATIONS" : "CHOOSE " + selectionSlot.toUpperCase(Locale.UK));
            List<JourneyLocation> recents = saved.recents();
            if (recents.isEmpty()) for (String station : new RecentStationsManager(activity).getRecentStations()) {
                if (station != null && !station.trim().isEmpty()) recents.add(JourneyLocation.station(station, null));
            }
            updateResults(recents, "", true, recents.isEmpty() ? "No recent destinations" : null);
            return;
        }
        header.setText(selectionSlot == null ? "STATIONS, ADDRESSES & POSTCODES" : "CHOOSE " + selectionSlot.toUpperCase(Locale.UK));
        String postcode = normalizedPostcode(typed);
        if (postcode != null) postcodeMatch = JourneyLocation.postcode(postcode);
        localStations = findStations(typed);
        awaitingAddresses = typed.length() >= 3;
        awaitingTfl = typed.length() >= 3;
        displayCombined(typed);
        if (typed.length() < 3) return;
        pendingLookup = () -> {
            tflRequest = tflApi.searchJourneyStations(typed, "tube,dlr,overground,tram", 8);
            tflRequest.enqueue(new Callback<JsonObject>() {
                @Override public void onResponse(@NonNull Call<JsonObject> call,
                                                 @NonNull Response<JsonObject> response) {
                    if (serial != token) return;
                    awaitingTfl = false;
                    if (response.isSuccessful() && response.body() != null) {
                        remoteStations = parseTflStations(response.body());
                    } else {
                        tflError = "TfL station lookup failed (HTTP " + response.code() + ")";
                    }
                    displayCombined(typed);
                }
                @Override public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                    if (serial != token || call.isCanceled()) return;
                    awaitingTfl = false;
                    tflError = "TfL station lookup unavailable";
                    displayCombined(typed);
                }
            });
            String addressQuery = postcodeMatch == null ? typed : postcodeMatch.name;
            activeRequest = addressApi.suggestions(addressQuery);
            activeRequest.enqueue(new Callback<JsonObject>() {
                @Override public void onResponse(@NonNull Call<JsonObject> call,
                                                 @NonNull Response<JsonObject> response) {
                    if (serial != token) return;
                    awaitingAddresses = false;
                    if (response.isSuccessful() && response.body() != null) {
                        remoteAddresses = parseAddresses(response.body());
                    } else {
                        addressError = describeAddressError(response);
                    }
                    displayCombined(typed);
                }
                @Override public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                    if (serial != token || call.isCanceled()) return;
                    awaitingAddresses = false;
                    addressError = "Address lookup unavailable; station search still works";
                    displayCombined(typed);
                }
            });
        };
        handler.postDelayed(pendingLookup, 350);
    }

    private String describeAddressError(@NonNull Response<JsonObject> response) {
        int code = response.code();
        int upstream = 0;
        String providerError = "";
        try {
            ResponseBody body = response.errorBody();
            if (body != null) {
                JsonObject detail = JsonParser.parseString(body.string()).getAsJsonObject();
                if (detail.has("upstreamStatus") && !detail.get("upstreamStatus").isJsonNull())
                    upstream = detail.get("upstreamStatus").getAsInt();
                if (detail.has("error") && !detail.get("error").isJsonNull())
                    providerError = detail.get("error").getAsString();
            }
        } catch (Exception ignored) { }
        if (code == 404) return "Address endpoint missing from Cloudflare (404)";
        if (code == 503) return "Geoapify key not configured in Cloudflare (503)";
        if (code == 429 || upstream == 429) return "Geoapify quota / rate limit reached (429)";
        if (upstream == 401 || upstream == 403)
            return "Geoapify API key rejected or restricted (" + upstream + ")";
        if (upstream == 400) return "Geoapify rejected the request (400)";
        if (upstream >= 500) return "Geoapify service error (" + upstream + ")";
        if (code == 401 || code == 403) return "Cloudflare API authentication failed (" + code + ")";
        if (!providerError.isEmpty()) return providerError;
        return "Address lookup failed (HTTP " + code + ")";
    }

    private void displayCombined(String query) {
        List<JourneyLocation> all = new ArrayList<>();
        if (postcodeMatch != null) all.add(postcodeMatch);
        all.addAll(localStations.subList(0, Math.min(7, localStations.size())));
        all.addAll(remoteStations.subList(0, Math.min(5, remoteStations.size())));
        all.addAll(remoteAddresses.subList(0, Math.min(5, remoteAddresses.size())));
        Map<String, JourneyLocation> distinct = new LinkedHashMap<>();
        for (JourneyLocation place : all) {
            String key = place.type == JourneyLocation.Type.STATION
                    ? "station:" + canonicalStationName(place.displayName()).toLowerCase(Locale.UK)
                    : place.stableKey();
            JourneyLocation previous = distinct.get(key);
            if (previous == null) {
                distinct.put(key, place);
            } else if (place.type == JourneyLocation.Type.STATION
                    && !hasStationId(previous) && hasStationId(place)) {
                distinct.put(key, place);
            }
        }
        List<JourneyLocation> display = new ArrayList<>(distinct.values());
        if (display.size() > 11) display = display.subList(0, 11);
        updateResults(display, query, false, display.isEmpty() && !awaitingAddresses && !awaitingTfl
                ? "No matching locations" : null);
        if (!addressError.isEmpty()) {
            status.setText(addressError);
            status.setVisibility(View.VISIBLE);
        } else if (!tflError.isEmpty()) {
            status.setText(tflError);
            status.setVisibility(View.VISIBLE);
        } else if (awaitingAddresses && query.length() >= 3) {
            status.setText("Searching addresses…");
            status.setVisibility(View.VISIBLE);
        }
        resizePopup();
    }

    static String canonicalStationName(@NonNull String value) {
        return value.trim().replaceAll(
                "(?i)\\s+(?:rail|underground|tube|overground|dlr)\\s+station$", "")
                .replaceAll("(?i)\\s+station$", "").trim();
    }

    private static boolean hasStationId(@NonNull JourneyLocation value) {
        return value.stationId != null && !value.stationId.trim().isEmpty();
    }

    private void updateResults(List<JourneyLocation> display, String query, boolean recent, String emptyMessage) {
        displayedCount = display.size();
        adapter.show(display, query, recent);
        listView.setVisibility(display.isEmpty() ? View.GONE : View.VISIBLE);
        empty.setVisibility(emptyMessage == null ? View.GONE : View.VISIBLE);
        if (emptyMessage != null) empty.setText(emptyMessage);
        status.setVisibility(View.GONE);
        resizePopup();
    }

    private void resizePopup() {
        if (!popup.isShowing() || active == null) return;
        Rect visible = new Rect();
        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
        int[] anchor = new int[2];
        cardSearchInput.getLocationOnScreen(anchor);
        int availableBelow = Math.max(0, visible.bottom - anchor[1]
                - cardSearchInput.getHeight() - dp(14));
        int baseHeight = dp(68) + (quickLocations.getVisibility() == View.VISIBLE ? dp(126) : 0)
                + (empty.getVisibility() == View.VISIBLE ? dp(48) : 0)
                + (status.getVisibility() == View.VISIBLE ? dp(36) : 0);

        int listTarget = Math.min(displayedCount, 5) * dp(68);
        if (displayedCount > 0 && quickLocations.getVisibility() == View.GONE) {
            listTarget = Math.max(listTarget, dp(178));
        }
        int listMax = Math.max(0, Math.min(dp(345), availableBelow - baseHeight));
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        int newListHeight = Math.min(listTarget, listMax);
        if (params.height != newListHeight) {
            params.height = newListHeight;
            listView.setLayoutParams(params);
        }
        int newPopupHeight = Math.min(availableBelow, baseHeight + newListHeight);
        popup.update(cardSearchInput, 0, dp(8), cardSearchInput.getWidth(), newPopupHeight);
    }

    private List<JourneyLocation> findStations(String input) {
        String term = input.trim().toLowerCase(Locale.UK);
        List<String> leading = new ArrayList<>(), middle = new ArrayList<>();
        for (String station : stations) {
            if (station == null) continue;
            String name = station.toLowerCase(Locale.UK);
            String crs = StationHelper.getCrsFromName(station);
            if (name.startsWith(term)) leading.add(station);
            else if (name.contains(term) || (crs != null && crs.toLowerCase(Locale.UK).contains(term))) middle.add(station);
        }
        leading.addAll(middle);
        List<JourneyLocation> result = new ArrayList<>();
        for (String station : leading) {
            result.add(JourneyLocation.station(station, StationHelper.getNaptanFromName(station)));
            if (result.size() >= 8) break;
        }
        return result;
    }

    private static List<JourneyLocation> parseTflStations(JsonObject data) {
        List<JourneyLocation> result = new ArrayList<>();
        JsonArray array = data.getAsJsonArray("matches");
        if (array == null) return result;
        for (JsonElement el : array) {
            if (!el.isJsonObject()) continue;
            JsonObject match = el.getAsJsonObject();
            String name = getString(match, "name", "");
            String id = getString(match, "id", "");
            if (!name.isEmpty() && !id.isEmpty()) {
                String canonicalName = canonicalStationName(name);
                String localId = StationHelper.getNaptanFromName(canonicalName);
                JourneyLocation place = JourneyLocation.station(name,
                        localId != null && !localId.isEmpty() ? localId : id);
                place.subtitle = "TfL station";
                result.add(place);
            }
        }
        return result;
    }

    private static List<JourneyLocation> parseAddresses(JsonObject data) {
        List<JourneyLocation> result = new ArrayList<>();
        JsonArray array = data.getAsJsonArray("results");
        if (array == null) return result;
        for (JsonElement el : array) {
            if (!el.isJsonObject()) continue;
            JsonObject place = el.getAsJsonObject();
            try {
                double lat = place.get("lat").getAsDouble();
                double lon = place.get("lon").getAsDouble();
                String title = getString(place, "address_line1", getString(place, "formatted", "Address"));
                String subtitle = getString(place, "address_line2", getString(place, "postcode", ""));
                result.add(JourneyLocation.address(title, subtitle, lat, lon));
            } catch (RuntimeException ignored) { }
        }
        return result;
    }

    private static String getString(JsonObject o, String key, String other) {
        JsonElement value = o.get(key);
        return value == null || value.isJsonNull() ? other : value.getAsString();
    }

    private void requestCurrentLocation() {
        locationTarget = active == null ? fromField : active;
        boolean fine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            locationPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        } else loadCurrentLocation();
    }

    @SuppressLint("MissingPermission")
    private void loadCurrentLocation() {
        if (locatingNow) return;
        locatingNow = true;
        fusedLocation.getLastLocation()
                .addOnSuccessListener(activity, location -> {
                    if (isUsableCachedLocation(location)) {
                        useResolvedLocation(location);
                    } else {
                        requestFreshLocation();
                    }
                })
                .addOnFailureListener(activity, ignored -> requestFreshLocation());
    }

    private boolean isUsableCachedLocation(@Nullable Location location) {
        if (location == null) return false;
        long ageMillis = (SystemClock.elapsedRealtimeNanos()
                - location.getElapsedRealtimeNanos()) / 1_000_000L;
        boolean fine = ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        float maxAccuracyMetres = fine ? 125f : 1200f;
        return ageMillis >= 0 && ageMillis <= 20_000L
                && location.hasAccuracy()
                && location.getAccuracy() <= maxAccuracyMetres;
    }

    @SuppressLint("MissingPermission")
    private void requestFreshLocation() {
        fusedLocation.getCurrentLocation(
                        LocationRequest.PRIORITY_HIGH_ACCURACY,
                        new CancellationTokenSource().getToken())
                .addOnSuccessListener(activity, location -> {
                    if (location == null) {
                        locatingNow = false;
                        Toast.makeText(activity, "Current location unavailable",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        useResolvedLocation(location);
                    }
                })
                .addOnFailureListener(activity, error -> {
                    locatingNow = false;
                    Toast.makeText(activity, "Could not obtain current location",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void useResolvedLocation(@NonNull Location location) {
        locatingNow = false;
        JourneyLocationCache.remember(activity, location);
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (active != null && locationTarget != null && active != locationTarget) return;
        if (active == null) active = locationTarget == null ? fromField : locationTarget;
        select(JourneyLocation.currentLocation(
                location.getLatitude(), location.getLongitude()));
    }

    @SuppressLint("MissingPermission")
    public void prefillCurrentOriginIfPermitted() {
        if (from != null || fromField.length() != 0) return;
        boolean fine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) return;
        Location warmed = JourneyLocationCache.getFresh(activity);
        if (warmed != null) {
            setChoice(fromField, JourneyLocation.currentLocation(
                    warmed.getLatitude(), warmed.getLongitude()), null);
            return;
        }
        fusedLocation.getLastLocation()
                .addOnSuccessListener(activity, location -> {
                    if (from != null || fromField.length() != 0 || activity.isDestroyed()) return;
                    if (isUsableCachedLocation(location)) {
                        JourneyLocationCache.remember(activity, location);
                        setChoice(fromField, JourneyLocation.currentLocation(
                                location.getLatitude(), location.getLongitude()), null);
                    } else {
                        fusedLocation.getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY,
                                        new CancellationTokenSource().getToken())
                                .addOnSuccessListener(activity, fix -> {
                                    if (fix != null && from == null && fromField.length() == 0
                                            && !activity.isDestroyed()) {
                                        JourneyLocationCache.remember(activity, fix);
                                        setChoice(fromField, JourneyLocation.currentLocation(
                                                fix.getLatitude(), fix.getLongitude()), null);
                                    }
                                });
                    }
                });
    }

    public void restoreCurrentLocation(boolean fromSide, @NonNull Runnable onReady) {
        restoringCurrentField = fromSide ? fromField : toField;
        onRestoredCurrent = onReady;
        boolean fine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            locationPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        } else resolveCurrentForSavedRoute();
    }

    @SuppressLint("MissingPermission")
    private void resolveCurrentForSavedRoute() {
        fusedLocation.getLastLocation()
                .addOnSuccessListener(activity, location -> {
                    if (isUsableCachedLocation(location)) finishRestoringCurrent(location);
                    else requestFreshForSavedRoute();
                })
                .addOnFailureListener(activity, error -> requestFreshForSavedRoute());
    }

    @SuppressLint("MissingPermission")
    private void requestFreshForSavedRoute() {
        fusedLocation.getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY,
                        new CancellationTokenSource().getToken())
                .addOnSuccessListener(activity, this::finishRestoringCurrent)
                .addOnFailureListener(activity, error -> finishRestoringCurrent(null));
    }

    private void finishRestoringCurrent(@Nullable Location fix) {
        AutoCompleteTextView field = restoringCurrentField;
        Runnable callback = onRestoredCurrent;
        restoringCurrentField = null;
        onRestoredCurrent = null;
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (fix == null) {
            Toast.makeText(activity, "Current location unavailable. Tap the location button to retry.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (field != null) {
            setChoice(field, JourneyLocation.currentLocation(fix.getLatitude(), fix.getLongitude()), null);
        }
        if (callback != null) callback.run();
    }

    public void destroy() {
        restoringCurrentField = null;
        onRestoredCurrent = null;
        close();
    }
    private int dp(int pixels) { return Math.round(pixels * activity.getResources().getDisplayMetrics().density); }
}