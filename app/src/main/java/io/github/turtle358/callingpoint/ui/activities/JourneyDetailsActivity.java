package io.github.turtle358.callingpoint.ui.activities;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.maplibre.android.MapLibre;
import org.maplibre.android.maps.MapView;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.NationalRailAPI;
import io.github.turtle358.callingpoint.models.JourneyLeg;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse;
import io.github.turtle358.callingpoint.models.StationDepartureResponse;
import io.github.turtle358.callingpoint.ui.adapters.JourneyLegAdapter;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.LiveLocationEngine;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.PinnedJourneyManager;
import io.github.turtle358.callingpoint.utils.StationHelper;
import io.github.turtle358.callingpoint.utils.SharedJourneyLink;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;
import io.github.turtle358.callingpoint.utils.CallingPointMapNetwork;
import io.github.turtle358.callingpoint.utils.MapLibreWalkingMap;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class JourneyDetailsActivity extends AppCompatActivity {

    private JourneyLegAdapter adapter;
    private final Map<String, ServiceDetailsResponse> liveDataMap = new HashMap<>();
    private MapView mapView;
    private final android.os.Handler autoRefreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable autoRefreshRunnable;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MapLibre.getInstance(getApplicationContext());

        CallingPointMapNetwork.initialize(getApplicationContext());

        setContentView(R.layout.activity_journey_details);

        mapView = findViewById(R.id.mapViewTransfer);
        if (mapView != null) mapView.onCreate(savedInstanceState);

        View topHeaderBar = findViewById(R.id.topHeaderBar);
        if (topHeaderBar != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(topHeaderBar, (v, insets) -> {
                v.getLayoutParams().height = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
                v.requestLayout();
                return insets;
            });
        }

        JourneyOption journey = (JourneyOption) getIntent().getSerializableExtra("JOURNEY_OPTION");

        if (journey == null || journey.legs == null || journey.legs.isEmpty()) {
            finish();
            return;
        }

        TextView tvTitle = findViewById(R.id.tvDetailTitle);
        TextView tvDate = findViewById(R.id.tvJourneyDate);
        TextView tvSummary = findViewById(R.id.tvDetailSummary);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        String primaryBrand = "walk";
        for (JourneyLeg leg : journey.legs) {
            String op = leg.operatorName;
            if (op == null || op.trim().isEmpty() || op.toLowerCase().contains("walk")) {
                continue;
            }
            if ("BUS".equalsIgnoreCase(leg.transportMode) || (op.matches("^[a-zA-Z]{0,2}\\d+[a-zA-Z]?$") && !op.equalsIgnoreCase("c2c"))) {
                primaryBrand = "bus";
            } else {
                primaryBrand = op;
            }
            break;
        }

        int brandColour = TOCBrandHelper.getColourForToc(primaryBrand);

        if (topHeaderBar != null) {
            topHeaderBar.setBackgroundColor(brandColour);
        }

        boolean isLightColour = ColorUtils.calculateLuminance(brandColour) > 0.5;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(isLightColour);

        String origin = journey.legs.get(0).fromStationName;
        String dest = journey.legs.get(journey.legs.size() - 1).toStationName;

        String titleText = origin + " to " + dest;
        tvTitle.setText(titleText);

        tvTitle.post(() -> {
            if (tvTitle.getLineCount() > 1) {
                tvTitle.setText(origin + "\nto " + dest);
            }
        });

        tvDate.setText(getFormattedDate(journey.departureDate));

        String summary = journey.departTime + " — " + journey.arriveTime + "  •  " + journey.duration;
        tvSummary.setText(summary);

        setupPinButton(journey);
        setupShareButton(journey);

        androidx.cardview.widget.CardView cardTimeline = findViewById(R.id.cardTimeline);
        androidx.cardview.widget.CardView cardWalkOnlyMap = findViewById(R.id.cardWalkOnlyMap);
        RecyclerView recyclerJourneyLegs = findViewById(R.id.recyclerJourneyLegs);

        boolean isWalkOnly = journey.legs.size() == 1 && "TUBE/WALK".equals(journey.legs.get(0).transportMode);

        if (isWalkOnly) {
            cardTimeline.setVisibility(View.GONE);
            cardWalkOnlyMap.setVisibility(View.VISIBLE);

            mapView.setOnTouchListener((v, event) -> {
                int action = event.getActionMasked();
                if (v.getParent() != null) {
                    if (action == android.view.MotionEvent.ACTION_DOWN
                            || action == android.view.MotionEvent.ACTION_MOVE) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    } else if (action == android.view.MotionEvent.ACTION_UP
                            || action == android.view.MotionEvent.ACTION_CANCEL) {
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }
                return false;
            });

            MapLibreWalkingMap.render(this, mapView, origin, dest);

            View fabMap = findViewById(R.id.fabOpenExternalMap);
            if (fabMap != null) {
                fabMap.setVisibility(View.GONE);
            }

        } else {
            cardTimeline.setVisibility(View.VISIBLE);
            cardWalkOnlyMap.setVisibility(View.GONE);

            recyclerJourneyLegs.setLayoutManager(new LinearLayoutManager(this));
            adapter = new JourneyLegAdapter(journey.legs);
            recyclerJourneyLegs.setAdapter(adapter);

            fetchLiveServiceDetails(journey);
        }
    }

    private String getFormattedDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "Today";
        try {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.UK);
            java.util.Date parsedDate = format.parse(isoDate);

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(parsedDate);
            java.util.Calendar today = java.util.Calendar.getInstance();
            java.util.Calendar tomorrow = java.util.Calendar.getInstance();
            tomorrow.add(java.util.Calendar.DAY_OF_YEAR, 1);

            if (cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) && cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)) {
                return "Today";
            } else if (cal.get(java.util.Calendar.YEAR) == tomorrow.get(java.util.Calendar.YEAR) && cal.get(java.util.Calendar.DAY_OF_YEAR) == tomorrow.get(java.util.Calendar.DAY_OF_YEAR)) {
                return "Tomorrow";
            } else {
                java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.UK);
                return outputFormat.format(parsedDate);
            }
        } catch (Exception e) {
            return "Today";
        }
    }

    private boolean isJourneyToday(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return true;
        try {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.UK);
            java.util.Date parsedDate = format.parse(isoDate);

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(parsedDate);

            java.util.Calendar today = java.util.Calendar.getInstance();

            return cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                    cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR);
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
        if (adapter != null) adapter.onHostStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
        if (adapter != null) adapter.onHostResume();

        JourneyOption journey = (JourneyOption) getIntent().getSerializableExtra("JOURNEY_OPTION");
        if (journey != null && journey.legs != null && !journey.legs.isEmpty()) {
            fetchLiveServiceDetails(journey);

            autoRefreshRunnable = new Runnable() {
                @Override
                public void run() {
                    fetchLiveServiceDetails(journey);
                    autoRefreshHandler.postDelayed(this, 30 * 1000);
                }
            };

            autoRefreshHandler.postDelayed(autoRefreshRunnable, 30 * 1000);
        }
    }

    @Override
    public void onPause() {
        if (adapter != null) adapter.onHostPause();
        if (mapView != null) mapView.onPause();
        if (autoRefreshRunnable != null) autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (adapter != null) adapter.onHostStop();
        if (mapView != null) mapView.onStop();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (adapter != null) adapter.onHostDestroy();
        if (mapView != null) mapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }

    private void setupShareButton(JourneyOption journey) {
        ImageButton btnShareJourney = findViewById(R.id.btnShareJourney);
        btnShareJourney.setOnClickListener(v -> {
            try {
                String link = SharedJourneyLink.create(journey);
                String journeyShare = "Check out my journey with CallingPoint:\n\n" + link;
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "CallingPoint shared journey");
                shareIntent.putExtra(Intent.EXTRA_TEXT, journeyShare);
                startActivity(Intent.createChooser(shareIntent, "Share journey"));
            } catch (Exception e) {
                Toast.makeText(this, "Unable to share this journey: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupPinButton(JourneyOption journey) {
        ImageButton btnPinJourney = findViewById(R.id.btnPinJourney);
        PinnedJourneyManager pinnedManager = new PinnedJourneyManager(this);

        boolean isPinned = pinnedManager.isJourneyPinned(journey);
        btnPinJourney.setImageResource(isPinned ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);

        btnPinJourney.setOnClickListener(v -> {
            pinnedManager.toggleJourneyPin(journey);
            boolean isNowPinned = pinnedManager.isJourneyPinned(journey);
            btnPinJourney.setImageResource(isNowPinned ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
            Toast.makeText(this, isNowPinned ? "Journey Saved!" : "Journey Removed", Toast.LENGTH_SHORT).show();
        });
    }

    private void fetchLiveServiceDetails(JourneyOption journey) {
        if (!isJourneyToday(journey.departureDate)) {
            return;
        }

        for (JourneyLeg leg : journey.legs) {
            if ("TRAIN".equals(leg.transportMode)) {
                if (leg.serviceId != null) {
                    fetchDetailsForLeg(leg, journey);
                } else {
                    long now = System.currentTimeMillis();
                    if (now - leg.lastMatchAttemptTime > 5 * 60 * 1000) {
                        leg.lastMatchAttemptTime = now;
                        updatePinnedJourneyIfPinned(journey);
                        attemptMatchForLeg(leg, journey);
                    }
                }
            }
        }
    }

    private void fetchDetailsForLeg(JourneyLeg leg, JourneyOption journey) {
        NationalRailAPI api = APIClient.getClient().create(NationalRailAPI.class);
        api.getServiceDetails(leg.serviceId).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ServiceDetailsResponse> call, @NonNull Response<ServiceDetailsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ServiceDetailsResponse details = response.body();
                    liveDataMap.put(leg.serviceId, details);

                    List<JourneyLeg.Stop> fullStops = extractStopsFromDetails(details, leg.fromStationName, leg.toStationName);
                    if (!fullStops.isEmpty()) {
                        leg.intermediateStops = fullStops;
                    }

                    leg.liveLocation = LiveLocationEngine.getLiveLocation(details);
                    leg.lastCheckedLiveTime = String.valueOf(System.currentTimeMillis());

                    updatePinnedJourneyIfPinned(journey);

                    if (adapter != null) {
                        adapter.updateLiveData(liveDataMap);
                    }

                    TextView tvLastUpdated = findViewById(R.id.tvLastUpdated);
                    if (tvLastUpdated != null) {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.UK);
                        tvLastUpdated.setText("Last updated: " + sdf.format(new java.util.Date()));
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ServiceDetailsResponse> call, @NonNull Throwable t) {
            }
        });
    }

    private List<JourneyLeg.Stop> extractStopsFromDetails(ServiceDetailsResponse details, String fromStation, String toStation) {
        List<JourneyLeg.Stop> stops = new ArrayList<>();
        if (details == null) return stops;

        List<ServiceDetailsResponse.TimelinePoint> allPoints = new ArrayList<>();
        if (details.previousCallingPoints != null && !details.previousCallingPoints.isEmpty()) {
            allPoints.addAll(details.previousCallingPoints.get(0).callingPoint);
        }

        ServiceDetailsResponse.TimelinePoint current = new ServiceDetailsResponse.TimelinePoint();
        current.locationName = details.locationName;
        current.scheduledTime = details.scheduledDepartureTime != null ? details.scheduledDepartureTime : details.scheduledArrivalTime;
        allPoints.add(current);

        if (details.subsequentCallingPoints != null && !details.subsequentCallingPoints.isEmpty()) {
            allPoints.addAll(details.subsequentCallingPoints.get(0).callingPoint);
        }

        boolean recording = false;
        for (ServiceDetailsResponse.TimelinePoint pt : allPoints) {
            if (pt.locationName == null) continue;
            if (pt.locationName.equalsIgnoreCase(toStation)) {
                break;
            }
            if (recording) {
                String schTime = pt.scheduledTime != null ? pt.scheduledTime : "--:--";
                stops.add(new JourneyLeg.Stop(pt.locationName, schTime));
            }
            if (pt.locationName.equalsIgnoreCase(fromStation)) {
                recording = true;
            }
        }
        return stops;
    }

    private void attemptMatchForLeg(JourneyLeg leg, JourneyOption journey) {
        String fromCrs = StationHelper.getCrsFromName(leg.fromStationName);
        String toCrs = StationHelper.getCrsFromName(leg.toStationName);
        if (fromCrs == null || toCrs == null) return;

        NationalRailAPI api = APIClient.getClient().create(NationalRailAPI.class);
        int timeOffset = calculateTimeOffset(leg.departureTime);

        api.getStationDepartures(fromCrs, 40, toCrs, "to", timeOffset, 120).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<StationDepartureResponse> call, @NonNull Response<StationDepartureResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().trainServices != null) {
                    for (StationDepartureResponse.TrainService service : response.body().trainServices) {
                        if (service.scheduledDepartureTime != null && isTimeMatching(service.scheduledDepartureTime, leg.departureTime, 2)) {
                            leg.serviceId = service.serviceID;
                            if (service.operatorName != null) {
                                io.github.turtle358.callingpoint.models.Toc matchedToc = TOCBrandHelper.getTocByName(service.operatorName);
                                if (matchedToc != null) {
                                    leg.operatorName = matchedToc.name;
                                }
                            }

                            updatePinnedJourneyIfPinned(journey);
                            fetchDetailsForLeg(leg, journey);
                            break;
                        }
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<StationDepartureResponse> call, @NonNull Throwable t) {}
        });
    }

    private void updatePinnedJourneyIfPinned(JourneyOption journey) {
        PinnedJourneyManager pinnedManager = new PinnedJourneyManager(this);
        if (pinnedManager.isJourneyPinned(journey)) {
            pinnedManager.updateJourney(journey);
        }
    }

    private int calculateTimeOffset(String departureTimeStr) {
        if (departureTimeStr == null || !departureTimeStr.contains(":")) return 0;
        try {
            String[] parts = departureTimeStr.split(":");
            int depMins = Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());

            java.util.Calendar now = java.util.Calendar.getInstance();
            int currentMins = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);

            int diff = depMins - currentMins;
            int offset = diff - 10;
            return Math.max(-120, Math.min(120, offset));
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isTimeMatching(String time1, String time2, int toleranceMins) {
        try {
            String[] p1 = time1.trim().split(":");
            String[] p2 = time2.trim().split(":");

            int m1 = Integer.parseInt(p1[0].trim()) * 60 + Integer.parseInt(p1[1].trim());
            int m2 = Integer.parseInt(p2[0].trim()) * 60 + Integer.parseInt(p2[1].trim());

            return Math.abs(m1 - m2) <= toleranceMins;
        } catch (Exception e) {
            return time1.trim().equalsIgnoreCase(time2.trim());
        }
    }

}