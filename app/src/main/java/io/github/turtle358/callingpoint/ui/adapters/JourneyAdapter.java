package io.github.turtle358.callingpoint.ui.adapters;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;

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

        if (route.legs != null && !route.legs.isEmpty()) {
            String primaryBrand = "walk";

            for (int i = 0; i < route.legs.size(); i++) {
                String op = route.legs.get(i).operatorName;

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

            holder.tvOperatorName.setText(primaryBrand);

            if(TOCBrandHelper.getManagerName(primaryBrand).equalsIgnoreCase("Great British Railways")) {
                holder.ivModeIcon.setImageResource(R.drawable.ic_national_rail);
            } else {
                holder.ivModeIcon.setImageResource(TOCBrandHelper.getIconForToc(primaryBrand));
            }


            holder.ivModeIcon.clearColorFilter();
        }

        holder.tvJourneyTimes.setText((route.departTime != null ? route.departTime : "--:--") + " → " + (route.arriveTime != null ? route.arriveTime : "--:--"));

        if (route.liveDepartTime != null && !route.liveDepartTime.equalsIgnoreCase("On time") && !route.liveDepartTime.equals(route.departTime)) {
            holder.tvLiveDepart.setVisibility(View.VISIBLE);
            holder.tvLiveDepart.setText(route.liveDepartTime.equalsIgnoreCase("Cancelled") ? "Cancelled" : "Exp " + route.liveDepartTime);

            holder.tvJourneyTimes.setPaintFlags(holder.tvJourneyTimes.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvJourneyTimes.setTextColor(android.graphics.Color.parseColor("#888888"));
        } else {
            holder.tvLiveDepart.setVisibility(View.GONE);
            holder.tvJourneyTimes.setPaintFlags(holder.tvJourneyTimes.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.tvJourneyTimes.setTextColor(holder.defaultTimeColor);
        }

        holder.tvJourneyDuration.setText(route.duration != null ? route.duration : "");
        holder.tvJourneyChanges.setText(route.changesSummary != null ? route.changesSummary : "Direct");

        holder.itemView.setOnClickListener(v -> listener.onJourneyClick(route));
    }

    @Override
    public int getItemCount() {
        return routes != null ? routes.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View tocColourBar;
        android.widget.ImageView ivModeIcon;
        TextView tvOperatorName, tvJourneyTimes, tvLiveDepart, tvJourneyDuration, tvJourneyChanges;
        int defaultTimeColor;

        public ViewHolder(View itemView) {
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