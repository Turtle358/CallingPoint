package io.github.turtle358.callingpoint.ui.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyLeg;
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;

public class JourneyLegAdapter extends RecyclerView.Adapter<JourneyLegAdapter.LegViewHolder> {

    private final List<JourneyLeg> legs;
    private final Map<String, ServiceDetailsResponse> liveDataMap = new HashMap<>();
    private final int COLOUR_FUTURE_LINE = Color.parseColor("#444444");
    private final int COLOUR_FUTURE_DOT = Color.parseColor("#888888");

    public JourneyLegAdapter(List<JourneyLeg> legs) {
        this.legs = legs;
    }

    public void updateLiveData(Map<String, ServiceDetailsResponse> liveDataMap) {
        this.liveDataMap.clear();
        this.liveDataMap.putAll(liveDataMap);
        notifyDataSetChanged();
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
            holder.layoutTimeline.setVisibility(View.GONE);
            holder.cardTransfer.setVisibility(View.VISIBLE);
            holder.tvTransferTitle.setText("Underground / Walk to " + currentLeg.toStationName);
            holder.tvTransferDuration.setText(currentLeg.durationMinutes + " min transfer");
            holder.ivTransferIcon.setImageResource(R.drawable.ic_tfl);
            return;
        }

        holder.headerOperator.setVisibility(View.VISIBLE);
        holder.layoutTimeline.setVisibility(View.VISIBLE);
        holder.cardTransfer.setVisibility(View.GONE);

        holder.tvOperatorName.setText(currentLeg.operatorName);
        holder.tvTrainDestination.setText("to " + currentLeg.trainDestination);
        int brandColour = TOCBrandHelper.getColourForToc(currentLeg.operatorName);
        holder.ivManagerLogo.setImageResource(TOCBrandHelper.getIconForToc(currentLeg.operatorName));

        ServiceDetailsResponse liveDetails = liveDataMap.get(currentLeg.serviceId);

        if (liveDetails != null) {
            String updatedLocation = io.github.turtle358.callingpoint.utils.LiveLocationEngine.getLiveLocation(liveDetails);
            holder.tvLiveLocation.setText(updatedLocation);
            holder.tvLiveLocation.setVisibility(View.VISIBLE);
            holder.tvLiveLocation.setTextColor(liveDetails.isCancelled ? Color.parseColor("#DE350B") : Color.parseColor("#00875A"));
        } else {
            holder.tvLiveLocation.setText(currentLeg.liveLocation != null ? currentLeg.liveLocation : "");
            holder.tvLiveLocation.setVisibility(currentLeg.liveLocation != null ? View.VISIBLE : View.GONE);
            holder.tvLiveLocation.setTextColor(Color.parseColor("#888888"));
        }

        holder.layoutTimeline.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
        boolean[] previousPassed = {true};

        List<ServiceDetailsResponse.TimelinePoint> previousStops = getStopsBefore(liveDetails, currentLeg.fromStationName);
        if (!previousStops.isEmpty()) {
            addToggleBtn(inflater, holder.layoutTimeline, previousStops.size() + " stations before boarding", false, previousPassed[0], brandColour, previousStops.size(), null);

            for (ServiceDetailsResponse.TimelinePoint stop : previousStops) {
                View node = createTimelineNode(inflater, holder.layoutTimeline, stop.locationName, stop.scheduledTime, stop, false, false, brandColour, previousPassed, false);
                node.setVisibility(View.GONE); 
                holder.layoutTimeline.addView(node);
            }
        }

        ServiceDetailsResponse.TimelinePoint boardingPoint = findLivePoint(liveDetails, currentLeg.fromStationName);
        View originNode = createTimelineNode(inflater, holder.layoutTimeline, currentLeg.fromStationName, currentLeg.departureTime, boardingPoint, previousStops.isEmpty(), false, brandColour, previousPassed, true);
        holder.layoutTimeline.addView(originNode);

