package io.github.turtle358.callingpoint.ui.activities;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
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

    private RecyclerView recyclerTimeline;
    private RecyclerView recyclerCoaches;
    private SwipeRefreshLayout swipeRefreshLayout;

    private TextView tvHeaderDestination, tvHeaderTime, tvHeaderStatus;
    private TextView tvHeaderPlatform, tvHeaderOperator, tvCoachCapacity;
    private ImageButton btnPinService;

    private View topHeaderBar, cardTrainDetails, titleCallingPoints, cardTflDisclaimer, cardHeaderInfo, cardCallingPoints;
    private ShimmerFrameLayout shimmerTimeline;
    private String serviceId = "";
    private String userOrigin;
    private String userDest;
    private View cardErrorState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service_details);

        userOrigin = getIntent().getStringExtra("USER_ORIGIN");
        userDest = getIntent().getStringExtra("USER_DESTINATION");

        topHeaderBar = findViewById(R.id.topHeaderBar);
        if (topHeaderBar != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(topHeaderBar, (v, insets) -> {
                int statusBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
                v.getLayoutParams().height = statusBarHeight;
                v.requestLayout();
                return insets;
            });
        }

        tvHeaderDestination = findViewById(R.id.tvHeaderDestination);
        tvHeaderTime = findViewById(R.id.tvHeaderTime);
        tvHeaderStatus = findViewById(R.id.tvHeaderStatus);
        tvHeaderPlatform = findViewById(R.id.tvHeaderPlatform);
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

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        serviceId = getIntent().getStringExtra("SERVICE_ID");
        if (serviceId != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                fetchServiceDetails(serviceId);
                swipeRefreshLayout.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            });
            fetchServiceDetails(serviceId);
        } else {
            Toast.makeText(this, "Error: No Service ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

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
                swipeRefreshLayout.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    if (cardErrorState != null) cardErrorState.setVisibility(View.GONE);
                    if (cardCallingPoints != null) cardCallingPoints.setVisibility(View.VISIBLE);
                    if (recyclerTimeline != null) recyclerTimeline.setVisibility(View.VISIBLE);
                    ServiceDetailsResponse data = response.body();

                    if (btnPinService != null) {
                        btnPinService.setVisibility(View.VISIBLE);
                    }
                    if (cardTflDisclaimer != null) cardTflDisclaimer.setVisibility(View.GONE);

                    String operatorName = data.operator != null ? data.operator : "Unknown";
                    int brandColour = TOCBrandHelper.getColourForToc(operatorName);

                    if (topHeaderBar != null) {
                        topHeaderBar.setBackgroundColor(brandColour);
                    }

                    boolean isLightColour = ColorUtils.calculateLuminance(brandColour) > 0.5;
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(isLightColour);

                    List<ServiceDetailsResponse.TimelinePoint> allPoints = new ArrayList<>();
                    if (data.previousCallingPoints != null && !data.previousCallingPoints.isEmpty()) {
                        allPoints.addAll(data.previousCallingPoints.get(0).callingPoint);
                    }

                    String schTime = data.std != null ? data.std : (data.sta != null ? data.sta : "");
                    String estTime = data.etd != null ? data.etd : (data.eta != null ? data.eta : "");

                    boolean isCancelled = data.isCancelled || "Cancelled".equalsIgnoreCase(estTime);
                    checkDelayRepayEligibility(schTime, estTime, operatorName, isCancelled);

                    ServiceDetailsResponse.TimelinePoint currentStation = new ServiceDetailsResponse.TimelinePoint();
                    currentStation.locationName = data.locationName != null ? data.locationName : "Current Station";
                    currentStation.scheduledTime = schTime;
                    currentStation.estimatedTime = estTime;
                    currentStation.isCancelled = isCancelled;
                    currentStation.platform = data.platform;

                    if (data.atd != null) currentStation.actualTime = data.atd;
                    else if (data.ata != null) currentStation.actualTime = data.ata;
                    allPoints.add(currentStation);

                    if (data.subsequentCallingPoints != null && !data.subsequentCallingPoints.isEmpty()) {
                        allPoints.addAll(data.subsequentCallingPoints.get(0).callingPoint);
                    }

                    String originName = allPoints.get(0).locationName;
                    String destName = allPoints.get(allPoints.size() - 1).locationName;

                    if (originName.equals(destName)) {
                        tvHeaderDestination.setText(currentStation.locationName);
                    } else {
                        String titleText = originName + " to " + destName;
                        tvHeaderDestination.setText(titleText);
                        tvHeaderDestination.post(() -> {
                            if (tvHeaderDestination.getLineCount() > 1) {
                                tvHeaderDestination.setText(originName + "\nto " + destName);
                            }
                        });
                    }

                    int stopsCount = allPoints.size() - 1;
                    String durationStr = calculateDuration(allPoints.get(0).scheduledTime, allPoints.get(allPoints.size() - 1).scheduledTime);

                    String subInfoText = "";
                    if (!durationStr.isEmpty()) subInfoText += durationStr + " • ";
                    subInfoText += stopsCount + (stopsCount == 1 ? " stop • " : " stops • ");
                    subInfoText += operatorName;

                    tvHeaderOperator.setText(subInfoText);
                    tvHeaderTime.setText(schTime);
                    tvHeaderPlatform.setText(data.platform != null ? "Platform " + data.platform : "");

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

                    if (data.formation != null && data.formation.coaches != null && !data.formation.coaches.isEmpty()) {
                        cardTrainDetails.setVisibility(View.VISIBLE);
                        tvCoachCapacity.setText(data.formation.coaches.size() + " coaches");
                        CoachAdapter coachAdapter = new CoachAdapter(data.formation.coaches);
                        recyclerCoaches.setAdapter(coachAdapter);
                    } else {
                        cardTrainDetails.setVisibility(View.GONE);
                    }

                    if (titleCallingPoints != null) titleCallingPoints.setVisibility(View.VISIBLE);
                    String originParam = userOrigin != null && !userOrigin.isEmpty() ? userOrigin : currentStation.locationName;

                    TimelineAdapter adapter = new TimelineAdapter(allPoints, brandColour, originParam, userDest, crs -> {
                        android.content.Intent intent = new android.content.Intent(ServiceDetailsActivity.this, MainActivity.class);
                        intent.putExtra("OPEN_STATION_CRS", crs);
                        startActivity(intent);
                    });

                    recyclerTimeline.setAdapter(adapter);

                } else {
                    fetchTflServiceDetails(serviceId);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ServiceDetailsResponse> call, @NonNull Throwable t) {
                swipeRefreshLayout.setRefreshing(false);
                fetchTflServiceDetails(serviceId);
            }
        });
    }

    private void fetchTflServiceDetails(String vehicleId) {
        Log.d("TfL_API", "Fetching TfL vehicle arrivals for ID: " + vehicleId);
        if (btnPinService != null) {
            btnPinService.setVisibility(View.GONE);
        }
        TflAPI tflApi = APIClient.getClient().create(TflAPI.class);

        tflApi.getVehicleArrivals(vehicleId).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<TflVehicleArrival>> call, @NonNull Response<List<TflVehicleArrival>> response) {
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
                    Log.e("TfL_API", "TfL Response Empty. Likely a National Rail dummy ID. Falling back to textual Journey Planner...");
                    fetchFallbackJourneyPlanner();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<TflVehicleArrival>> call, @NonNull Throwable t) {
                swipeRefreshLayout.setRefreshing(false);
                Log.e("TfL_API", "Network error fetching TfL Vehicle", t);
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

        String cleanOrigin = userOrigin;
        if (cleanOrigin.contains(" via ")) cleanOrigin = cleanOrigin.substring(0, cleanOrigin.indexOf(" via "));
        cleanOrigin = cleanOrigin.replace(" Underground Station", "")
                .replace(" DLR Station", "")
                .replace(" Rail Station", "")
                .trim();

        String cleanDest = userDest;
        if (cleanDest.contains(" via ")) cleanDest = cleanDest.substring(0, cleanDest.indexOf(" via "));
        cleanDest = cleanDest.replace(" Underground Station", "")
                .replace(" DLR Station", "")
                .replace(" Rail Station", "")
                .trim();

        String fromNaptan = StationHelper.getNaptanFromName(cleanOrigin);
        String toNaptan = StationHelper.getNaptanFromName(cleanDest);

        String fromQuery = (fromNaptan != null && !fromNaptan.isEmpty()) ? fromNaptan : cleanOrigin;
        String toQuery = (toNaptan != null && !toNaptan.isEmpty()) ? toNaptan : cleanDest;

        TflAPI tflApi = APIClient.getClient().create(TflAPI.class);

        tflApi.getJourneyResults(fromQuery, toQuery, false, null, null, null).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<String> journeyPlannerStops = new ArrayList<>();
                    String operatorName = "London Underground";

                    try {
                        JsonObject root = response.body();
                        if (root.has("journeys")) {
                            JsonArray journeys = root.getAsJsonArray("journeys");
                            if (journeys.size() > 0) {
                                JsonObject journey = journeys.get(0).getAsJsonObject();
                                if (journey.has("legs")) {
                                    JsonArray legs = journey.getAsJsonArray("legs");

                                    for (JsonElement legElem : legs) {
                                        JsonObject leg = legElem.getAsJsonObject();

                                        if (leg.has("mode") && leg.getAsJsonObject("mode").has("id")) {
                                            String modeId = leg.getAsJsonObject("mode").get("id").getAsString();
                                            if ("tube".equals(modeId) || "elizabeth-line".equals(modeId) || "overground".equals(modeId) || "dlr".equals(modeId) || "national-rail".equals(modeId)) {
                                                if (leg.has("routeOptions")) {
                                                    JsonArray routeOptions = leg.getAsJsonArray("routeOptions");
                                                    if (routeOptions.size() > 0) {
                                                        JsonObject option = routeOptions.get(0).getAsJsonObject();
                                                        if (option.has("name")) {
                                                            operatorName = option.get("name").getAsString();
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (leg.has("departurePoint") && leg.getAsJsonObject("departurePoint").has("commonName")) {
                                            String depName = leg.getAsJsonObject("departurePoint").get("commonName").getAsString()
                                                    .replace(" Underground Station", "")
                                                    .replace(" DLR Station", "")
                                                    .replace(" Rail Station", "");
                                            if (journeyPlannerStops.isEmpty() || !journeyPlannerStops.get(journeyPlannerStops.size() - 1).equals(depName)) {
                                                journeyPlannerStops.add(depName);
                                            }
                                        }

                                        if (leg.has("path")) {
                                            JsonObject path = leg.getAsJsonObject("path");
                                            if (path.has("stopPoints")) {
                                                JsonArray stopPoints = path.getAsJsonArray("stopPoints");
                                                for (JsonElement elem : stopPoints) {
                                                    JsonObject stopObj = elem.getAsJsonObject();
                                                    if (stopObj.has("name")) {
                                                        String name = stopObj.get("name").getAsString()
                                                                .replace(" Underground Station", "")
                                                                .replace(" DLR Station", "")
                                                                .replace(" Rail Station", "");

                                                        if (journeyPlannerStops.isEmpty() || !journeyPlannerStops.get(journeyPlannerStops.size() - 1).equals(name)) {
                                                            journeyPlannerStops.add(name);
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (leg.has("arrivalPoint") && leg.getAsJsonObject("arrivalPoint").has("commonName")) {
                                            String arrName = leg.getAsJsonObject("arrivalPoint").get("commonName").getAsString()
                                                    .replace(" Underground Station", "")
                                                    .replace(" DLR Station", "")
                                                    .replace(" Rail Station", "");
                                            if (journeyPlannerStops.isEmpty() || !journeyPlannerStops.get(journeyPlannerStops.size() - 1).equals(arrName)) {
                                                journeyPlannerStops.add(arrName);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e("TfL_API", "Error parsing fallback Journey Planner path", e);
                    }

                    showLoadingShimmer(false);
                    if (!journeyPlannerStops.isEmpty()) {
                        renderStaticJourneyTimeline(journeyPlannerStops, operatorName);
                    } else {
                        showTrueFallback();
                    }
                } else {
                    showLoadingShimmer(false);
                    showTrueFallback();
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                showLoadingShimmer(false);
                showTrueFallback();
            }
        });
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

        int brandColour = TOCBrandHelper.getColourForToc(operatorName);
        if (topHeaderBar != null) {
            topHeaderBar.setBackgroundColor(brandColour);
            boolean isLightColour = ColorUtils.calculateLuminance(brandColour) > 0.5;
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(isLightColour);
        }

        String originName = stops.get(0);
        String destName = stops.get(stops.size() - 1);

        tvHeaderDestination.setText(originName + " to " + destName);
        tvHeaderDestination.post(() -> {
            if (tvHeaderDestination.getLineCount() > 1) {
                tvHeaderDestination.setText(originName + "\nto " + destName);
            }
        });

        int stopsCount = allPoints.size() - 1;
        String subInfoText = stopsCount + (stopsCount == 1 ? " stop • " : " stops • ") + operatorName;

        tvHeaderOperator.setText(subInfoText);
        tvHeaderTime.setText("Frequent Service");
        tvHeaderPlatform.setText("");

        tvHeaderStatus.setText("Live Tracker");
        tvHeaderStatus.setTextColor(Color.parseColor("#00875A"));
        tvHeaderStatus.setVisibility(View.VISIBLE);

        cardTrainDetails.setVisibility(View.GONE);
        if (titleCallingPoints != null) titleCallingPoints.setVisibility(View.VISIBLE);

        if (cardTflDisclaimer != null) cardTflDisclaimer.setVisibility(View.VISIBLE);

        recyclerTimeline.setVisibility(View.VISIBLE);

        String originParam = userOrigin != null && !userOrigin.isEmpty() ? userOrigin : stops.get(0);
        TimelineAdapter adapter = new TimelineAdapter(allPoints, brandColour, originParam, userDest, crs -> {
            android.content.Intent intent = new android.content.Intent(ServiceDetailsActivity.this, MainActivity.class);
            intent.putExtra("OPEN_STATION_CRS", crs);
            startActivity(intent);
        });

        recyclerTimeline.setAdapter(adapter);
    }

    private void enrichWithJourneyPlanner(TflAPI tflApi, List<TflVehicleArrival> tflArrivals, String fromNaptan, String toNaptan) {
        tflApi.getJourneyResults(fromNaptan, toNaptan, false, null, null, null).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                List<String> journeyPlannerStops = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JsonObject root = response.body();
                        if (root.has("journeys")) {
                            JsonArray journeys = root.getAsJsonArray("journeys");
                            if (!journeys.isEmpty()) {
                                JsonObject journey = journeys.get(0).getAsJsonObject();
                                if (journey.has("legs")) {
                                    JsonArray legs = journey.getAsJsonArray("legs");
                                    if (!legs.isEmpty()) {
                                        JsonObject leg = legs.get(0).getAsJsonObject();
                                        if (leg.has("path")) {
                                            JsonObject path = leg.getAsJsonObject("path");
                                            if (path.has("stopPoints")) {
                                                JsonArray stopPoints = path.getAsJsonArray("stopPoints");
                                                for (JsonElement elem : stopPoints) {
                                                    JsonObject stopObj = elem.getAsJsonObject();
                                                    if (stopObj.has("name")) {
                                                        String name = stopObj.get("name").getAsString()
                                                                .replace(" Underground Station", "")
                                                                .replace(" DLR Station", "")
                                                                .replace(" Rail Station", "");
                                                        journeyPlannerStops.add(name);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e("TfL_API", "Error parsing Journey Planner path stop points", e);
                    }
                }
                renderTflTimeline(tflArrivals, journeyPlannerStops);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
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
            String cleanName = arr.stationName.replace(" Underground Station", "")
                    .replace(" DLR Station", "")
                    .replace(" Rail Station", "");
            arrivalMap.put(cleanName.toLowerCase(), arr);
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
                tp.locationName = tflPoint.stationName.replace(" Underground Station", "")
                        .replace(" DLR Station", "")
                        .replace(" Rail Station", "");
                tp.platform = tflPoint.platformName;
                tp.scheduledTime = parseISOToTime(tflPoint.expectedArrival, displayFormat);
                tp.estimatedTime = tp.scheduledTime;
                allPoints.add(tp);
            }
        }

        int brandColour = TOCBrandHelper.getColourForToc(operatorName);
        if (topHeaderBar != null) {
            topHeaderBar.setBackgroundColor(brandColour);
            boolean isLightColour = ColorUtils.calculateLuminance(brandColour) > 0.5;
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(isLightColour);
        }

        String originName = userOrigin != null && !userOrigin.isEmpty() ? userOrigin : allPoints.get(0).locationName;
        String destName = allPoints.get(allPoints.size() - 1).locationName;

        if (originName.equals(destName)) {
            tvHeaderDestination.setText(originName);
        } else {
            tvHeaderDestination.setText(originName + " to " + destName);
            tvHeaderDestination.post(() -> {
                if (tvHeaderDestination.getLineCount() > 1) {
                    tvHeaderDestination.setText(originName + "\nto " + destName);
                }
            });
        }

        int stopsCount = allPoints.size() - 1;
        String durationStr = calculateDuration(allPoints.get(0).scheduledTime, allPoints.get(allPoints.size() - 1).scheduledTime);
        String subInfoText = (!durationStr.isEmpty() ? durationStr + " • " : "") +
                stopsCount + (stopsCount == 1 ? " stop • " : " stops • ") + operatorName;

        tvHeaderOperator.setText(subInfoText);
        tvHeaderTime.setText(allPoints.get(0).scheduledTime);

        String rawPlatform = allPoints.get(0).platform != null ? allPoints.get(0).platform : "";
        if (rawPlatform.contains(" - ")) {
            String[] parts = rawPlatform.split(" - ");
            tvHeaderPlatform.setText(parts[parts.length - 1]);
        } else {
            tvHeaderPlatform.setText(rawPlatform);
        }

        tvHeaderStatus.setText("Live");
        tvHeaderStatus.setTextColor(Color.parseColor("#00875A"));
        tvHeaderStatus.setVisibility(View.VISIBLE);
        cardTrainDetails.setVisibility(View.GONE);
        if (titleCallingPoints != null) titleCallingPoints.setVisibility(View.VISIBLE);
        recyclerTimeline.setVisibility(View.VISIBLE);

        String originParam = userOrigin != null && !userOrigin.isEmpty() ? userOrigin : allPoints.get(0).locationName;
        TimelineAdapter adapter = new TimelineAdapter(allPoints, brandColour, originParam, userDest, crs -> {
            android.content.Intent intent = new android.content.Intent(ServiceDetailsActivity.this, MainActivity.class);
            intent.putExtra("OPEN_STATION_CRS", crs);
            startActivity(intent);
        });

        recyclerTimeline.setAdapter(adapter);
    }

    private String parseISOToTime(String arrivalString, SimpleDateFormat displayFormat) {
        if (arrivalString == null || arrivalString.isEmpty()) return "--:--";
        try {
            SimpleDateFormat tflFormat;
            if (arrivalString.contains(".")) {
                tflFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.UK);
            } else {
                tflFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.UK);
            }
            tflFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = tflFormat.parse(arrivalString);
            return date != null ? displayFormat.format(date) : "--:--";
        } catch (Exception e) {
            return "--:--";
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
        tvHeaderPlatform.setText("");

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

    private String calculateDuration(String start, String end) {
        if (start == null || end == null || !start.contains(":") || !end.contains(":")) return "";
        try {
            String[] sParts = start.split(":");
            String[] eParts = end.split(":");
            int sMins = Integer.parseInt(sParts[0]) * 60 + Integer.parseInt(sParts[1]);
            int eMins = Integer.parseInt(eParts[0]) * 60 + Integer.parseInt(eParts[1]);

            int diff = eMins - sMins;
            if (diff < 0) diff += 1440;

            int h = diff / 60;
            int m = diff % 60;

            if (h > 0) return h + "h " + m + "m";
            return m + "m";
        } catch (Exception e) {
            return "";
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
        } else if (estTime.contains(":")) {
            try {
                String[] sParts = schTime.split(":");
                String[] eParts = estTime.split(":");
                int sMins = Integer.parseInt(sParts[0]) * 60 + Integer.parseInt(sParts[1]);
                int eMins = Integer.parseInt(eParts[0]) * 60 + Integer.parseInt(eParts[1]);

                int diff = eMins - sMins;
                if (diff < -720) diff += 1440;

                if (diff >= TOCBrandHelper.getDelayRepayMinTimeForName(operatorName)) {
                    isEligible = true;
                    tvDelayText.setText("This train is delayed by " + diff + " mins. \nYou may be able to create a delay-repay claim from " + operatorName + ".");
                }
            } catch (Exception e) {
            }
        }

        if (isEligible) {
            cardDelayRepay.setVisibility(View.VISIBLE);
            cardDelayRepay.setOnClickListener(v -> {
                CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
                customTabsIntent.launchUrl(this, Uri.parse(delayRepayUrl));
            });
        } else {
            cardDelayRepay.setVisibility(View.GONE);
        }
    }

    private void showLoadingShimmer(boolean isLoading) {
        if (shimmerTimeline != null) {
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
}