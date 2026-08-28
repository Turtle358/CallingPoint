package io.github.turtle358.callingpoint.ui.adapters;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;

public class JourneyAdapter extends RecyclerView.Adapter<JourneyAdapter.ViewHolder> {

    private List<JourneyOption> routes;
    private final OnJourneyClickListener listener;
    boolean isLightColour;

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

        String origin = "";
        String dest = "";

        if (route.legs != null && !route.legs.isEmpty()) {
            origin = route.legs.get(0).fromStationName;
            dest = route.legs.get(route.legs.size() - 1).toStationName;

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

            int brandColour = TOCBrandHelper.getColourForToc(primaryBrand);

            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setColor(brandColour);
            isLightColour = ColorUtils.calculateLuminance(brandColour) > 0.5;
            pillBg.setCornerRadius(holder.itemView.getContext().getResources().getDisplayMetrics().density * 8);
            holder.tvOperatorName.setBackground(pillBg);
            holder.tvOperatorName.setTextColor(isLightColour ? Color.BLACK : Color.WHITE);

            if (primaryBrand != null && !primaryBrand.isEmpty()) {
                if (primaryBrand.equalsIgnoreCase("bus")) {
                    holder.tvOperatorName.setText("Bus");
                } else {
                    String formattedName = primaryBrand.substring(0, 1).toUpperCase() + primaryBrand.substring(1).toLowerCase();
                    holder.tvOperatorName.setText(formattedName);
                }
            }

            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(brandColour);
            holder.dotTop.setBackground(dotBg);
            holder.dotBottom.setBackground(dotBg);

            int translucentBrandColor = ColorUtils.setAlphaComponent(brandColour, 102);
            holder.timelineLine.setBackgroundColor(translucentBrandColor);

            if (primaryBrand.equalsIgnoreCase("bus")) {
                holder.ivModeIcon.setImageResource(R.drawable.ic_bus);
            } else if (primaryBrand.equalsIgnoreCase("walk")) {
                holder.ivModeIcon.setImageResource(R.drawable.ic_walk);
            } else if (TOCBrandHelper.getManagerForToc(primaryBrand).equalsIgnoreCase("gbr") || TOCBrandHelper.getManagerForToc(primaryBrand).equalsIgnoreCase("national")) {
                holder.ivModeIcon.setImageResource(R.drawable.ic_national_rail);
            } else {
                holder.ivModeIcon.setImageResource(TOCBrandHelper.getIconForToc(primaryBrand));
            }
        }

        holder.tvJourneyDate.setText(getFormattedDate(route.departureDate));

        holder.tvOriginStation.setText(origin);
        holder.tvDestStation.setText(dest);
        holder.tvDepTime.setText(route.departTime != null ? route.departTime : "--:--");
        holder.tvArrTime.setText(route.arriveTime != null ? route.arriveTime : "--:--");

        if (route.liveDepartTime != null && !route.liveDepartTime.equalsIgnoreCase("On time") && !route.liveDepartTime.equals(route.departTime)) {
            holder.tvLiveStatus.setVisibility(View.VISIBLE);

            if (route.liveDepartTime.equalsIgnoreCase("Cancelled")) {
                holder.tvLiveStatus.setText("Cancelled");
                holder.tvLiveStatus.setTextColor(Color.parseColor("#DE350B"));
            } else {
                holder.tvLiveStatus.setText("Exp " + route.liveDepartTime);
                holder.tvLiveStatus.setTextColor(Color.parseColor("#FF8C00"));
            }

            holder.tvDepTime.setPaintFlags(holder.tvDepTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvDepTime.setTextColor(Color.parseColor("#888888"));
        } else {
            holder.tvLiveStatus.setVisibility(View.GONE);
            holder.tvDepTime.setPaintFlags(holder.tvDepTime.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.tvDepTime.setTextColor(holder.defaultTimeColor);
        }

        holder.tvJourneyDuration.setText(route.duration != null ? route.duration : "");
        holder.tvJourneyChanges.setText(route.changesSummary != null ? route.changesSummary : "Direct");

        holder.itemView.setOnClickListener(v -> listener.onJourneyClick(route));
    }

    private String getFormattedDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "Today";

        try {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.UK);
            java.util.Date parsedDate = format.parse(isoDate);

            Calendar cal = Calendar.getInstance();
            cal.setTime(parsedDate);

            Calendar today = Calendar.getInstance();
            Calendar tomorrow = Calendar.getInstance();
            tomorrow.add(Calendar.DAY_OF_YEAR, 1);

            if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
                return "Today";
            } else if (cal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR)) {
                return "Tomorrow";
            } else {
                java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("d MMM", Locale.UK);
                return outputFormat.format(parsedDate);
            }
        } catch (Exception e) {
            return "Today";
        }
    }

    @Override
    public int getItemCount() {
        return routes != null ? routes.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View dotTop, dotBottom, timelineLine;
        ImageView ivModeIcon;
        TextView tvOperatorName, tvJourneyDate, tvJourneyDuration, tvJourneyChanges;
        TextView tvDepTime, tvArrTime, tvOriginStation, tvDestStation, tvLiveStatus;
        int defaultTimeColor;

        public ViewHolder(View itemView) {
            super(itemView);
            dotTop = itemView.findViewById(R.id.dotTop);
            dotBottom = itemView.findViewById(R.id.dotBottom);
            timelineLine = itemView.findViewById(R.id.timelineLine);

            ivModeIcon = itemView.findViewById(R.id.ivModeIcon);
            tvOperatorName = itemView.findViewById(R.id.tvOperatorName);
            tvJourneyDate = itemView.findViewById(R.id.tvJourneyDate);
            tvJourneyDuration = itemView.findViewById(R.id.tvJourneyDuration);
            tvJourneyChanges = itemView.findViewById(R.id.tvJourneyChanges);

            tvDepTime = itemView.findViewById(R.id.tvDepTime);
            tvArrTime = itemView.findViewById(R.id.tvArrTime);
            tvOriginStation = itemView.findViewById(R.id.tvOriginStation);
            tvDestStation = itemView.findViewById(R.id.tvDestStation);
            tvLiveStatus = itemView.findViewById(R.id.tvLiveStatus);

            defaultTimeColor = tvDepTime.getCurrentTextColor();
        }
    }
}