        if (currentLeg.intermediateStops != null && !currentLeg.intermediateStops.isEmpty()) {
            addToggleBtn(inflater, holder.layoutTimeline, currentLeg.intermediateStops.size() + " stations", currentLeg.isExpanded, previousPassed[0], brandColour, currentLeg.intermediateStops.size(), () -> {
                currentLeg.isExpanded = !currentLeg.isExpanded; 
            });

            for (JourneyLeg.Stop stop : currentLeg.intermediateStops) {
                ServiceDetailsResponse.TimelinePoint stopPoint = findLivePoint(liveDetails, stop.stationName);
                View node = createTimelineNode(inflater, holder.layoutTimeline, stop.stationName, stop.time, stopPoint, false, false, brandColour, previousPassed, false);
                node.setVisibility(currentLeg.isExpanded ? View.VISIBLE : View.GONE);
                holder.layoutTimeline.addView(node);
            }
        }

        ServiceDetailsResponse.TimelinePoint alightPoint = findLivePoint(liveDetails, currentLeg.toStationName);
        List<ServiceDetailsResponse.TimelinePoint> subsequentStops = getStopsAfter(liveDetails, currentLeg.toStationName);

        View destNode = createTimelineNode(inflater, holder.layoutTimeline, currentLeg.toStationName, currentLeg.arrivalTime, alightPoint, false, subsequentStops.isEmpty(), brandColour, previousPassed, true);
        holder.layoutTimeline.addView(destNode);

        if (!subsequentStops.isEmpty()) {
            addToggleBtn(inflater, holder.layoutTimeline, "later stations", false, previousPassed[0], brandColour, subsequentStops.size(), null);

            for (int i = 0; i < subsequentStops.size(); i++) {
                ServiceDetailsResponse.TimelinePoint stop = subsequentStops.get(i);
                boolean isFinalNode = (i == subsequentStops.size() - 1);
                View node = createTimelineNode(inflater, holder.layoutTimeline, stop.locationName, stop.scheduledTime, stop, false, isFinalNode, brandColour, previousPassed, false);
                node.setVisibility(View.GONE); 
                holder.layoutTimeline.addView(node);
            }
        }

