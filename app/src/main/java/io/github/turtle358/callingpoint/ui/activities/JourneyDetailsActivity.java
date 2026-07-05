package io.github.turtle358.callingpoint.ui.activities;

import android.content.Context;
import android.os.Bundle;
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

import java.util.HashMap;
import java.util.Map;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.NationalRailAPI;
import io.github.turtle358.callingpoint.models.JourneyLeg;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse;
import io.github.turtle358.callingpoint.ui.adapters.JourneyLegAdapter;
import io.github.turtle358.callingpoint.utils.PinnedJourneyManager;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class JourneyDetailsActivity extends AppCompatActivity {

    private JourneyLegAdapter adapter;
    private final Map<String, ServiceDetailsResponse> liveDataMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journey_details);

        View topHeaderBar = findViewById(R.id.topHeaderBar);
        if (topHeaderBar != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(topHeaderBar, (v, insets) -> {
                int statusBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
                v.getLayoutParams().height = statusBarHeight;
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
        TextView tvSummary = findViewById(R.id.tvDetailSummary);
        RecyclerView recyclerJourneyLegs = findViewById(R.id.recyclerJourneyLegs);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        String firstOperator = journey.legs.get(0).operatorName;
        int brandColour = TOCBrandHelper.getColourForToc(firstOperator);

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

        String summary = journey.departTime + " — " + journey.arriveTime + "  •  " + journey.duration;
        tvSummary.setText(summary);

        setupPinButton(journey);

        recyclerJourneyLegs.setLayoutManager(new LinearLayoutManager(this));
        adapter = new JourneyLegAdapter(journey.legs);
        recyclerJourneyLegs.setAdapter(adapter);

        fetchLiveServiceDetails(journey);
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

        NationalRailAPI api = APIClient.getClient().create(NationalRailAPI.class);

        int fetchCount = 0;
        for (JourneyLeg leg : journey.legs) {
            if ("TRAIN".equals(leg.transportMode) && leg.serviceId != null) {
                fetchCount++;
            }
        }

        if (fetchCount == 0) return;

        final int[] completedRequests = {0};
        final int totalRequests = fetchCount;

        for (JourneyLeg leg : journey.legs) {
            if ("TRAIN".equals(leg.transportMode) && leg.serviceId != null) {
                api.getServiceDetails(leg.serviceId).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<ServiceDetailsResponse> call, @NonNull Response<ServiceDetailsResponse> response) {
                        completedRequests[0]++;
                        if (response.isSuccessful() && response.body() != null) {
                            liveDataMap.put(leg.serviceId, response.body());
                        }
                        checkAndPushData(completedRequests[0], totalRequests);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ServiceDetailsResponse> call, @NonNull Throwable t) {
                        completedRequests[0]++;
                        checkAndPushData(completedRequests[0], totalRequests);
                    }
                });
            }
        }
    }

    private void checkAndPushData(int completed, int total) {
        if (completed == total && adapter != null) {
            adapter.updateLiveData(liveDataMap);
        }
    }
}