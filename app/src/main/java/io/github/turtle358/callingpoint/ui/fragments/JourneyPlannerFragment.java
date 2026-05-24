package io.github.turtle358.callingpoint.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.ui.activities.JourneySearchActivity;
import io.github.turtle358.callingpoint.ui.adapters.PinnedJourneyAdapter;
import io.github.turtle358.callingpoint.utils.PinnedJourneyManager;

public class JourneyPlannerFragment extends Fragment {

    private RecyclerView recyclerPinnedJourneys;
    private TextView tvEmptyState, tvListHeader;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_journey_planner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerPinnedJourneys = view.findViewById(R.id.recyclerPinnedJourneys);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        tvListHeader = view.findViewById(R.id.tvListHeader);
        CardView cardMockSearch = view.findViewById(R.id.cardMockSearch);

        recyclerPinnedJourneys.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Launch the actual search activity when tapped
        cardMockSearch.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), JourneySearchActivity.class));
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPinnedJourneys();
    }

    private void loadPinnedJourneys() {
        PinnedJourneyManager manager = new PinnedJourneyManager(requireContext());
        List<JourneyOption> savedJourneys = manager.getPinnedJourneys();

        if (savedJourneys != null && !savedJourneys.isEmpty()) {
            tvListHeader.setVisibility(View.VISIBLE);
            tvEmptyState.setVisibility(View.GONE);
            recyclerPinnedJourneys.setVisibility(View.VISIBLE);

            PinnedJourneyAdapter adapter = new PinnedJourneyAdapter(requireContext(), savedJourneys);
            recyclerPinnedJourneys.setAdapter(adapter);
        } else {
            tvListHeader.setVisibility(View.GONE);
            recyclerPinnedJourneys.setVisibility(View.GONE);
            tvEmptyState.setVisibility(View.VISIBLE);
        }
    }
}