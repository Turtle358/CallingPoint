package io.github.turtle358.callingpoint.ui.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyLeg;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;

public class JourneyLegAdapter extends RecyclerView.Adapter<JourneyLegAdapter.LegViewHolder> {

    private final List<JourneyLeg> legs;

    public JourneyLegAdapter(List<JourneyLeg> legs) {
        this.legs = legs;
    }

    @NonNull
    @Override
    public LegViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_journey_leg, parent, false);
        return new LegViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LegViewHolder holder, int position) {
        JourneyLeg currentLeg = legs.get(position);

        if ("TUBE/WALK".equals(currentLeg.transportMode)) {
            holder.headerOperator.setVisibility(View.GONE);
            holder.layoutTimeline.setVisibility(View.GONE);
            holder.cardTransfer.setVisibility(View.VISIBLE);
            holder.tvTransferTitle.setText("Underground / Walk to " + currentLeg.toStationName);
            holder.tvTransferDuration.setText(currentLeg.durationMinutes + " min transfer");
            return;
        }

        holder.headerOperator.setVisibility(View.VISIBLE);
        holder.layoutTimeline.setVisibility(View.VISIBLE);
        holder.cardTransfer.setVisibility(View.GONE);

        holder.tvOperatorName.setText(currentLeg.operatorName);
        holder.tvTrainDestination.setText("to " + currentLeg.trainDestination);
        holder.ivManagerLogo.setImageResource(TOCBrandHelper.getIconForToc(currentLeg.operatorName));

        if (currentLeg.liveLocation != null && !currentLeg.liveLocation.isEmpty()) {
            holder.tvLiveLocation.setText(currentLeg.liveLocation);
            holder.tvLiveLocation.setVisibility(View.VISIBLE);
        } else {
            holder.tvLiveLocation.setVisibility(View.GONE);
        }

        holder.layoutTimeline.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());

        addTimelineNode(inflater, holder.layoutTimeline, currentLeg.fromStationName, currentLeg.departureTime, null, true, false);

        if (currentLeg.intermediateStops != null && !currentLeg.intermediateStops.isEmpty()) {
            TextView btnToggle = new TextView(holder.itemView.getContext());
            btnToggle.setText(currentLeg.isExpanded ? "Hide stops" : "View stops");
            btnToggle.setTextColor(Color.parseColor("#1565C0"));
            btnToggle.setPadding(90, 8, 0, 8);
            btnToggle.setOnClickListener(v -> {
                currentLeg.isExpanded = !currentLeg.isExpanded;
                notifyItemChanged(position);
            });
            holder.layoutTimeline.addView(btnToggle);

            if (currentLeg.isExpanded) {
                for (JourneyLeg.Stop stop : currentLeg.intermediateStops) {
                    addTimelineNode(inflater, holder.layoutTimeline, stop.stationName, stop.time, null, false, false);
                }
            }
        }

        addTimelineNode(inflater, holder.layoutTimeline, currentLeg.toStationName, currentLeg.arrivalTime, null, false, true);

        if (position < legs.size() - 1) {
            JourneyLeg nextLeg = legs.get(position + 1);
            if (!"TUBE/WALK".equals(nextLeg.transportMode)) {
                holder.cardTransfer.setVisibility(View.VISIBLE);
                holder.tvTransferTitle.setText("Change at " + currentLeg.toStationName);
                holder.tvTransferDuration.setText(calculateTransferTime(currentLeg.arrivalTime, nextLeg.departureTime) + " min to change");
            }
        }
    }

    private void addTimelineNode(LayoutInflater inflater, LinearLayout container, String name, String schTime, String liveTime, boolean isFirst, boolean isLast) {
        View node = inflater.inflate(R.layout.item_timeline_node, container, false);
        ((TextView) node.findViewById(R.id.tvStationName)).setText(name);
        ((TextView) node.findViewById(R.id.tvSchTime)).setText(schTime);

        if (isFirst) node.findViewById(R.id.lineTop).setVisibility(View.INVISIBLE);
        if (isLast) node.findViewById(R.id.lineBottom).setVisibility(View.INVISIBLE);

        container.addView(node);
    }

    @Override
    public int getItemCount() { return legs != null ? legs.size() : 0; }

    private int calculateTransferTime(String arrival, String nextDeparture) {
        try {
            String[] arr = arrival.split(":");
            String[] dep = nextDeparture.split(":");
            int diff = (Integer.parseInt(dep[0]) * 60 + Integer.parseInt(dep[1])) - (Integer.parseInt(arr[0]) * 60 + Integer.parseInt(arr[1]));
            return diff < 0 ? diff + 1440 : diff;
        } catch (Exception e) { return 0; }
    }

    static class LegViewHolder extends RecyclerView.ViewHolder {
        LinearLayout headerOperator, layoutTimeline;
        TextView tvOperatorName, tvTrainDestination, tvLiveLocation;
        CardView cardTransfer;
        TextView tvTransferTitle, tvTransferDuration;
        ImageView ivManagerLogo;

        public LegViewHolder(@NonNull View itemView) {
            super(itemView);
            headerOperator = itemView.findViewById(R.id.headerOperator);
            tvOperatorName = itemView.findViewById(R.id.tvOperatorName);
            tvTrainDestination = itemView.findViewById(R.id.tvTrainDestination);
            tvLiveLocation = itemView.findViewById(R.id.tvLiveLocation);
            ivManagerLogo = itemView.findViewById(R.id.ivManagerLogo);
            layoutTimeline = itemView.findViewById(R.id.layoutTimeline);
            cardTransfer = itemView.findViewById(R.id.cardTransfer);
            tvTransferTitle = itemView.findViewById(R.id.tvTransferTitle);
            tvTransferDuration = itemView.findViewById(R.id.tvTransferDuration);
        }
    }
}