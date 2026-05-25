package io.github.turtle358.callingpoint.ui.activities;

import android.content.Context;
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

        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_journey_details);

        JourneyOption journey = (JourneyOption) getIntent().getSerializableExtra("JOURNEY_OPTION");

        if (journey == null || journey.legs == null || journey.legs.isEmpty()) {
            finish();
            return;
        }

        View topHeaderBar = findViewById(R.id.topHeaderBar);
        TextView tvTitle = findViewById(R.id.tvDetailTitle);
        TextView tvSummary = findViewById(R.id.tvDetailSummary);
        RecyclerView recyclerJourneyLegs = findViewById(R.id.recyclerJourneyLegs);

        String firstOperator = journey.legs.get(0).operatorName;
        int brandColour = TOCBrandHelper.getColourForToc(firstOperator);
        topHeaderBar.setBackgroundColor(brandColour);

        String origin = journey.legs.get(0).fromStationName;
        String dest = journey.legs.get(journey.legs.size() - 1).toStationName;
        tvTitle.setText(origin + " to " + dest);

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
        String apiKey = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE).getString("SERVICE_DETAILS_KEY", "");
        if (apiKey.isEmpty()) return;

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
                api.getServiceDetails(apiKey, leg.serviceId).enqueue(new Callback<>() {
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