package io.github.turtle358.callingpoint.ui.activities;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_service_details);

        topHeaderBar = findViewById(R.id.topHeaderBar);
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

        serviceId = getIntent().getStringExtra("SERVICE_ID");
        if (serviceId != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> fetchServiceDetails(serviceId));
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

                    List<ServiceDetailsResponse.TimelinePoint> allPoints = new ArrayList<>();
                    if (data.previousCallingPoints != null && !data.previousCallingPoints.isEmpty()) {
                        allPoints.addAll(data.previousCallingPoints.get(0).callingPoint);
                    }

                    String schTime = data.std != null ? data.std : (data.sta != null ? data.sta : "");
                    String estTime = data.etd != null ? data.etd : (data.eta != null ? data.eta : "");

                    ServiceDetailsResponse.TimelinePoint currentStation = new ServiceDetailsResponse.TimelinePoint();
                    currentStation.locationName = data.locationName != null ? data.locationName : "Current Station";
                    currentStation.scheduledTime = schTime;
                    currentStation.estimatedTime = estTime;
                    currentStation.isCancelled = "Cancelled".equalsIgnoreCase(estTime);
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
                        tvHeaderDestination.setText(originName + " to " + destName);
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
                    } else if ("Cancelled".equalsIgnoreCase(estTime)) {
                        tvHeaderStatus.setTextColor(Color.parseColor("#DE350B"));
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

                    TimelineAdapter adapter = new TimelineAdapter(allPoints, brandColour, data.locationName, crs -> {
                        android.content.Intent intent = new android.content.Intent(ServiceDetailsActivity.this, MainActivity.class);
                        intent.putExtra("OPEN_STATION_CRS", crs);
                        startActivity(intent);
                    });

                    recyclerTimeline.setAdapter(adapter);

                } else {
                    Toast.makeText(ServiceDetailsActivity.this, "Failed to load details.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ServiceDetailsResponse> call, @NonNull Throwable t) {
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(ServiceDetailsActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
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
}