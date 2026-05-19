package com.example.delaywatcher;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class TimelineAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_STOP = 0;
    private static final int TYPE_HEADER = 1;

    private final List<ServiceDetailsResponse.TimelinePoint> allPoints;
    private final List<TimelineItem> displayList = new ArrayList<>();
    private final int brandColor;
    private boolean isExpanded = false;

    private static class TimelineItem {
        int type;
        ServiceDetailsResponse.TimelinePoint point;
        int hiddenCount;
        TimelineItem(int type, ServiceDetailsResponse.TimelinePoint point) {
            this.type = type;
            this.point = point;
        }
    }

    public TimelineAdapter(List<ServiceDetailsResponse.TimelinePoint> allPoints, int brandColor) {
        this.allPoints = allPoints;
        this.brandColor = brandColor;
        buildDisplayList();
    }

    private void buildDisplayList() {
        displayList.clear();
        List<ServiceDetailsResponse.TimelinePoint> past = new ArrayList<>();
        List<ServiceDetailsResponse.TimelinePoint> future = new ArrayList<>();

        for (ServiceDetailsResponse.TimelinePoint p : allPoints) {
            if (p.actualTime != null) past.add(p);
            else future.add(p);
        }

        if (past.isEmpty()) {
            for (ServiceDetailsResponse.TimelinePoint p : future) displayList.add(new TimelineItem(TYPE_STOP, p));
        } else {
            displayList.add(new TimelineItem(TYPE_STOP, past.get(0)));

            if (past.size() > 1) {
                if (isExpanded) {
                    for (int i = 1; i < past.size(); i++) {
                        displayList.add(new TimelineItem(TYPE_STOP, past.get(i)));
                    }
                    TimelineItem header = new TimelineItem(TYPE_HEADER, null);
                    header.hiddenCount = past.size() - 1;
                    displayList.add(header);
                } else {
                    // Show Expand button instead of middle stops
                    TimelineItem header = new TimelineItem(TYPE_HEADER, null);
                    header.hiddenCount = past.size() - 1;
                    displayList.add(header);
                }
            }
            for (ServiceDetailsResponse.TimelinePoint p : future) displayList.add(new TimelineItem(TYPE_STOP, p));
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return displayList.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_timeline_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_timeline, parent, false);
            return new StopViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        TimelineItem item = displayList.get(position);

        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder h = (HeaderViewHolder) holder;
            h.tvStopsCount.setText(item.hiddenCount + " stops");
            h.iconExpand.setImageResource(isExpanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);
            h.itemView.setOnClickListener(v -> {
                isExpanded = !isExpanded;
                buildDisplayList();
            });

        } else if (holder instanceof StopViewHolder) {
            StopViewHolder h = (StopViewHolder) holder;
            ServiceDetailsResponse.TimelinePoint point = item.point;

            h.tvStationName.setText(point.locationName);
            h.tvTime.setText(point.scheduledTime);

            h.lineTop.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
            h.lineBottom.setVisibility(position == displayList.size() - 1 ? View.INVISIBLE : View.VISIBLE);

            boolean isPast = point.actualTime != null;
            boolean isNextStop = !isPast && position > 0 && displayList.get(position - 1).type == TYPE_HEADER;

            if (isPast) {
                h.dot.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#999999")));
                h.lineTop.setBackgroundColor(Color.parseColor("#999999"));
                h.lineBottom.setBackgroundColor(Color.parseColor("#999999"));
            } else {
                h.dot.setBackgroundTintList(ColorStateList.valueOf(brandColor));
                h.lineTop.setBackgroundColor(isNextStop ? Color.parseColor("#999999") : brandColor);
                h.lineBottom.setBackgroundColor(brandColor);
            }

            if (point.isCancelled) {
                h.tvLiveStatus.setText("Cancelled");
                h.tvLiveStatus.setTextColor(Color.parseColor("#DE350B"));
                h.dot.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#DE350B")));
            } else if (isPast) {
                h.tvLiveStatus.setText("Dep " + point.actualTime);
                h.tvLiveStatus.setTextColor(Color.parseColor("#00875A"));
            } else if (point.estimatedTime != null && !point.estimatedTime.equals("On time")) {
                h.tvLiveStatus.setText("Exp " + point.estimatedTime);
                h.tvLiveStatus.setTextColor(Color.parseColor("#FF8C00"));
            } else {
                h.tvLiveStatus.setText("On time");
                h.tvLiveStatus.setTextColor(Color.parseColor("#00875A"));
            }
        }
    }

    @Override
    public int getItemCount() { return displayList.size(); }

    public static class StopViewHolder extends RecyclerView.ViewHolder {
        TextView tvTime, tvLiveStatus, tvStationName;
        View lineTop, dot, lineBottom;
        public StopViewHolder(View v) {
            super(v);
            tvTime = v.findViewById(R.id.tvTime);
            tvLiveStatus = v.findViewById(R.id.tvLiveStatus);
            tvStationName = v.findViewById(R.id.tvStationName);
            lineTop = v.findViewById(R.id.lineTop);
            dot = v.findViewById(R.id.dot);
            lineBottom = v.findViewById(R.id.lineBottom);
        }
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvStopsCount;
        ImageView iconExpand;
        public HeaderViewHolder(View v) {
            super(v);
            tvStopsCount = v.findViewById(R.id.tvStopsCount);
            iconExpand = v.findViewById(R.id.iconExpand);
        }
    }
}