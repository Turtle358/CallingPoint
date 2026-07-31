package io.github.turtle358.callingpoint.ui.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.ui.activities.JourneyDetailsActivity;
import io.github.turtle358.callingpoint.utils.PinnedJourneyManager;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;

public class PinnedJourneyAdapter extends RecyclerView.Adapter<PinnedJourneyAdapter.PinnedViewHolder> {

    private final Context context;
    private final List<JourneyOption> pinnedJourneys;
    private final PinnedJourneyManager manager;

    public PinnedJourneyAdapter(Context context, List<JourneyOption> pinnedJourneys) {
        this.context = context;
        this.pinnedJourneys = pinnedJourneys;
        this.manager = new PinnedJourneyManager(context);
    }

    @NonNull
    @Override
    public PinnedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_journey, parent, false);
        return new PinnedViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PinnedViewHolder holder, int position) {
        JourneyOption journey = pinnedJourneys.get(position);

        if (journey.legs != null && !journey.legs.isEmpty()) {
            String primaryBrand = "walk";

            for (int i = 0; i < journey.legs.size(); i++) {
                String op = journey.legs.get(i).operatorName;

                if (op == null || op.trim().isEmpty() || op.toLowerCase().contains("walk")) {
                    continue;
                }

                if (op.matches("^[a-zA-Z]{0,2}\\d+[a-zA-Z]?$") && !op.equalsIgnoreCase("c2c")) {
                    primaryBrand = "bus";
                } else {
                    primaryBrand = op;
                }
                break;
            }

            int brandColor = TOCBrandHelper.getColourForToc(primaryBrand);
            holder.tocColourBar.setBackgroundColor(brandColor);

            if (primaryBrand.equalsIgnoreCase("bus")) {
                holder.tvOperatorName.setText("London Bus");
            } else {
                holder.tvOperatorName.setText(primaryBrand);
            }

            if(TOCBrandHelper.getManagerName(primaryBrand).equalsIgnoreCase("Great British Railways")) {
                holder.ivModeIcon.setImageResource(R.drawable.ic_national_rail);
            } else {
                holder.ivModeIcon.setImageResource(TOCBrandHelper.getIconForToc(primaryBrand));
            }
            holder.ivModeIcon.clearColorFilter();
        }

        holder.tvJourneyTimes.setText((journey.departTime != null ? journey.departTime : "--:--") + " → " + (journey.arriveTime != null ? journey.arriveTime : "--:--"));

        if (journey.liveDepartTime != null && !journey.liveDepartTime.equalsIgnoreCase("On time") && !journey.liveDepartTime.equals(journey.departTime)) {
            holder.tvLiveDepart.setVisibility(View.VISIBLE);
            holder.tvLiveDepart.setText(journey.liveDepartTime.equalsIgnoreCase("Cancelled") ? "Cancelled" : "Exp " + journey.liveDepartTime);

            holder.tvJourneyTimes.setPaintFlags(holder.tvJourneyTimes.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvJourneyTimes.setTextColor(android.graphics.Color.parseColor("#888888"));
        } else {
            holder.tvLiveDepart.setVisibility(View.GONE);
            holder.tvJourneyTimes.setPaintFlags(holder.tvJourneyTimes.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.tvJourneyTimes.setTextColor(holder.defaultTimeColor);
        }

        holder.tvJourneyDuration.setText(journey.duration != null ? journey.duration : "");

        if (journey.changesSummary != null && !journey.changesSummary.isEmpty()) {
            holder.tvJourneyChanges.setText(journey.changesSummary);
        } else {
            int changes = journey.legs.size() - 1;
            holder.tvJourneyChanges.setText(changes == 0 ? "Direct" : changes + " change(s)");
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, JourneyDetailsActivity.class);
            intent.putExtra("JOURNEY_OPTION", journey);
            context.startActivity(intent);
        });

    }

    @Override
    public int getItemCount() {
        return pinnedJourneys != null ? pinnedJourneys.size() : 0;
    }
    public void swapItems(int fromPosition, int toPosition) {
        Collections.swap(pinnedJourneys, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    public List<JourneyOption> getItems() {
        return pinnedJourneys;
    }

    public void restoreItem(JourneyOption journey, int position) {
        pinnedJourneys.add(position, journey);
        notifyItemInserted(position);
    }

    static class PinnedViewHolder extends RecyclerView.ViewHolder {
        View tocColourBar;
        ImageView ivModeIcon;
        TextView tvOperatorName, tvJourneyTimes, tvLiveDepart, tvJourneyDuration, tvJourneyChanges;
        int defaultTimeColor;

        public PinnedViewHolder(@NonNull View itemView) {
            super(itemView);
            tocColourBar = itemView.findViewById(R.id.tocColourBar);
            ivModeIcon = itemView.findViewById(R.id.ivModeIcon);
            tvOperatorName = itemView.findViewById(R.id.tvOperatorName);
            tvJourneyTimes = itemView.findViewById(R.id.tvJourneyTimes);
            tvLiveDepart = itemView.findViewById(R.id.tvLiveDepart);
            tvJourneyDuration = itemView.findViewById(R.id.tvJourneyDuration);
            tvJourneyChanges = itemView.findViewById(R.id.tvJourneyChanges);

            defaultTimeColor = tvJourneyTimes.getCurrentTextColor();
        }
    }
}