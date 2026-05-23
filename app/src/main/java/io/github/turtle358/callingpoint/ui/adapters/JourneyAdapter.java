package io.github.turtle358.callingpoint.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyOption;

public class JourneyAdapter extends RecyclerView.Adapter<JourneyAdapter.ViewHolder> {

    private List<JourneyOption> routes;
    private final OnJourneyClickListener listener;

    public interface OnJourneyClickListener {
        void onJourneyClick(JourneyOption route);
    }

    public JourneyAdapter(List<JourneyOption> routes, OnJourneyClickListener listener) {
        this.routes = routes;
        this.listener = listener;
    }

    public void updateData(List<JourneyOption> newRoutes) {
        this.routes = newRoutes;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_journey, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JourneyOption route = routes.get(position);

        holder.tvJourneyTimes.setText((route.departTime != null ? route.departTime : "--:--") + " → " + (route.arriveTime != null ? route.arriveTime : "--:--"));
        holder.tvJourneyDuration.setText(route.duration != null ? route.duration : "");
        holder.tvJourneyChanges.setText(route.changesSummary != null ? route.changesSummary : "Direct");

        holder.itemView.setOnClickListener(v -> listener.onJourneyClick(route));
    }

    @Override
    public int getItemCount() {
        return routes != null ? routes.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvJourneyTimes, tvJourneyDuration, tvJourneyChanges;

        public ViewHolder(View itemView) {
            super(itemView);
            tvJourneyTimes = itemView.findViewById(R.id.tvJourneyTimes);
            tvJourneyDuration = itemView.findViewById(R.id.tvJourneyDuration);
            tvJourneyChanges = itemView.findViewById(R.id.tvJourneyChanges);
        }
    }
}