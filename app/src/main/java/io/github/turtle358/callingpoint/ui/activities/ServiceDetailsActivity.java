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

    private TextView tvHeaderDestination, tvHeaderTime, tvHeaderStatus;
    private TextView tvHeaderPlatform, tvHeaderOperator, tvCoachCapacity;

    private View topHeaderBar, cardTrainDetails;

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

        recyclerCoaches.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        recyclerTimeline = findViewById(R.id.recyclerTimeline);
        recyclerTimeline.setLayoutManager(new LinearLayoutManager(this));

        String serviceId = getIntent().getStringExtra("SERVICE_ID");
        if (serviceId != null) {
            fetchServiceDetails(serviceId);
        } else {
            Toast.makeText(this, "Error: No Service ID provided", Toast.LENGTH_SHORT).show();
            finish();
        }
        ImageButton btnPinService = findViewById(R.id.btnPinService);
        PinnedDataManager pinnedManager = new PinnedDataManager(this);
        boolean isPinned = pinnedManager.isServicePinned(serviceId);
        btnPinService.setImageResource(isPinned ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
        btnPinService.setOnClickListener(v -> {
            pinnedManager.toggleServicePin(serviceId);

            boolean isNowPinned = pinnedManager.isServicePinned(serviceId);
            btnPinService.setImageResource(isNowPinned ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
            Toast.makeText(this, isNowPinned ? "Train Pinned!" : "Pin Removed", Toast.LENGTH_SHORT).show();
        });
    }

    private void fetchServiceDetails(String serviceId) {
        String key = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE).getString("SERVICE_DETAILS_KEY", "");

        if (key.isEmpty()) {
            Toast.makeText(this, "Please enter your Service Details API Key in Settings", Toast.LENGTH_LONG).show();
            return;
        }

        NationalRailAPI api = APIClient.getClient().create(NationalRailAPI.class);

        api.getServiceDetails(key, serviceId).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ServiceDetailsResponse> call, @NonNull Response<ServiceDetailsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ServiceDetailsResponse data = response.body();
                    System.out.println(response.body());

                    String operatorName = data.operator != null ? data.operator : "Unknown";
                    int brandColour = TOCBrandHelper.getColorForToc(operatorName);
                    if (topHeaderBar != null) {
                        topHeaderBar.setBackgroundColor(brandColour);
                    }

                    String destination = "Unknown Destination";
                    if (data.subsequentCallingPoints != null && !data.subsequentCallingPoints.isEmpty()) {
                        List<ServiceDetailsResponse.TimelinePoint> futurePoints = data.subsequentCallingPoints.get(0).callingPoint;
                        if (futurePoints != null && !futurePoints.isEmpty()) {
                            destination = "To " + futurePoints.get(futurePoints.size() - 1).locationName;
                        }
                    } else if (data.locationName != null) {
                        destination = "Terminates at " + data.locationName;
                    }

                    tvHeaderDestination.setText(destination);
                    tvHeaderTime.setText(data.std);
                    tvHeaderStatus.setText(data.etd);
                    tvHeaderOperator.setText(operatorName);
                    tvHeaderPlatform.setText(data.platform != null ? "Platform " + data.platform : "");

                    if ("On time".equalsIgnoreCase(data.etd)) {
                        tvHeaderStatus.setTextColor(Color.parseColor("#00875A"));
                    } else if ("Cancelled".equalsIgnoreCase(data.etd)) {
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

                    List<ServiceDetailsResponse.TimelinePoint> allPoints = new ArrayList<>();

                    if (data.previousCallingPoints != null && !data.previousCallingPoints.isEmpty()) {
                        allPoints.addAll(data.previousCallingPoints.get(0).callingPoint);
                    }

                    ServiceDetailsResponse.TimelinePoint currentStation = new ServiceDetailsResponse.TimelinePoint();
                    currentStation.locationName = data.locationName != null ? data.locationName : "Current Station";
                    currentStation.scheduledTime = data.std != null ? data.std : "";
                    currentStation.estimatedTime = data.etd;
                    currentStation.isCancelled = "Cancelled".equalsIgnoreCase(data.etd);

                    if (data.atd != null) {
                        currentStation.actualTime = data.atd;
                    } else if (data.ata != null) {
                        currentStation.actualTime = data.ata;
                    }

                    allPoints.add(currentStation);

                    if (data.subsequentCallingPoints != null && !data.subsequentCallingPoints.isEmpty()) {
                        allPoints.addAll(data.subsequentCallingPoints.get(0).callingPoint);
                    }

                    TimelineAdapter adapter = new TimelineAdapter(allPoints, brandColour);
                    recyclerTimeline.setAdapter(adapter);

                } else {
                    Toast.makeText(ServiceDetailsActivity.this, "Failed to load details. Check API Key.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ServiceDetailsResponse> call, @NonNull Throwable t) {
                Toast.makeText(ServiceDetailsActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }
}