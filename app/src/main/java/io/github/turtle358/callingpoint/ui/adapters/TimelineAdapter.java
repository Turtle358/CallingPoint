package io.github.turtle358.callingpoint.ui.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse;

public class TimelineAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_STOP = 0;
    private static final int TYPE_HEADER_PRE = 1;
    private static final int TYPE_HEADER_POST = 2;

    private final List<ServiceDetailsResponse.TimelinePoint> allPoints;
    private final List<TimelineItem> displayList = new ArrayList<>();
    private final int brandColor;

    private boolean isPreExpanded = false;
    private boolean isPostExpanded = false;

    private final String userOrigin;
    private final String userDestination;
    private final OnStationClickListener listener;

    public interface OnStationClickListener {
        void onStationClick(String crs);
    }

    private static class TimelineItem {
        int type;
        ServiceDetailsResponse.TimelinePoint point;
        int hiddenCount;
        boolean isDarkened;

        TimelineItem(int type, ServiceDetailsResponse.TimelinePoint point, boolean isDarkened) {
            this.type = type;
            this.point = point;
            this.isDarkened = isDarkened;
        }
    }

    public TimelineAdapter(List<ServiceDetailsResponse.TimelinePoint> allPoints, int brandColor, String userOrigin, String userDestination, OnStationClickListener listener) {
        this.allPoints = allPoints;
        this.brandColor = brandColor;
        this.userOrigin = userOrigin;
        this.userDestination = userDestination;
        this.listener = listener;
        buildDisplayList(false);
    }

    private void buildDisplayList(boolean animate) {
        List<TimelineItem> oldList = new ArrayList<>(displayList);
        List<TimelineItem> newList = new ArrayList<>();

        List<ServiceDetailsResponse.TimelinePoint> preList = new ArrayList<>();
        List<ServiceDetailsResponse.TimelinePoint> coreList = new ArrayList<>();
        List<ServiceDetailsResponse.TimelinePoint> postList = new ArrayList<>();

        int phase = 0; // 0 = pre-origin, 1 = core journey, 2 = post-destination

        for (ServiceDetailsResponse.TimelinePoint p : allPoints) {
            boolean matchesOrigin = userOrigin != null && (userOrigin.equalsIgnoreCase(p.locationName) || userOrigin.equalsIgnoreCase(p.crs));
            boolean matchesDest = userDestination != null && (userDestination.equalsIgnoreCase(p.locationName) || userDestination.equalsIgnoreCase(p.crs));

            if (phase == 0 && matchesOrigin) {
                phase = 1;
            }

            if (phase == 0) {
                preList.add(p);
            } else if (phase == 1) {
                coreList.add(p);
                if (matchesDest) {
                    phase = 2;
                }
            } else {
                postList.add(p);
            }
        }

        // Fallback: If origin was never found, show everything as core journey
        if (coreList.isEmpty()) {
            coreList.addAll(preList);
            preList.clear();
            postList.clear();
        }

        // Build Pre-Origin Section
        if (!preList.isEmpty()) {
            TimelineItem headerPre = new TimelineItem(TYPE_HEADER_PRE, null, false);
            headerPre.hiddenCount = preList.size();
            newList.add(headerPre);

            if (isPreExpanded) {
                for (ServiceDetailsResponse.TimelinePoint p : preList) {
                    newList.add(new TimelineItem(TYPE_STOP, p, true));
                }
            }
        }

        // Build Core Journey Section
        for (ServiceDetailsResponse.TimelinePoint p : coreList) {
            newList.add(new TimelineItem(TYPE_STOP, p, false));
        }

        // Build Post-Destination Section
        if (!postList.isEmpty()) {
            TimelineItem headerPost = new TimelineItem(TYPE_HEADER_POST, null, false);
            headerPost.hiddenCount = postList.size();
            newList.add(headerPost);

            if (isPostExpanded) {
                for (ServiceDetailsResponse.TimelinePoint p : postList) {
                    newList.add(new TimelineItem(TYPE_STOP, p, true));
                }
            }
        }

        if (animate) {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() { return oldList.size(); }
                @Override
                public int getNewListSize() { return newList.size(); }
                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    TimelineItem oldItem = oldList.get(oldItemPosition);
                    TimelineItem newItem = newList.get(newItemPosition);
                    if (oldItem.type != newItem.type) return false;
                    if (oldItem.type == TYPE_HEADER_PRE || oldItem.type == TYPE_HEADER_POST) return true;
                    return oldItem.point.locationName.equals(newItem.point.locationName);
                }
                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    TimelineItem oldItem = oldList.get(oldItemPosition);
                    TimelineItem newItem = newList.get(newItemPosition);
                    if (oldItem.type != newItem.type) return false;
                    if (oldItem.type == TYPE_HEADER_PRE || oldItem.type == TYPE_HEADER_POST) return oldItem.hiddenCount == newItem.hiddenCount;
                    return oldItem.isDarkened == newItem.isDarkened;
                }
            });
            displayList.clear();
            displayList.addAll(newList);
            result.dispatchUpdatesTo(this);
        } else {
            displayList.clear();
            displayList.addAll(newList);
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemViewType(int position) {
        return displayList.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER_PRE || viewType == TYPE_HEADER_POST) {
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

            if (item.type == TYPE_HEADER_PRE) {
                h.tvStopsCount.setText(item.hiddenCount + " previous stops");
                h.iconExpand.setImageResource(isPreExpanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);
                h.itemView.setOnClickListener(v -> {
                    isPreExpanded = !isPreExpanded;
                    buildDisplayList(true);
                });
            } else if (item.type == TYPE_HEADER_POST) {
                h.tvStopsCount.setText(item.hiddenCount + " later stops");
                h.iconExpand.setImageResource(isPostExpanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);
                h.itemView.setOnClickListener(v -> {
                    isPostExpanded = !isPostExpanded;
                    buildDisplayList(true);
                });
            }

        } else if (holder instanceof StopViewHolder) {
            StopViewHolder h = (StopViewHolder) holder;
            ServiceDetailsResponse.TimelinePoint point = item.point;

            h.tvStationName.setText(point.locationName);
            h.tvTime.setText(point.scheduledTime);

            // Dim outer nodes for visual emphasis on core journey
            h.itemView.setAlpha(item.isDarkened ? 0.4f : 1.0f);

            // Apply standard selectable background to all nodes
            android.util.TypedValue outValue = new android.util.TypedValue();
            h.itemView.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            h.itemView.setBackgroundResource(outValue.resourceId);

            h.itemView.setOnClickListener(v -> {
                String crs = point.crs;
                if (crs == null || crs.trim().isEmpty()) {
                    crs = io.github.turtle358.callingpoint.utils.StationHelper.getCrsFromName(point.locationName);
                }
                if (crs != null && !crs.isEmpty() && listener != null) {
                    listener.onStationClick(crs);
                }
            });

            h.lineTop.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
            h.lineBottom.setVisibility(position == displayList.size() - 1 ? View.INVISIBLE : View.VISIBLE);

            boolean isPast = point.actualTime != null;
            boolean prevIsPast = false;

            if (position > 0) {
                TimelineItem prev = displayList.get(position - 1);
                if (prev.type == TYPE_STOP && prev.point.actualTime != null) prevIsPast = true;
                if (prev.type == TYPE_HEADER_PRE || prev.type == TYPE_HEADER_POST) prevIsPast = true;
            } else {
                prevIsPast = true;
            }

            boolean isNextStop = !isPast && prevIsPast;

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
                h.tvTime.setPaintFlags(h.tvTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                h.tvTime.setTextColor(Color.parseColor("#9E9E9E"));
            } else if (isPast) {
                h.tvLiveStatus.setText("Dep " + point.actualTime);
                h.tvLiveStatus.setTextColor(Color.parseColor("#00875A"));
                h.tvTime.setPaintFlags(h.tvTime.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            } else if (point.estimatedTime != null && !point.estimatedTime.equals("On time")) {
                h.tvLiveStatus.setText("Exp " + point.estimatedTime);
                h.tvLiveStatus.setTextColor(Color.parseColor("#FF8C00"));
                h.tvTime.setPaintFlags(h.tvTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                h.tvTime.setTextColor(Color.parseColor("#9E9E9E"));
            } else {
                h.tvLiveStatus.setText("On time");
                h.tvLiveStatus.setTextColor(Color.parseColor("#00875A"));
                h.tvTime.setPaintFlags(h.tvTime.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
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