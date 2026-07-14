package io.github.turtle358.callingpoint.ui.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.models.Toc;
import io.github.turtle358.callingpoint.ui.activities.JourneySearchActivity;
import io.github.turtle358.callingpoint.ui.adapters.PinnedJourneyAdapter;
import io.github.turtle358.callingpoint.utils.PinnedJourneyManager;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;

public class JourneyPlannerFragment extends Fragment {

    private RecyclerView recyclerPinnedJourneys;
    private View layoutEmptyState;
    private TextView tvListHeader;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_journey_planner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerPinnedJourneys = view.findViewById(R.id.recyclerPinnedJourneys);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        tvListHeader = view.findViewById(R.id.tvListHeader);
        CardView cardMockSearch = view.findViewById(R.id.cardMockSearch);

        recyclerPinnedJourneys.setLayoutManager(new LinearLayoutManager(requireContext()));

        cardMockSearch.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), JourneySearchActivity.class));
        });

        setupDelayRepayHub(view);
    }

    private void setupDelayRepayHub(View view) {
        LinearLayout header = view.findViewById(R.id.delayRepayHeader);
        ImageView iconExpand = view.findViewById(R.id.iconExpandDelay);
        RecyclerView recyclerDelayRepay = view.findViewById(R.id.recyclerDelayRepayTocs);
        CardView cardDelayRepayHub = view.findViewById(R.id.cardDelayRepayHub);

        List<Toc> visibleTocs = TOCBrandHelper.getVisibleTocs();
        List<Toc> validTocs = new ArrayList<>();

        for (Toc toc : visibleTocs) {
            if (toc.delayRepayUrl != null && !toc.delayRepayUrl.isEmpty()) {
                validTocs.add(toc);
            }
        }

        recyclerDelayRepay.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerDelayRepay.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_disruption, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Toc toc = validTocs.get(position);
                TextView tvName = holder.itemView.findViewById(R.id.tvTocName);
                TextView tvCode = holder.itemView.findViewById(R.id.tvTocCode);
                TextView tvStatus = holder.itemView.findViewById(R.id.tvStatus);

                tvName.setText(toc.name);
                tvCode.setText(toc.acronym != null ? toc.acronym : toc.code);

                if ("tfl".equalsIgnoreCase(toc.manager)) {
                    tvStatus.setText("Handled via TfL refunds. Different rules apply.");
                    tvStatus.setVisibility(View.VISIBLE);
                } else if ("GC".equalsIgnoreCase(toc.code) || "LF".equalsIgnoreCase(toc.code)) {
                    tvStatus.setText("Minimum delay of 60 minutes applies.");
                    tvStatus.setVisibility(View.VISIBLE);
                } else if ("ES".equalsIgnoreCase(toc.code)){
                    tvStatus.setText("Minimum delay of 60 minutes applies.");
                    tvStatus.setVisibility(View.VISIBLE);
                } else if ("HX".equalsIgnoreCase(toc.code)){
                    tvStatus.setText("Minimum delay of 30 minutes applies.");
                    tvStatus.setVisibility(View.VISIBLE);
                } else {
                    tvStatus.setVisibility(View.GONE);
                }

                if (holder.itemView instanceof ViewGroup && ((ViewGroup) holder.itemView).getChildCount() > 2) {
                    ((ViewGroup) holder.itemView).getChildAt(2).setVisibility(View.GONE);
                }

                int brandColour = TOCBrandHelper.getColourForToc(toc.name);
                tvCode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(brandColour));

                boolean isLightColour = androidx.core.graphics.ColorUtils.calculateLuminance(brandColour) > 0.5;
                tvCode.setTextColor(isLightColour ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);

                holder.itemView.setOnClickListener(v -> {
                    CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
                    customTabsIntent.launchUrl(requireContext(), Uri.parse(toc.delayRepayUrl));
                });
            }

            @Override
            public int getItemCount() {
                return validTocs.size();
            }
        });

        header.setOnClickListener(v -> {
            boolean isExpanded = recyclerDelayRepay.getVisibility() == View.VISIBLE;
            android.transition.TransitionManager.beginDelayedTransition((ViewGroup) cardDelayRepayHub.getParent());
            recyclerDelayRepay.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
            iconExpand.animate().rotation(isExpanded ? 0f : 180f).setDuration(200).start();
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
            layoutEmptyState.setVisibility(View.GONE);
            recyclerPinnedJourneys.setVisibility(View.VISIBLE);

            PinnedJourneyAdapter adapter = new PinnedJourneyAdapter(requireContext(), savedJourneys);
            recyclerPinnedJourneys.setAdapter(adapter);
        } else {
            tvListHeader.setVisibility(View.GONE);
            recyclerPinnedJourneys.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        }
    }
}