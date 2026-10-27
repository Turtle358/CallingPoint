package io.github.turtle358.callingpoint.ui.activities;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.NationalRailAPI;
import io.github.turtle358.callingpoint.api.TflAPI;
import io.github.turtle358.callingpoint.data.PinnedDataManager;
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse;
import io.github.turtle358.callingpoint.models.TflVehicleArrival;
import io.github.turtle358.callingpoint.ui.adapters.CoachAdapter;
import io.github.turtle358.callingpoint.ui.adapters.TimelineAdapter;
import io.github.turtle358.callingpoint.utils.StationHelper;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ServiceDetailsActivity extends AppCompatActivity {

    private RecyclerView recyclerTimeline, recyclerCoaches;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvHeaderDestination, tvHeaderTime, tvHeaderStatus, tvHeaderOperator, tvCoachCapacity;
    private ImageButton btnPinService;
    private View topHeaderBar, cardTrainDetails, titleCallingPoints, cardTflDisclaimer, cardHeaderInfo, cardCallingPoints, cardErrorState;
    private ShimmerFrameLayout shimmerTimeline;

    private String serviceId = "";
    private String userOrigin;
    private String userDest;
    private String fallbackOperatorName = "London Underground";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service_details);

        userOrigin = getIntent().getStringExtra("USER_ORIGIN");
        userDest = getIntent().getStringExtra("USER_DESTINATION");
        serviceId = getIntent().getStringExtra("SERVICE_ID");

        initViews();
        setupInsets();
        setupListeners();

        if (serviceId != null && !serviceId.isEmpty()) {
            fetchServiceDetails(serviceId);
        } else {
            Toast.makeText(this, "Error: No Service ID provided", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initViews() {
        topHeaderBar = findViewById(R.id.topHeaderBar);
        tvHeaderDestination = findViewById(R.id.tvHeaderDestination);
        tvHeaderTime = findViewById(R.id.tvHeaderTime);
        tvHeaderStatus = findViewById(R.id.tvHeaderStatus);
        tvHeaderOperator = findViewById(R.id.tvHeaderOperator);
        cardHeaderInfo = findViewById(R.id.cardHeaderInfo);
        cardCallingPoints = findViewById(R.id.cardCallingPoints);
        cardTrainDetails = findViewById(R.id.cardTrainDetails);
        tvCoachCapacity = findViewById(R.id.tvCoachCapacity);
        recyclerCoaches = findViewById(R.id.recyclerCoaches);
        btnPinService = findViewById(R.id.btnPinService);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshServiceDetails);
        recyclerTimeline = findViewById(R.id.recyclerTimeline);
        titleCallingPoints = findViewById(R.id.tvCallingPointsTitle);
        cardTflDisclaimer = findViewById(R.id.cardTflDisclaimer);
        shimmerTimeline = findViewById(R.id.shimmerTimeline);
        cardErrorState = findViewById(R.id.cardErrorState);

        recyclerCoaches.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerTimeline.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupInsets() {
        if (topHeaderBar != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(topHeaderBar, (v, insets) -> {
                v.getLayoutParams().height = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
                v.requestLayout();
                return insets;
            });
        }
    }

    private void setupListeners() {
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        swipeRefreshLayout.setOnRefreshListener(() -> {
            fetchServiceDetails(serviceId);
            swipeRefreshLayout.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        });

        PinnedDataManager pinnedManager = new PinnedDataManager(this);
        updatePinButtonVisuals(pinnedManager.isServicePinned(serviceId));

        btnPinService.setOnClickListener(v -> {
            pinnedManager.toggleServicePin(serviceId);
            boolean isNowPinned = pinnedManager.isServicePinned(serviceId);
            updatePinButtonVisuals(isNowPinned);
            Toast.makeText(this, isNowPinned ? "Train Pinned!" : "Pin Removed", Toast.LENGTH_SHORT).show();
        });
    }

    private void updatePinButtonVisuals(boolean isPinned) {
        btnPinService.setImageResource(isPinned ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
    }

    private void fetchServiceDetails(String serviceId) {
        swipeRefreshLayout.setRefreshing(true);
        NationalRailAPI api = APIClient.getClient().create(NationalRailAPI.class);

        api.getServiceDetails(serviceId).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ServiceDetailsResponse> call, @NonNull Response<ServiceDetailsResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                swipeRefreshLayout.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    renderNationalRailDetails(response.body(), api);
                } else {
                    fetchTflServiceDetails(serviceId);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ServiceDetailsResponse> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                swipeRefreshLayout.setRefreshing(false);
                fetchTflServiceDetails(serviceId);
            }
        });
    }

    private void renderNationalRailDetails(ServiceDetailsResponse data, NationalRailAPI api) {
        if (cardErrorState != null) cardErrorState.setVisibility(View.GONE);
        if (cardCallingPoints != null) cardCallingPoints.setVisibility(View.VISIBLE);
        if (recyclerTimeline != null) recyclerTimeline.setVisibility(View.VISIBLE);
        if (btnPinService != null) btnPinService.setVisibility(View.VISIBLE);
        if (cardTflDisclaimer != null) cardTflDisclaimer.setVisibility(View.GONE);

        String operatorName = data.operator != null ? data.operator : "Unknown";
        setThemeByOperator(operatorName);

        boolean preferArrival = userDest != null && userDest.equalsIgnoreCase(data.locationName);

        String schTime = preferArrival
                ? (data.scheduledArrivalTime != null ? data.scheduledArrivalTime : (data.scheduledDepartureTime != null ? data.scheduledDepartureTime : ""))
                : (data.scheduledDepartureTime != null ? data.scheduledDepartureTime : (data.scheduledArrivalTime != null ? data.scheduledArrivalTime : ""));

        String estTime = preferArrival
                ? (data.estimatedArrivalTime != null ? data.estimatedArrivalTime : (data.estimatedDepartureTime != null ? data.estimatedDepartureTime : ""))
                : (data.estimatedDepartureTime != null ? data.estimatedDepartureTime : (data.estimatedArrivalTime != null ? data.estimatedArrivalTime : ""));

        boolean isCancelled = data.isCancelled || "Cancelled".equalsIgnoreCase(estTime);
        checkDelayRepayEligibility(schTime, estTime, operatorName, isCancelled);

        ServiceDetailsResponse.TimelinePoint currentStation = new ServiceDetailsResponse.TimelinePoint();
        currentStation.locationName = data.locationName != null ? data.locationName : "Current Station";
        currentStation.crs = data.crs;
        currentStation.scheduledTime = schTime;
        currentStation.estimatedTime = estTime;
        currentStation.isCancelled = isCancelled;
        currentStation.platform = data.platform;
        currentStation.actualTime = preferArrival
                ? (data.actualArrivalTime != null ? data.actualArrivalTime : data.actualDepartureTime)
                : (data.actualDepartureTime != null ? data.actualDepartureTime : data.actualArrivalTime);

        List<ServiceDetailsResponse.TimelinePoint> allPoints = new ArrayList<>();
        if (data.previousCallingPoints != null && !data.previousCallingPoints.isEmpty()) {
            allPoints.addAll(data.previousCallingPoints.get(0).callingPoint);
        }
        allPoints.add(currentStation);
        if (data.subsequentCallingPoints != null && !data.subsequentCallingPoints.isEmpty()) {
            allPoints.addAll(data.subsequentCallingPoints.get(0).callingPoint);
        }

        String originName = allPoints.get(0).locationName;
        String destName = allPoints.get(allPoints.size() - 1).locationName;

        setDestinationTitle(originName, destName, originName.equals(destName) ? currentStation.locationName : null);

        int stopsCount = allPoints.size() - 1;
        String durationStr = calculateDuration(allPoints.get(0).scheduledTime, allPoints.get(allPoints.size() - 1).scheduledTime);
        String subInfoText = (!durationStr.isEmpty() ? durationStr + " • " : "") + stopsCount + (stopsCount == 1 ? " stop • " : " stops • ") + operatorName;

        tvHeaderOperator.setText(subInfoText);
        tvHeaderTime.setText(schTime);

        tvHeaderStatus.setText(estTime);
        tvHeaderStatus.setVisibility(View.VISIBLE);
        if ("On time".equalsIgnoreCase(estTime)) {
            tvHeaderStatus.setTextColor(Color.parseColor("#00875A"));
        } else if (isCancelled) {
            tvHeaderStatus.setTextColor(Color.parseColor("#DE350B"));
            tvHeaderStatus.setText("Cancelled");
        } else {
            tvHeaderStatus.setTextColor(Color.parseColor("#FF8C00"));
        }

        renderCoachData(data);

        if (titleCallingPoints != null) titleCallingPoints.setVisibility(View.VISIBLE);
        String originParam = userOrigin != null && !userOrigin.isEmpty() ? userOrigin : currentStation.locationName;
        int brandColour = TOCBrandHelper.getColourForToc(operatorName);

        TimelineAdapter adapter = new TimelineAdapter(allPoints, brandColour, originParam, userDest, serviceId, api, crs -> {
            android.content.Intent intent = new android.content.Intent(ServiceDetailsActivity.this, MainActivity.class);
            intent.putExtra("OPEN_STATION_CRS", crs);
            startActivity(intent);
        });
        recyclerTimeline.setAdapter(adapter);
    }

    private void renderCoachData(ServiceDetailsResponse data) {
        if (data.formation != null && data.formation.coaches != null && !data.formation.coaches.isEmpty()) {
            cardTrainDetails.setVisibility(View.VISIBLE);
            tvCoachCapacity.setText(data.formation.coaches.size() + " coaches");
            recyclerCoaches.setAdapter(new CoachAdapter(data.formation.coaches));
        } else if (data.length != 0) {
            cardTrainDetails.setVisibility(View.VISIBLE);
            tvCoachCapacity.setText(data.length + " coaches");
            List<ServiceDetailsResponse.Coach> basicCoaches = new ArrayList<>();
            for (int i = 1; i <= data.length; i++) {
                ServiceDetailsResponse.Coach basicCoach = new ServiceDetailsResponse.Coach();
                basicCoach.number = String.valueOf(i);
                basicCoaches.add(basicCoach);
            }
            recyclerCoaches.setAdapter(new CoachAdapter(basicCoaches));
        } else {
            cardTrainDetails.setVisibility(View.GONE);
        }
    }

    private void fetchTflServiceDetails(String vehicleId) {
        if (btnPinService != null) btnPinService.setVisibility(View.GONE);
        TflAPI tflApi = APIClient.getClient().create(TflAPI.class);

        tflApi.getVehicleArrivals(vehicleId).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<TflVehicleArrival>> call, @NonNull Response<List<TflVehicleArrival>> response) {
                if (isFinishing() || isDestroyed()) return;
                swipeRefreshLayout.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    List<TflVehicleArrival> tflArrivals = response.body();
                    tflArrivals.sort((a, b) -> Integer.compare(a.timeToStation, b.timeToStation));

                    String fromNaptan = tflArrivals.get(0).naptanId;
                    String toNaptan = tflArrivals.get(tflArrivals.size() - 1).naptanId;

                    if (fromNaptan != null && toNaptan != null && !fromNaptan.equals(toNaptan)) {
                        enrichWithJourneyPlanner(tflApi, tflArrivals, fromNaptan, toNaptan);
                    } else {
                        renderTflTimeline(tflArrivals, null);
                    }
                } else {
                    fetchFallbackJourneyPlanner();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<TflVehicleArrival>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                swipeRefreshLayout.setRefreshing(false);
                fetchFallbackJourneyPlanner();
            }
        });
    }

    private void fetchFallbackJourneyPlanner() {
        if (userOrigin == null || userDest == null || userOrigin.isEmpty() || userDest.isEmpty() || userDest.equals("Unknown")) {
            showTrueFallback();
            return;
        }

        showLoadingShimmer(true);
        String cleanOrigin = cleanBaseName(userOrigin);
        String cleanDest = cleanBaseName(userDest);

        String fromNaptan = StationHelper.getNaptanFromName(cleanOrigin);
        String toNaptan = StationHelper.getNaptanFromName(cleanDest);
        String fromQuery = (fromNaptan != null && !fromNaptan.isEmpty()) ? fromNaptan : cleanOrigin;
        String toQuery = (toNaptan != null && !toNaptan.isEmpty()) ? toNaptan : cleanDest;

        TflAPI tflApi = APIClient.getClient().create(TflAPI.class);

        tflApi.getJourneyResults(fromQuery, toQuery, false, null, null, null, null, "tube", null).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (isFinishing() || isDestroyed()) return;
                showLoadingShimmer(false);

                List<String> journeyPlannerStops = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null) {
                    extractStopsFromJson(response.body(), journeyPlannerStops);
                }

                if (!journeyPlannerStops.isEmpty()) {
                    renderStaticJourneyTimeline(journeyPlannerStops, fallbackOperatorName);
                } else {
                    showTrueFallback();
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                showLoadingShimmer(false);
                showTrueFallback();
            }
        });
    }

    private void extractStopsFromJson(JsonObject root, List<String> stopsOut) {
        try {
            if (!root.has("journeys")) return;
            JsonArray journeys = root.getAsJsonArray("journeys");
            if (journeys.isEmpty()) return;

            JsonObject journey = journeys.get(0).getAsJsonObject();
            if (!journey.has("legs")) return;
            JsonArray legs = journey.getAsJsonArray("legs");

            for (JsonElement legElem : legs) {
                JsonObject leg = legElem.getAsJsonObject();

                if (leg.has("mode") && leg.getAsJsonObject("mode").has("id")) {
                    String modeId = leg.getAsJsonObject("mode").get("id").getAsString();
                    if (modeId.matches("tube|elizabeth-line|overground|dlr|national-rail") && leg.has("routeOptions")) {
                        JsonArray routeOptions = leg.getAsJsonArray("routeOptions");
                        if (!routeOptions.isEmpty() && routeOptions.get(0).getAsJsonObject().has("name")) {
                            fallbackOperatorName = routeOptions.get(0).getAsJsonObject().get("name").getAsString();
                        }
                    }
                }
                if (leg.has("departurePoint") && leg.getAsJsonObject("departurePoint").has("commonName")) {
                    String name = cleanStationName(leg.getAsJsonObject("departurePoint").get("commonName").getAsString());
                    if (stopsOut.isEmpty() || !stopsOut.get(stopsOut.size() - 1).equals(name)) stopsOut.add(name);
                }

                if (leg.has("path") && leg.getAsJsonObject("path").has("stopPoints")) {
                    for (JsonElement elem : leg.getAsJsonObject("path").getAsJsonArray("stopPoints")) {
                        if (elem.getAsJsonObject().has("name")) {
                            String name = cleanStationName(elem.getAsJsonObject().get("name").getAsString());
                            if (stopsOut.isEmpty() || !stopsOut.get(stopsOut.size() - 1).equals(name)) stopsOut.add(name);
                        }
                    }
                }

                if (leg.has("arrivalPoint") && leg.getAsJsonObject("arrivalPoint").has("commonName")) {
                    String name = cleanStationName(leg.getAsJsonObject("arrivalPoint").get("commonName").getAsString());
                    if (stopsOut.isEmpty() || !stopsOut.get(stopsOut.size() - 1).equals(name)) stopsOut.add(name);
                }
            }
        } catch (Exception e) {
            Log.e("TfL_API", "Error parsing fallback Journey Planner path", e);
        }
    }

    private void renderStaticJourneyTimeline(List<String> stops, String operatorName) {
        if (cardErrorState != null) cardErrorState.setVisibility(View.GONE);
        if (cardCallingPoints != null) cardCallingPoints.setVisibility(View.VISIBLE);
        List<ServiceDetailsResponse.TimelinePoint> allPoints = new ArrayList<>();

        for (String stopName : stops) {
            ServiceDetailsResponse.TimelinePoint tp = new ServiceDetailsResponse.TimelinePoint();
            tp.locationName = stopName;
            tp.platform = "";
            tp.scheduledTime = "";
            tp.estimatedTime = "";
            tp.isCancelled = false;
            allPoints.add(tp);
        }

        setThemeByOperator(operatorName);
        setDestinationTitle(stops.get(0), stops.get(stops.size() - 1), null);

        int stopsCount = allPoints.size() - 1;
        tvHeaderOperator.setText(stopsCount + (stopsCount == 1 ? " stop • " : " stops • ") + operatorName);
        tvHeaderTime.setText("Frequent Service");
        tvHeaderStatus.setText("Live Tracker");
        tvHeaderStatus.setTextColor(Color.parseColor("#00875A"));
        tvHeaderStatus.setVisibility(View.VISIBLE);

        cardTrainDetails.setVisibility(View.GONE);
        if (titleCallingPoints != null) titleCallingPoints.setVisibility(View.VISIBLE);
        if (cardTflDisclaimer != null) cardTflDisclaimer.setVisibility(View.VISIBLE);
        recyclerTimeline.setVisibility(View.VISIBLE);

        String originParam = userOrigin != null && !userOrigin.isEmpty() ? userOrigin : stops.get(0);
        TimelineAdapter adapter = new TimelineAdapter(allPoints, TOCBrandHelper.getColourForToc(operatorName), originParam, userDest, null, null, crs -> {
            android.content.Intent intent = new android.content.Intent(ServiceDetailsActivity.this, MainActivity.class);
            intent.putExtra("OPEN_STATION_CRS", crs);
            startActivity(intent);
        });
        recyclerTimeline.setAdapter(adapter);
    }

    private void enrichWithJourneyPlanner(TflAPI tflApi, List<TflVehicleArrival> tflArrivals, String fromNaptan, String toNaptan) {
        tflApi.getJourneyResults(fromNaptan, toNaptan, false, null, null, null, null, "tube", null).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (isFinishing() || isDestroyed()) return;
                List<String> journeyPlannerStops = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null) {
                    extractStopsFromJson(response.body(), journeyPlannerStops);
                }
                renderTflTimeline(tflArrivals, journeyPlannerStops);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                renderTflTimeline(tflArrivals, null);
            }
        });
    }

    private void renderTflTimeline(List<TflVehicleArrival> tflArrivals, List<String> journeyPlannerStops) {
        if (cardErrorState != null) cardErrorState.setVisibility(View.GONE);
        if (cardCallingPoints != null) cardCallingPoints.setVisibility(View.VISIBLE);

        SimpleDateFormat displayFormat = new SimpleDateFormat("HH:mm", Locale.UK);
        displayFormat.setTimeZone(TimeZone.getTimeZone("Europe/London"));
        Map<String, TflVehicleArrival> arrivalMap = new HashMap<>();

        for (TflVehicleArrival arr : tflArrivals) {
            arrivalMap.put(cleanStationName(arr.stationName).toLowerCase(), arr);
        }

        List<ServiceDetailsResponse.TimelinePoint> allPoints = new ArrayList<>();
        String operatorName = tflArrivals.get(0).lineName;

        if (journeyPlannerStops != null && !journeyPlannerStops.isEmpty()) {
            for (String stopName : journeyPlannerStops) {
                ServiceDetailsResponse.TimelinePoint tp = new ServiceDetailsResponse.TimelinePoint();
                tp.locationName = stopName;
                TflVehicleArrival matchedArr = arrivalMap.get(stopName.toLowerCase());

                if (matchedArr != null) {
                    tp.platform = matchedArr.platformName;
                    tp.scheduledTime = parseISOToTime(matchedArr.expectedArrival, displayFormat);
                    tp.estimatedTime = tp.scheduledTime;
                } else {
                    tp.platform = "";
                    tp.scheduledTime = "--:--";
                    tp.estimatedTime = "--:--";
                }
                allPoints.add(tp);
            }
        } else {
            for (TflVehicleArrival tflPoint : tflArrivals) {
                ServiceDetailsResponse.TimelinePoint tp = new ServiceDetailsResponse.TimelinePoint();
                tp.locationName = cleanStationName(tflPoint.stationName);
                tp.platform = tflPoint.platformName;
                tp.scheduledTime = parseISOToTime(tflPoint.expectedArrival, displayFormat);
                tp.estimatedTime = tp.scheduledTime;
                allPoints.add(tp);
            }
        }

        setThemeByOperator(operatorName);

        String originName = userOrigin != null && !userOrigin.isEmpty() ? userOrigin : allPoints.get(0).locationName;
        String destName = allPoints.get(allPoints.size() - 1).locationName;
        setDestinationTitle(originName, destName, originName.equals(destName) ? originName : null);

        int stopsCount = allPoints.size() - 1;
        String durationStr = calculateDuration(allPoints.get(0).scheduledTime, allPoints.get(allPoints.size() - 1).scheduledTime);
        String subInfoText = (!durationStr.isEmpty() ? durationStr + " • " : "") + stopsCount + (stopsCount == 1 ? " stop • " : " stops • ") + operatorName;

        tvHeaderOperator.setText(subInfoText);
        tvHeaderTime.setText(allPoints.get(0).scheduledTime);
        tvHeaderStatus.setText("Live");
        tvHeaderStatus.setTextColor(Color.parseColor("#00875A"));
        tvHeaderStatus.setVisibility(View.VISIBLE);
        cardTrainDetails.setVisibility(View.GONE);

        if (titleCallingPoints != null) titleCallingPoints.setVisibility(View.VISIBLE);
        recyclerTimeline.setVisibility(View.VISIBLE);

        String originParam = userOrigin != null && !userOrigin.isEmpty() ? userOrigin : allPoints.get(0).locationName;
        TimelineAdapter adapter = new TimelineAdapter(allPoints, TOCBrandHelper.getColourForToc(operatorName), originParam, userDest, null, null, crs -> {
            android.content.Intent intent = new android.content.Intent(ServiceDetailsActivity.this, MainActivity.class);
            intent.putExtra("OPEN_STATION_CRS", crs);
            startActivity(intent);
        });

        recyclerTimeline.setAdapter(adapter);
    }

    private void setThemeByOperator(String operatorName) {
        int brandColour = TOCBrandHelper.getColourForToc(operatorName);
        if (topHeaderBar != null) {
            topHeaderBar.setBackgroundColor(brandColour);
            boolean isLightColour = ColorUtils.calculateLuminance(brandColour) > 0.5;
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(isLightColour);
        }
    }

    private void setDestinationTitle(String origin, String dest, String directOverride) {
        if (directOverride != null) {
            tvHeaderDestination.setText(directOverride);
        } else {
            tvHeaderDestination.setText(origin + " to " + dest);
            tvHeaderDestination.post(() -> {
                if (tvHeaderDestination.getLineCount() > 1) {
                    tvHeaderDestination.setText(origin + "\nto " + dest);
                }
            });
        }
    }

    private String cleanBaseName(String name) {
        if (name == null) return "";
        if (name.contains(" via ")) name = name.substring(0, name.indexOf(" via "));
        return cleanStationName(name);
    }

    private String cleanStationName(String name) {
        if (name == null) return "Unknown";
        return name.replace(" Underground Station", "")
                .replace(" DLR Station", "")
                .replace(" Rail Station", "")
                .trim();
    }

    private int timeToMins(String time) {
        if (time == null || !time.contains(":")) return -1;
        try {
            String[] parts = time.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) { return -1; }
    }

    private String calculateDuration(String start, String end) {
        int sMins = timeToMins(start);
        int eMins = timeToMins(end);
        if (sMins == -1 || eMins == -1) return "";

        int diff = eMins - sMins;
        if (diff < 0) diff += 1440;

        int h = diff / 60;
        int m = diff % 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }

    private String parseISOToTime(String arrivalString, SimpleDateFormat displayFormat) {
        if (arrivalString == null || arrivalString.isEmpty()) return "--:--";
        try {
            SimpleDateFormat tflFormat = new SimpleDateFormat(arrivalString.contains(".") ? "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" : "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.UK);
            tflFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = tflFormat.parse(arrivalString);
            return date != null ? displayFormat.format(date) : "--:--";
        } catch (Exception e) {
            return "--:--";
        }
    }

    private void checkDelayRepayEligibility(String schTime, String estTime, String operatorName, boolean isCancelled) {
        View cardDelayRepay = findViewById(R.id.cardContextualDelayRepay);
        TextView tvDelayText = findViewById(R.id.tvDelayRepayContextText);

        if (cardDelayRepay == null || schTime == null || estTime == null) return;
        String delayRepayUrl = TOCBrandHelper.getDelayRepayUrlForName(operatorName);
        if (delayRepayUrl == null || delayRepayUrl.isEmpty()) {
            cardDelayRepay.setVisibility(View.GONE);
            return;
        }

        boolean isEligible = false;

        if (isCancelled) {
            isEligible = true;
            tvDelayText.setText("This train has been cancelled. Tap to claim from " + operatorName + ".");
        } else if (estTime.equalsIgnoreCase("Delayed")) {
            isEligible = true;
            tvDelayText.setText("This train is heavily delayed. \nYou may be able to create a delay-repay claim from " + operatorName + ".");
        } else {
            int sMins = timeToMins(schTime);
            int eMins = timeToMins(estTime);
            if (sMins != -1 && eMins != -1) {
                int diff = eMins - sMins;
                if (diff < -720) diff += 1440;

                if (diff >= TOCBrandHelper.getDelayRepayMinTimeForName(operatorName)) {
                    isEligible = true;
                    tvDelayText.setText("This train is delayed by " + diff + " mins. \nYou may be able to create a delay-repay claim from " + operatorName + ".");
                }
            }
        }

        if (isEligible) {
            cardDelayRepay.setVisibility(View.VISIBLE);
            cardDelayRepay.setOnClickListener(v -> new CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(delayRepayUrl)));
        } else {
            cardDelayRepay.setVisibility(View.GONE);
        }
    }

    private void showTrueFallback() {
        if (userOrigin != null && userDest != null && !userDest.isEmpty()) {
            tvHeaderDestination.setText(userOrigin + " to " + userDest);
        } else if (userOrigin != null) {
            tvHeaderDestination.setText(userOrigin + " Departures");
        } else {
            tvHeaderDestination.setText("Unknown Service");
        }

        tvHeaderOperator.setText("");
        tvHeaderTime.setText("");
        tvHeaderStatus.setText("Unavailable");
        tvHeaderStatus.setTextColor(Color.parseColor("#DE350B"));
        tvHeaderStatus.setVisibility(View.VISIBLE);

        if (titleCallingPoints != null) titleCallingPoints.setVisibility(View.GONE);
        if (cardTflDisclaimer != null) cardTflDisclaimer.setVisibility(View.GONE);
        recyclerTimeline.setVisibility(View.GONE);
        if (cardTrainDetails != null) cardTrainDetails.setVisibility(View.GONE);
        if (cardCallingPoints != null) cardCallingPoints.setVisibility(View.GONE);
        if (cardErrorState != null) cardErrorState.setVisibility(View.VISIBLE);
    }

    private void showLoadingShimmer(boolean isLoading) {
        if (shimmerTimeline == null) return;

        if (isLoading) {
            shimmerTimeline.setVisibility(View.VISIBLE);
            shimmerTimeline.startShimmer();
            recyclerTimeline.setVisibility(View.GONE);
            if (titleCallingPoints != null) titleCallingPoints.setVisibility(View.GONE);
            if (cardTflDisclaimer != null) cardTflDisclaimer.setVisibility(View.GONE);
            if (cardHeaderInfo != null) cardHeaderInfo.setVisibility(View.GONE);
            if (cardCallingPoints != null) cardCallingPoints.setVisibility(View.GONE);
        } else {
            shimmerTimeline.stopShimmer();
            shimmerTimeline.setVisibility(View.GONE);
            recyclerTimeline.setVisibility(View.VISIBLE);
            if (titleCallingPoints != null) titleCallingPoints.setVisibility(View.VISIBLE);
            if (cardHeaderInfo != null) cardHeaderInfo.setVisibility(View.VISIBLE);
            if (cardCallingPoints != null) cardCallingPoints.setVisibility(View.VISIBLE);
        }
    }
}