        if (position < legs.size() - 1) {
            JourneyLeg nextLeg = legs.get(position + 1);
            if (!"TUBE/WALK".equals(nextLeg.transportMode)) {
                holder.cardTransfer.setVisibility(View.VISIBLE);
                holder.tvTransferTitle.setText("Change at " + currentLeg.toStationName);
                holder.ivTransferIcon.setImageResource(R.drawable.ic_transfer);
                holder.tvTransferDuration.setText("Transfer time " + calculateTransferTime(currentLeg.arrivalTime, nextLeg.departureTime) + "m");
            }
        }
    }

    private void addToggleBtn(LayoutInflater inflater, LinearLayout container, String label, boolean isExpanded, boolean isPassed, int brandColour, int childCountToToggle, Runnable onToggle) {
        View toggleBtn = inflater.inflate(R.layout.item_timeline_toggle, container, false);

        TextView tvToggleText = toggleBtn.findViewById(R.id.tvToggleText);
        View lineToggle = toggleBtn.findViewById(R.id.lineToggle);

        tvToggleText.setText(isExpanded ? "Hide stations ∧" : "View " + label + " ∨");
        lineToggle.setBackgroundColor(isPassed ? brandColour : COLOUR_FUTURE_LINE);

        toggleBtn.setTag(isExpanded);
        toggleBtn.setOnClickListener(v -> {
            boolean isCurrentlyVisible = v.getTag() != null && (boolean) v.getTag();
            boolean willBeVisible = !isCurrentlyVisible;
            v.setTag(willBeVisible);

            tvToggleText.setText(willBeVisible ? "Hide stations ∧" : "View " + label + " ∨");

            if (onToggle != null) onToggle.run();

            android.transition.AutoTransition transition = new android.transition.AutoTransition();
            transition.setDuration(200);
            android.transition.TransitionManager.beginDelayedTransition((ViewGroup) container.getRootView(), transition);

            int btnIndex = container.indexOfChild(v);
            for (int i = 1; i <= childCountToToggle; i++) {
                View child = container.getChildAt(btnIndex + i);
                if (child != null) {
                    child.setVisibility(willBeVisible ? View.VISIBLE : View.GONE);
                }
            }
        });

        container.addView(toggleBtn);
    }

    private View createTimelineNode(LayoutInflater inflater, LinearLayout container, String name, String schTime, ServiceDetailsResponse.TimelinePoint livePoint, boolean isFirst, boolean isLast, int brandColour, boolean[] previousPassed, boolean isMajorStop) {
        View node = inflater.inflate(R.layout.item_timeline_node, container, false);

        TextView tvName = node.findViewById(R.id.tvStationName);
        TextView tvSchTime = node.findViewById(R.id.tvSchTime);
        TextView tvLiveTime = node.findViewById(R.id.tvLiveTime);
        TextView tvPlatform = node.findViewById(R.id.tvPlatform);
        View lineTop = node.findViewById(R.id.lineTop);
        View lineBottom = node.findViewById(R.id.lineBottom);
        View dotStation = node.findViewById(R.id.dotStation);

        tvName.setText(name);
        tvSchTime.setText(schTime);

        if (isMajorStop) {
            tvName.setTextSize(16);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            tvSchTime.setTypeface(null, android.graphics.Typeface.BOLD);
        }

        if (isFirst) lineTop.setVisibility(View.INVISIBLE);
        if (isLast) lineBottom.setVisibility(View.INVISIBLE);

        boolean isPassed = false;

        if (livePoint != null) {
            String statusTime = livePoint.actualTime != null ? livePoint.actualTime : livePoint.estimatedTime;

            if (livePoint.platform != null && !livePoint.platform.trim().isEmpty()) {
                tvPlatform.setText("Platform " + livePoint.platform);
                tvPlatform.setVisibility(View.VISIBLE);
            } else {
                tvPlatform.setVisibility(View.GONE);
            }

            if (livePoint.actualTime != null || "Arrived".equalsIgnoreCase(livePoint.estimatedTime) || "Departed".equalsIgnoreCase(livePoint.estimatedTime)) {
                isPassed = true;
            }

            if (livePoint.isCancelled) {
                tvLiveTime.setText("Cancelled");
                tvLiveTime.setTextColor(Color.parseColor("#DE350B"));
                tvLiveTime.setVisibility(View.VISIBLE);
                tvSchTime.setPaintFlags(tvSchTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tvSchTime.setTextColor(Color.parseColor("#9E9E9E"));
            } else if (statusTime != null && !statusTime.equals(schTime) && !statusTime.equalsIgnoreCase("On time")) {
                tvLiveTime.setText(isPassed ? statusTime : "Exp. " + statusTime);
                tvLiveTime.setTextColor(Color.parseColor("#DE350B"));
                tvLiveTime.setVisibility(View.VISIBLE);
                tvSchTime.setPaintFlags(tvSchTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tvSchTime.setTextColor(Color.parseColor("#9E9E9E"));
            } else {
                tvLiveTime.setText("On time");
                tvLiveTime.setTextColor(Color.parseColor("#00875A"));
                tvLiveTime.setVisibility(View.VISIBLE);
            }
        } else {
            tvPlatform.setVisibility(View.GONE);
        }

        if (isPassed) {
            lineTop.setBackgroundColor(brandColour);
            lineBottom.setBackgroundColor(brandColour);

            android.graphics.drawable.GradientDrawable pastDot = new android.graphics.drawable.GradientDrawable();
            pastDot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            pastDot.setColor(brandColour);
            dotStation.setBackground(pastDot);

        } else if (previousPassed[0]) {
            lineTop.setBackgroundColor(brandColour);
            lineBottom.setBackgroundColor(COLOUR_FUTURE_LINE);

            android.graphics.drawable.GradientDrawable currentDot = new android.graphics.drawable.GradientDrawable();
            currentDot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            currentDot.setColor(Color.parseColor("#121212"));
            currentDot.setStroke(6, brandColour);
            dotStation.setBackground(currentDot);

        } else {
            lineTop.setBackgroundColor(COLOUR_FUTURE_LINE);
            lineBottom.setBackgroundColor(COLOUR_FUTURE_LINE);

            android.graphics.drawable.GradientDrawable futureDot = new android.graphics.drawable.GradientDrawable();
            futureDot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            futureDot.setColor(Color.parseColor("#121212"));
            futureDot.setStroke(4, COLOUR_FUTURE_DOT);
            dotStation.setBackground(futureDot);
        }

        previousPassed[0] = isPassed;
        return node;
    }
    private List<ServiceDetailsResponse.TimelinePoint> getStopsBefore(ServiceDetailsResponse liveData, String targetStation) {
        List<ServiceDetailsResponse.TimelinePoint> results = new ArrayList<>();
        if (liveData == null || targetStation == null) return results;

        List<ServiceDetailsResponse.TimelinePoint> fullRoute = buildFullRoute(liveData);
        for (ServiceDetailsResponse.TimelinePoint pt : fullRoute) {
            if (targetStation.equalsIgnoreCase(pt.locationName)) break;
            results.add(pt);
        }
        return results;
    }

    private List<ServiceDetailsResponse.TimelinePoint> getStopsAfter(ServiceDetailsResponse liveData, String targetStation) {
        List<ServiceDetailsResponse.TimelinePoint> results = new ArrayList<>();
        if (liveData == null || targetStation == null) return results;

        boolean found = false;
        List<ServiceDetailsResponse.TimelinePoint> fullRoute = buildFullRoute(liveData);
        for (ServiceDetailsResponse.TimelinePoint pt : fullRoute) {
            if (found) {
                results.add(pt);
            } else if (targetStation.equalsIgnoreCase(pt.locationName)) {
                found = true;
            }
        }
        return results;
    }

    private List<ServiceDetailsResponse.TimelinePoint> buildFullRoute(ServiceDetailsResponse liveData) {
        List<ServiceDetailsResponse.TimelinePoint> fullRoute = new ArrayList<>();
        if (liveData.previousCallingPoints != null && !liveData.previousCallingPoints.isEmpty()) {
            fullRoute.addAll(liveData.previousCallingPoints.get(0).callingPoint);
        }
        ServiceDetailsResponse.TimelinePoint current = new ServiceDetailsResponse.TimelinePoint();
        current.locationName = liveData.locationName;
        current.scheduledTime = liveData.std != null ? liveData.std : liveData.sta;
        current.estimatedTime = liveData.etd != null ? liveData.etd : liveData.eta;
        current.actualTime = liveData.atd != null ? liveData.atd : liveData.ata;
        fullRoute.add(current);
        if (liveData.subsequentCallingPoints != null && !liveData.subsequentCallingPoints.isEmpty()) {
            fullRoute.addAll(liveData.subsequentCallingPoints.get(0).callingPoint);
        }
        return fullRoute;
    }

    private ServiceDetailsResponse.TimelinePoint findLivePoint(ServiceDetailsResponse liveData, String stationName) {
        if (liveData == null || stationName == null) return null;
        for (ServiceDetailsResponse.TimelinePoint cp : buildFullRoute(liveData)) {
            if (stationName.equalsIgnoreCase(cp.locationName)) return cp;
        }
        return null;
    }

    @Override
    public int getItemCount() { return legs != null ? legs.size() : 0; }

    private int calculateTransferTime(String arrival, String nextDeparture) {
        try {
            String[] arr = arrival.split(":");
            String[] dep = nextDeparture.split(":");
            int diff = (Integer.parseInt(dep[0]) * 60 + Integer.parseInt(dep[1])) - (Integer.parseInt(arr[0]) * 60 + Integer.parseInt(arr[1]));
            return diff < 0 ? diff + 1440 : diff;
        } catch (Exception e) { return 0; }
    }

    static class LegViewHolder extends RecyclerView.ViewHolder {
        LinearLayout headerOperator, layoutTimeline;
        TextView tvOperatorName, tvTrainDestination, tvLiveLocation;
        CardView cardTransfer;
        TextView tvTransferTitle, tvTransferDuration;
        ImageView ivManagerLogo;
        ImageView ivTransferIcon;

        public LegViewHolder(@NonNull View itemView) {
            super(itemView);
            headerOperator = itemView.findViewById(R.id.headerOperator);
            tvOperatorName = itemView.findViewById(R.id.tvOperatorName);
            tvTrainDestination = itemView.findViewById(R.id.tvTrainDestination);
            tvLiveLocation = itemView.findViewById(R.id.tvLiveLocation);
            ivManagerLogo = itemView.findViewById(R.id.ivManagerLogo);
            layoutTimeline = itemView.findViewById(R.id.layoutTimeline);
            cardTransfer = itemView.findViewById(R.id.cardTransfer);
            tvTransferTitle = itemView.findViewById(R.id.tvTransferTitle);
            tvTransferDuration = itemView.findViewById(R.id.tvTransferDuration);
            ivTransferIcon = itemView.findViewById(R.id.ivTransferIcon);
        }
    }
}