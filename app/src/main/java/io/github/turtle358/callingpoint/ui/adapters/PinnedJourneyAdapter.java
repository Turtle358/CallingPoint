package io.github.turtle358.callingpoint.ui.adapters;

import android.content.Context;
import android.content.Intent;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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

        String origin = "";
        String dest = "";

        if (journey.legs != null && !journey.legs.isEmpty()) {
            origin = journey.legs.get(0).fromStationName;
            dest = journey.legs.get(journey.legs.size() - 1).toStationName;

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

            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setColor(brandColor);
            pillBg.setCornerRadius(holder.itemView.getContext().getResources().getDisplayMetrics().density * 8);
            holder.tvOperatorName.setBackground(pillBg);
            holder.tvOperatorName.setTextColor(Color.WHITE);

            if (primaryBrand.equalsIgnoreCase("bus")) {
                holder.tvOperatorName.setText("London Bus");
            } else if (primaryBrand != null && !primaryBrand.isEmpty()) {
                String formattedName = primaryBrand.substring(0, 1).toUpperCase() + primaryBrand.substring(1).toLowerCase();
                holder.tvOperatorName.setText(formattedName);
            }

            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(brandColor);
            holder.dotTop.setBackground(dotBg);
            holder.dotBottom.setBackground(dotBg);

            int translucentBrandColor = ColorUtils.setAlphaComponent(brandColor, 102);
            holder.timelineLine.setBackgroundColor(translucentBrandColor);

            if (primaryBrand.equalsIgnoreCase("bus")) {
                holder.ivModeIcon.setImageResource(R.drawable.ic_bus);
                holder.ivModeIcon.setColorFilter(brandColor);
            } else if (primaryBrand.equalsIgnoreCase("walk")) {
                holder.ivModeIcon.setImageResource(R.drawable.ic_walk);
                holder.ivModeIcon.setColorFilter(brandColor);
            } else if (TOCBrandHelper.getManagerForToc(primaryBrand).equalsIgnoreCase("gbr") || TOCBrandHelper.getManagerForToc(primaryBrand).equalsIgnoreCase("national")) {
                holder.ivModeIcon.setImageResource(R.drawable.ic_national_rail);
            } else {
                holder.ivModeIcon.setImageResource(TOCBrandHelper.getIconForToc(primaryBrand));
                holder.ivModeIcon.setColorFilter(brandColor);
            }

        }

        holder.tvJourneyDate.setText(getFormattedDate(journey.departureDate));

        holder.tvOriginStation.setText(origin);
        holder.tvDestStation.setText(dest);
        holder.tvDepTime.setText(journey.departTime != null ? journey.departTime : "--:--");
        holder.tvArrTime.setText(journey.arriveTime != null ? journey.arriveTime : "--:--");

        if (journey.liveDepartTime != null && !journey.liveDepartTime.equalsIgnoreCase("On time") && !journey.liveDepartTime.equals(journey.departTime)) {
            holder.tvLiveStatus.setVisibility(View.VISIBLE);

            if (journey.liveDepartTime.equalsIgnoreCase("Cancelled")) {
                holder.tvLiveStatus.setText("Cancelled");
                holder.tvLiveStatus.setTextColor(Color.parseColor("#DE350B"));
            } else {
                holder.tvLiveStatus.setText("Exp " + journey.liveDepartTime);
                holder.tvLiveStatus.setTextColor(Color.parseColor("#FF8C00"));
            }

            holder.tvDepTime.setPaintFlags(holder.tvDepTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvDepTime.setTextColor(Color.parseColor("#888888"));
        } else {
            holder.tvLiveStatus.setVisibility(View.GONE);
            holder.tvDepTime.setPaintFlags(holder.tvDepTime.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.tvDepTime.setTextColor(holder.defaultTimeColor);
        }

        holder.tvJourneyDuration.setText(journey.duration != null ? journey.duration : "");

        if (journey.changesSummary != null && !journey.changesSummary.isEmpty()) {
            holder.tvJourneyChanges.setText(journey.changesSummary);
        } else {
            int changes = journey.legs != null ? journey.legs.size() - 1 : 0;
            holder.tvJourneyChanges.setText(changes == 0 ? "Direct" : changes + " change(s)");
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, JourneyDetailsActivity.class);
            intent.putExtra("JOURNEY_OPTION", journey);
            context.startActivity(intent);
        });
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
        View dotTop, dotBottom, timelineLine;
        ImageView ivModeIcon;
        TextView tvOperatorName, tvJourneyDate, tvJourneyDuration, tvJourneyChanges;
        TextView tvDepTime, tvArrTime, tvOriginStation, tvDestStation, tvLiveStatus;
        int defaultTimeColor;

        public PinnedViewHolder(@NonNull View itemView) {
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