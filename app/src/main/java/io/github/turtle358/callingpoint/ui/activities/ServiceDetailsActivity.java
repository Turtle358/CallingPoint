package io.github.turtle358.callingpoint.ui.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

import io.github.turtle358.callingpoint.ui.adapters.CoachAdapter;
import io.github.turtle358.callingpoint.data.PinnedDataManager;
import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;
import io.github.turtle358.callingpoint.ui.adapters.TimelineAdapter;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.NationalRailAPI;
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse;
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

    private View topHeaderBar, cardTrainDetails;
    private String serviceId = "";
    private String userOrigin;
    private String userDest;

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

        cardTrainDetails = findViewById(R.id.cardTrainDetails);
        tvCoachCapacity = findViewById(R.id.tvCoachCapacity);
        recyclerCoaches = findViewById(R.id.recyclerCoaches);
        btnPinService = findViewById(R.id.btnPinService);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshServiceDetails);
        recyclerTimeline = findViewById(R.id.recyclerTimeline);

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
                    ServiceDetailsResponse data = response.body();

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
        android.util.Log.d("TfL_API", "Fetching TfL vehicle arrivals for ID: " + vehicleId);

        io.github.turtle358.callingpoint.api.TflAPI tflApi = APIClient.getClient().create(io.github.turtle358.callingpoint.api.TflAPI.class);

        tflApi.getVehicleArrivals(vehicleId).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<io.github.turtle358.callingpoint.models.TflVehicleArrival>> call, @NonNull Response<List<io.github.turtle358.callingpoint.models.TflVehicleArrival>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    List<io.github.turtle358.callingpoint.models.TflVehicleArrival> tflArrivals = response.body();
                    tflArrivals.sort((a, b) -> Integer.compare(a.timeToStation, b.timeToStation));
                    List<ServiceDetailsResponse.TimelinePoint> allPoints = new ArrayList<>();

                    java.text.SimpleDateFormat displayFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK);
                    displayFormat.setTimeZone(java.util.TimeZone.getTimeZone("Europe/London"));

                    String operatorName = tflArrivals.get(0).lineName;

                    for (io.github.turtle358.callingpoint.models.TflVehicleArrival tflPoint : tflArrivals) {
                        ServiceDetailsResponse.TimelinePoint tp = new ServiceDetailsResponse.TimelinePoint();
                        tp.locationName = tflPoint.stationName.replace(" Underground Station", "").replace(" DLR Station", "");
                        tp.platform = tflPoint.platformName;
                        tp.isCancelled = false;

                        try {
                            String arrivalString = tflPoint.expectedArrival;
                            java.text.SimpleDateFormat tflFormat;
                            if (arrivalString != null && arrivalString.contains(".")) {
                                tflFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.UK);
                            } else {
                                tflFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.UK);
                            }
                            tflFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

                            java.util.Date date = tflFormat.parse(arrivalString);
                            if (date != null) {
                                tp.scheduledTime = displayFormat.format(date);
                                tp.estimatedTime = tp.scheduledTime;
                            }
                        } catch (Exception e) {
                            tp.scheduledTime = "";
                            tp.estimatedTime = "";
                        }
                        allPoints.add(tp);
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

                    String originParam = userOrigin != null && !userOrigin.isEmpty() ? userOrigin : allPoints.get(0).locationName;
                    TimelineAdapter adapter = new TimelineAdapter(allPoints, brandColour, originParam, userDest, crs -> {
                        android.content.Intent intent = new android.content.Intent(ServiceDetailsActivity.this, MainActivity.class);
                        intent.putExtra("OPEN_STATION_CRS", crs);
                        startActivity(intent);
                    });

                    recyclerTimeline.setAdapter(adapter);

                } else {
                    android.util.Log.e("TfL_API", "TfL Response Unsuccessful or Empty. HTTP Code: " + response.code());
                    showTrueFallback();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<io.github.turtle358.callingpoint.models.TflVehicleArrival>> call, @NonNull Throwable t) {
                android.util.Log.e("TfL_API", "Network error fetching TfL Vehicle", t);
                showTrueFallback();
            }
        });
    }

    private void showTrueFallback() {
        if (userOrigin != null && userDest != null && !userDest.isEmpty()) {
            tvHeaderDestination.setText(userOrigin + " to " + userDest);
        } else if (userOrigin != null) {
            tvHeaderDestination.setText(userOrigin + " Departures");
        } else {
            tvHeaderDestination.setText("London Underground");
        }

        tvHeaderOperator.setText("Transport for London");
        tvHeaderTime.setText("Live Tracker");
        tvHeaderPlatform.setText("");

        tvHeaderStatus.setText("Good Service");
        tvHeaderStatus.setTextColor(Color.parseColor("#00875A"));
        tvHeaderStatus.setVisibility(View.VISIBLE);

        recyclerTimeline.setVisibility(View.GONE);

        cardTrainDetails.setVisibility(View.VISIBLE);
        TextView tvCardTitle = findViewById(R.id.tvCoachCapacity);

        tvCardTitle.setText("Live calling points are not broadcast for Underground services. Please refer to station maps for routing.");
        tvCardTitle.setTextSize(14f);
        tvCardTitle.setTextColor(Color.parseColor("#666666"));

        recyclerCoaches.setVisibility(View.GONE);
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
}