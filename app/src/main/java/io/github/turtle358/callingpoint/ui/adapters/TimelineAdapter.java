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
import androidx.core.graphics.ColorUtils;
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
    private final int dimmedColor;
    private final int passedColor = Color.parseColor("#999999");

    private boolean isPreExpanded = false;
    private boolean isPostExpanded = false;
    private int lastPassedIndex = -1;

    private final String userOrigin;
    private final String userDestination;
    private final OnStationClickListener listener;

    public interface OnStationClickListener {
        void onStationClick(String crs);
    }

    private static class TimelineItem {
        int type;
        ServiceDetailsResponse.TimelinePoint point;
        int absoluteIndex;
        int hiddenCount;
        boolean isDarkened;
        boolean isExpanded;

        TimelineItem(int type, ServiceDetailsResponse.TimelinePoint point, int absoluteIndex, boolean isDarkened, boolean isExpanded) {
            this.type = type;
            this.point = point;
            this.absoluteIndex = absoluteIndex;
            this.isDarkened = isDarkened;
            this.isExpanded = isExpanded;
        }
    }

    public TimelineAdapter(List<ServiceDetailsResponse.TimelinePoint> allPoints, int brandColor, String userOrigin, String userDestination, OnStationClickListener listener) {
        this.allPoints = allPoints;
        this.brandColor = brandColor;
        this.dimmedColor = ColorUtils.blendARGB(brandColor, Color.parseColor("#444444"), 0.6f);
        this.userOrigin = userOrigin;
        this.userDestination = userDestination;
        this.listener = listener;
        buildDisplayList(false);
    }

    private void buildDisplayList(boolean animate) {
        List<TimelineItem> oldList = new ArrayList<>(displayList);
        List<TimelineItem> newList = new ArrayList<>();

        lastPassedIndex = -1;
        for (int i = 0; i < allPoints.size(); i++) {
            if (allPoints.get(i).actualTime != null) {
                lastPassedIndex = i;
            }
        }

        List<TimelineItem> preList = new ArrayList<>();
        List<TimelineItem> coreList = new ArrayList<>();
        List<TimelineItem> postList = new ArrayList<>();

        int phase = 0;

        for (int i = 0; i < allPoints.size(); i++) {
            ServiceDetailsResponse.TimelinePoint p = allPoints.get(i);
            boolean matchesOrigin = userOrigin != null && (userOrigin.equalsIgnoreCase(p.locationName) || userOrigin.equalsIgnoreCase(p.crs));
            boolean matchesDest = userDestination != null && (userDestination.equalsIgnoreCase(p.locationName) || userDestination.equalsIgnoreCase(p.crs));

            if (phase == 0 && matchesOrigin) phase = 1;

            if (phase == 0) {
                preList.add(new TimelineItem(TYPE_STOP, p, i, true, false));
            } else if (phase == 1) {
                coreList.add(new TimelineItem(TYPE_STOP, p, i, false, false));
                if (matchesDest) phase = 2;
            } else {
                postList.add(new TimelineItem(TYPE_STOP, p, i, true, false));
            }
        }

        if (coreList.isEmpty()) {
            coreList.addAll(preList);
            preList.clear();
            postList.clear();
        }

        if (!preList.isEmpty()) {
            TimelineItem headerPre = new TimelineItem(TYPE_HEADER_PRE, null, -1, true, isPreExpanded);
            headerPre.hiddenCount = preList.size();
            newList.add(headerPre);
            if (isPreExpanded) newList.addAll(preList);
        }

        newList.addAll(coreList);

        if (!postList.isEmpty()) {
            TimelineItem headerPost = new TimelineItem(TYPE_HEADER_POST, null, -1, true, isPostExpanded);
            headerPost.hiddenCount = postList.size();
            newList.add(headerPost);
            if (isPostExpanded) newList.addAll(postList);
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

                    if (oldItem.type == TYPE_HEADER_PRE || oldItem.type == TYPE_HEADER_POST) {
                        return oldItem.hiddenCount == newItem.hiddenCount && oldItem.isExpanded == newItem.isExpanded;
                    }

                    return oldItem.isDarkened == newItem.isDarkened && oldItem.absoluteIndex == newItem.absoluteIndex;
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

                int nextAbsIndex = isPreExpanded ? 0 : item.hiddenCount;
                boolean isNextPassed = lastPassedIndex >= nextAbsIndex;

                int expectedColor = isNextPassed ? passedColor : (isPreExpanded ? dimmedColor : brandColor);
                h.lineMiddle.setBackgroundColor(expectedColor);

                h.itemView.setOnClickListener(v -> {
                    isPreExpanded = !isPreExpanded;
                    buildDisplayList(true);
                });
            } else if (item.type == TYPE_HEADER_POST) {
                h.tvStopsCount.setText(item.hiddenCount + " later stops");
                h.iconExpand.setImageResource(isPostExpanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);

                int firstPostIndex = allPoints.size() - item.hiddenCount;
                boolean isPassed = lastPassedIndex >= firstPostIndex;
                h.lineMiddle.setBackgroundColor(isPassed ? passedColor : dimmedColor);

                h.itemView.setOnClickListener(v -> {
                    isPostExpanded = !isPostExpanded;
                    buildDisplayList(true);
                });
            }

        } else if (holder instanceof StopViewHolder) {
            StopViewHolder h = (StopViewHolder) holder;
            ServiceDetailsResponse.TimelinePoint point = item.point;
            int absIndex = item.absoluteIndex;

            h.tvStationName.setText(point.locationName);
            h.tvTime.setText(point.scheduledTime);

            float textAlpha = item.isDarkened ? 0.4f : 1.0f;
            h.tvStationName.setAlpha(textAlpha);
            h.tvTime.setAlpha(textAlpha);
            h.tvLiveStatus.setAlpha(textAlpha);

            int activeColor = item.isDarkened ? dimmedColor : brandColor;
            boolean isNextDarkened = (position < displayList.size() - 1) ? displayList.get(position + 1).isDarkened : item.isDarkened;
            int bottomActiveColor = isNextDarkened ? dimmedColor : brandColor;

            boolean isPassed = absIndex <= lastPassedIndex;
            boolean isLastPassedNode = (absIndex == lastPassedIndex);

            h.dot.setBackgroundTintList(ColorStateList.valueOf(isPassed ? passedColor : activeColor));
            h.lineTop.setBackgroundColor(isPassed ? passedColor : activeColor);

            h.lineBottom.setBackgroundColor(isPassed ? passedColor : bottomActiveColor);

            h.lineTop.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
            h.lineBottom.setVisibility(position == displayList.size() - 1 ? View.INVISIBLE : View.VISIBLE);

            if (isLastPassedNode && absIndex < allPoints.size() - 1) {
                h.iconTrainAt.setVisibility(View.GONE);
                h.iconTrainBetween.setVisibility(View.VISIBLE);
                h.iconTrainBetween.setColorFilter(bottomActiveColor);

                h.lineBottom.setBackgroundColor(passedColor);
                if (h.lineBottomActive != null) {
                    h.lineBottomActive.setVisibility(View.VISIBLE);
                    h.lineBottomActive.setBackgroundColor(bottomActiveColor);
                }
            } else {
                h.iconTrainBetween.setVisibility(View.GONE);
                if (h.lineBottomActive != null) {
                    h.lineBottomActive.setVisibility(View.GONE);
                }
                if (isLastPassedNode) {
                    h.iconTrainAt.setVisibility(View.VISIBLE);
                    h.iconTrainAt.setColorFilter(activeColor);
                } else {
                    h.iconTrainAt.setVisibility(View.GONE);
                }
            }

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

            if (point.isCancelled) {
                h.tvLiveStatus.setText("Cancelled");
                h.tvLiveStatus.setTextColor(Color.parseColor("#DE350B"));
                h.dot.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#DE350B")));
                h.tvTime.setPaintFlags(h.tvTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                h.tvTime.setTextColor(Color.parseColor("#9E9E9E"));
            } else if (isPassed) {
                String actual = point.actualTime != null ? point.actualTime : point.scheduledTime;
                boolean isLate = !actual.equals(point.scheduledTime);

                h.tvLiveStatus.setText("Dep " + actual);
                if (isLate) {
                    h.tvLiveStatus.setTextColor(Color.parseColor("#FF8C00"));
                    h.tvTime.setPaintFlags(h.tvTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    h.tvTime.setTextColor(Color.parseColor("#9E9E9E"));
                } else {
                    h.tvLiveStatus.setTextColor(Color.parseColor("#00875A"));
                    h.tvTime.setPaintFlags(h.tvTime.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                }
            } else if (point.estimatedTime != null && !point.estimatedTime.equalsIgnoreCase("On time")) {
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
        View lineTop, dot, lineBottom, lineBottomActive;
        ImageView iconTrainAt, iconTrainBetween;

        public StopViewHolder(View v) {
            super(v);
            tvTime = v.findViewById(R.id.tvTime);
            tvLiveStatus = v.findViewById(R.id.tvLiveStatus);
            tvStationName = v.findViewById(R.id.tvStationName);
            lineTop = v.findViewById(R.id.lineTop);
            dot = v.findViewById(R.id.dot);
            lineBottom = v.findViewById(R.id.lineBottom);
            lineBottomActive = v.findViewById(R.id.lineBottomActive);
            iconTrainAt = v.findViewById(R.id.iconTrainAt);
            iconTrainBetween = v.findViewById(R.id.iconTrainBetween);
        }
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvStopsCount;
        ImageView iconExpand;
        View lineMiddle;

        public HeaderViewHolder(View v) {
            super(v);
            tvStopsCount = v.findViewById(R.id.tvStopsCount);
            iconExpand = v.findViewById(R.id.iconExpand);
            lineMiddle = v.findViewById(R.id.lineMiddle);
        }
    }
}