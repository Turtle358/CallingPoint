package io.github.turtle358.callingpoint.ui.adapters;

import android.content.res.ColorStateList;
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
            holder.dotDep.setVisibility(View.GONE);
            holder.lineTravel.setVisibility(View.GONE);
            holder.dotArr.setVisibility(View.GONE);
            holder.tvDepTime.setVisibility(View.GONE);
            holder.tvArrTime.setVisibility(View.GONE);
            holder.tvDepStation.setVisibility(View.GONE);
            holder.tvArrStation.setVisibility(View.GONE);
            holder.btnViewStops.setVisibility(View.GONE);
            holder.layoutIntermediateStops.setVisibility(View.GONE);

            holder.cardTransfer.setVisibility(View.VISIBLE);
            holder.tvTransferTitle.setText("Underground / Walk to " + currentLeg.toStationName);
            holder.tvTransferDuration.setText(currentLeg.durationMinutes + " min transfer");
            return;
        }

        // Standard Train Leg
        holder.headerOperator.setVisibility(View.VISIBLE);
        holder.dotDep.setVisibility(View.VISIBLE);
        holder.lineTravel.setVisibility(View.VISIBLE);
        holder.dotArr.setVisibility(View.VISIBLE);
        holder.tvDepTime.setVisibility(View.VISIBLE);
        holder.tvArrTime.setVisibility(View.VISIBLE);
        holder.tvDepStation.setVisibility(View.VISIBLE);
        holder.tvArrStation.setVisibility(View.VISIBLE);

        holder.tvOperatorName.setText(currentLeg.operatorName);
        holder.tvTrainDestination.setText("to " + currentLeg.trainDestination);

        holder.tvDepTime.setText(currentLeg.departureTime);
        holder.tvDepStation.setText(currentLeg.fromStationName);
        holder.tvArrTime.setText(currentLeg.arrivalTime);
        holder.tvArrStation.setText(currentLeg.toStationName);

        // Apply TOC Brands (Colors & Logos!)
        int brandColor = TOCBrandHelper.getColorForToc(currentLeg.operatorName);
        holder.dotDep.setBackgroundTintList(ColorStateList.valueOf(brandColor));
        holder.dotArr.setBackgroundTintList(ColorStateList.valueOf(brandColor));
        holder.lineTravel.setBackgroundColor(brandColor);
        holder.ivManagerLogo.setImageResource(TOCBrandHelper.getIconForToc(currentLeg.operatorName));

        // --- EXPANDABLE STOPS LOGIC ---
        boolean hasStops = currentLeg.intermediateStops != null && !currentLeg.intermediateStops.isEmpty();
        holder.btnViewStops.setVisibility(hasStops ? View.VISIBLE : View.GONE);
        holder.btnViewStops.setText(currentLeg.isExpanded ? "Hide stops" : "View stops");
        holder.layoutIntermediateStops.setVisibility(currentLeg.isExpanded ? View.VISIBLE : View.GONE);

        holder.layoutIntermediateStops.removeAllViews();
        if (currentLeg.isExpanded && hasStops) {
            LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
            for (JourneyLeg.Stop stop : currentLeg.intermediateStops) {
                View stopView = inflater.inflate(R.layout.item_timeline_stop, holder.layoutIntermediateStops, false);
                TextView tvTime = stopView.findViewById(R.id.tvStopTime);
                TextView tvStation = stopView.findViewById(R.id.tvStopStation);

                tvTime.setText(stop.time);
                tvStation.setText(stop.stationName);
                holder.layoutIntermediateStops.addView(stopView);
            }
        }

        holder.btnViewStops.setOnClickListener(v -> {
            currentLeg.isExpanded = !currentLeg.isExpanded;
            notifyItemChanged(position);
        });
        // ------------------------------

        // Transfer Block Logic
        if (position < legs.size() - 1) {
            JourneyLeg nextLeg = legs.get(position + 1);
            if ("TUBE/WALK".equals(nextLeg.transportMode)) {
                holder.cardTransfer.setVisibility(View.GONE);
            } else {
                int waitMins = calculateTransferTime(currentLeg.arrivalTime, nextLeg.departureTime);
                holder.cardTransfer.setVisibility(View.VISIBLE);
                holder.tvTransferTitle.setText("Change at " + currentLeg.toStationName);
                holder.tvTransferDuration.setText(waitMins + " min to change");
            }
        } else {
            holder.cardTransfer.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return legs != null ? legs.size() : 0;
    }

    private int calculateTransferTime(String arrival, String nextDeparture) {
        if (arrival == null || nextDeparture == null) return 0;
        try {
            String[] arrParts = arrival.split(":");
            String[] depParts = nextDeparture.split(":");
            int arrMins = Integer.parseInt(arrParts[0]) * 60 + Integer.parseInt(arrParts[1]);
            int depMins = Integer.parseInt(depParts[0]) * 60 + Integer.parseInt(depParts[1]);
            int diff = depMins - arrMins;
            if (diff < 0) diff += 24 * 60;
            return diff;
        } catch (Exception e) { return 0; }
    }

    static class LegViewHolder extends RecyclerView.ViewHolder {
        LinearLayout headerOperator, layoutIntermediateStops;
        TextView tvOperatorName, tvTrainDestination, btnViewStops;
        TextView tvDepTime, tvDepStation, tvArrTime, tvArrStation;
        View dotDep, dotArr, lineTravel;
        ImageView ivManagerLogo; // NEW!

        CardView cardTransfer;
        TextView tvTransferTitle, tvTransferDuration;

        public LegViewHolder(@NonNull View itemView) {
            super(itemView);
            headerOperator = itemView.findViewById(R.id.headerOperator);
            tvOperatorName = itemView.findViewById(R.id.tvOperatorName);
            tvTrainDestination = itemView.findViewById(R.id.tvTrainDestination);
            tvDepTime = itemView.findViewById(R.id.tvDepTime);
            tvDepStation = itemView.findViewById(R.id.tvDepStation);
            tvArrTime = itemView.findViewById(R.id.tvArrTime);
            tvArrStation = itemView.findViewById(R.id.tvArrStation);
            dotDep = itemView.findViewById(R.id.dotDep);
            dotArr = itemView.findViewById(R.id.dotArr);
            lineTravel = itemView.findViewById(R.id.lineTravel);
            ivManagerLogo = itemView.findViewById(R.id.ivManagerLogo); // NEW!

            btnViewStops = itemView.findViewById(R.id.btnViewStops);
            layoutIntermediateStops = itemView.findViewById(R.id.layoutIntermediateStops);

            cardTransfer = itemView.findViewById(R.id.cardTransfer);
            tvTransferTitle = itemView.findViewById(R.id.tvTransferTitle);
            tvTransferDuration = itemView.findViewById(R.id.tvTransferDuration);
        }
    }
}