package io.github.turtle358.callingpoint.ui.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.ui.activities.JourneyDetailsActivity;
import io.github.turtle358.callingpoint.utils.PinnedJourneyManager;

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

        holder.itemView.setOnLongClickListener(v -> {
            manager.removeJourney(journey);
            pinnedJourneys.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, pinnedJourneys.size());
            Toast.makeText(context, "Saved journey removed", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return pinnedJourneys != null ? pinnedJourneys.size() : 0;
    }

    static class PinnedViewHolder extends RecyclerView.ViewHolder {
        TextView tvJourneyTimes, tvLiveDepart, tvJourneyDuration, tvJourneyChanges;
        int defaultTimeColor;

        public PinnedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvJourneyTimes = itemView.findViewById(R.id.tvJourneyTimes);
            tvLiveDepart = itemView.findViewById(R.id.tvLiveDepart);
            tvJourneyDuration = itemView.findViewById(R.id.tvJourneyDuration);
            tvJourneyChanges = itemView.findViewById(R.id.tvJourneyChanges);

            defaultTimeColor = tvJourneyTimes.getCurrentTextColor();
        }
    }
}