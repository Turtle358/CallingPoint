package io.github.turtle358.callingpoint.ui.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.ui.adapters.JourneyLegAdapter;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;

public class JourneyDetailsActivity extends AppCompatActivity {

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
        int brandColour = TOCBrandHelper.getColorForToc(firstOperator);
        topHeaderBar.setBackgroundColor(brandColour);

        String origin = journey.legs.get(0).fromStationName;
        String dest = journey.legs.get(journey.legs.size() - 1).toStationName;
        tvTitle.setText(origin + " to " + dest);

        String summary = journey.departTime + " — " + journey.arriveTime + "  •  " + journey.duration;
        tvSummary.setText(summary);

        recyclerJourneyLegs.setLayoutManager(new LinearLayoutManager(this));
        JourneyLegAdapter adapter = new JourneyLegAdapter(journey.legs);
        recyclerJourneyLegs.setAdapter(adapter);
    }
}