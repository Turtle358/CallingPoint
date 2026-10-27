package io.github.turtle358.callingpoint.ui.adapters;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyLeg;
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;

public class JourneyLegAdapter extends RecyclerView.Adapter<JourneyLegAdapter.LegViewHolder> {

    private final List<JourneyLeg> legs;
    private final Map<String, ServiceDetailsResponse> liveDataMap = new HashMap<>();
    private final int COLOUR_FUTURE_LINE = Color.parseColor("#444444");
    private final int COLOUR_FUTURE_DOT = Color.parseColor("#888888");

    private final Set<String> expandedBeforeSet = new HashSet<>();
    private final Set<String> expandedAfterSet = new HashSet<>();
    private final Map<String, Float> previousBiasMap = new HashMap<>();

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
        return new LegViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_journey_leg, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull LegViewHolder holder, int position) {
        JourneyLeg leg = legs.get(position);

        if ("TUBE/WALK".equals(leg.transportMode) || "TRANSFER".equals(leg.transportMode)) {
            holder.cardLegBackground.setVisibility(View.GONE);
            holder.contentContainer.setVisibility(View.GONE);
            holder.cardTransfer.setVisibility(View.VISIBLE);
            holder.btnMapTransfer.setVisibility(View.VISIBLE);
            holder.tvTransferTitle.setText("Walk " + leg.durationMinutes + " min");
            holder.ivTransferIcon.setImageResource(R.drawable.ic_walk);

            holder.cardTransfer.setOnClickListener(v -> {
                try {
                    String uriString = "https://www.google.com/maps/dir/?api=1&destination="
                            + URLEncoder.encode(leg.toStationName + " Station, London", StandardCharsets.UTF_8.toString())
                            + "&travelmode=walking";
                    v.getContext().startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uriString)));
                } catch (Exception e) {
                    Toast.makeText(v.getContext(), "Unable to open Maps", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        holder.cardLegBackground.setVisibility(View.VISIBLE);
        holder.contentContainer.setVisibility(View.VISIBLE);

        boolean nextIsBoardable = position < legs.size() - 1 && !("TUBE/WALK".equals(legs.get(position + 1).transportMode) || "TRANSFER".equals(legs.get(position + 1).transportMode));
        if (nextIsBoardable) {
            holder.cardTransfer.setVisibility(View.VISIBLE);
            holder.cardTransfer.setClickable(false);
            holder.btnMapTransfer.setVisibility(View.GONE);
            holder.ivTransferIcon.setImageResource(R.drawable.ic_transfer);
            holder.tvTransferTitle.setText("Change at " + leg.toStationName);
        } else {
            holder.cardTransfer.setVisibility(View.GONE);
        }

        boolean isBus = "BUS".equalsIgnoreCase(leg.transportMode) || (leg.operatorName != null && leg.operatorName.matches("^[a-zA-Z]{0,2}\\d+[a-zA-Z]?$") && !leg.operatorName.equalsIgnoreCase("c2c"));
        String lookupName = isBus ? "bus" : leg.operatorName;
        int brandColour = TOCBrandHelper.getColourForToc(lookupName);

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(brandColour);
        pillBg.setCornerRadius(holder.itemView.getContext().getResources().getDisplayMetrics().density * 8);
        holder.tvOperatorName.setBackground(pillBg);
        holder.tvOperatorName.setTextColor(TOCBrandHelper.getSecondaryColourForToc(lookupName));
        holder.tvOperatorName.setText(isBus ? (leg.operatorName.toLowerCase().contains("bus") ? leg.operatorName : "Bus " + leg.operatorName) : leg.operatorName);

        holder.tvTrainDestination.setText("to " + leg.trainDestination);
        holder.ivModeIcon.setImageResource(isBus ? R.drawable.ic_bus : R.drawable.ic_train);
        holder.ivManagerLogo.setImageResource(TOCBrandHelper.getIconForToc(lookupName));
        holder.ivManagerLogo.setOnClickListener(v -> TOCBrandHelper.showTOCInfoDialog(v.getContext(), lookupName));

        holder.layoutTimeline.removeAllViews();
        buildTimeline(holder, leg, brandColour);
    }

    private void buildTimeline(LegViewHolder holder, JourneyLeg leg, int brandColour) {
        LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
        ServiceDetailsResponse liveDetails = liveDataMap.get(leg.serviceId);
        String safeId = leg.serviceId != null ? leg.serviceId : "unknown";

        boolean[] previousPassed = {true};
        float currentBias = 0.5f;
        ServiceDetailsResponse.TimelinePoint activeAt = null, activeFrom = null, activeTo = null;

        if (liveDetails != null && !liveDetails.isCancelled) {
            List<ServiceDetailsResponse.TimelinePoint> fullRoute = buildFullRoute(liveDetails);
            int lastPassedIdx = -1;
            int currentMins = getCurrentMins();

            for (int i = 0; i < fullRoute.size(); i++) {
                ServiceDetailsResponse.TimelinePoint pt = fullRoute.get(i);
                String bestTime = pt.actualTime != null ? pt.actualTime : (pt.estimatedTime != null && !pt.estimatedTime.equalsIgnoreCase("On time") ? pt.estimatedTime : pt.scheduledTime);
                int ptMins = timeToMins(bestTime);

                if ("Arrived".equalsIgnoreCase(pt.estimatedTime)) {
                    activeAt = pt; lastPassedIdx = i;
                } else if ("Departed".equalsIgnoreCase(pt.estimatedTime) || pt.actualTime != null) {
                    lastPassedIdx = i;
                } else if (ptMins != -1) {
                    if (currentMins == ptMins) { activeAt = pt; lastPassedIdx = i; }
                    else if (currentMins > ptMins) lastPassedIdx = i;
                }
            }

            if (activeAt == null && lastPassedIdx >= 0 && lastPassedIdx < fullRoute.size() - 1) {
                activeFrom = fullRoute.get(lastPassedIdx);
                activeTo = fullRoute.get(lastPassedIdx + 1);

                String t1 = activeFrom.actualTime != null ? activeFrom.actualTime : (activeFrom.estimatedTime != null && !activeFrom.estimatedTime.equalsIgnoreCase("On time") ? activeFrom.estimatedTime : activeFrom.scheduledTime);
                String t2 = activeTo.estimatedTime != null && !activeTo.estimatedTime.equalsIgnoreCase("On time") ? activeTo.estimatedTime : activeTo.scheduledTime;

                int m1 = timeToMins(t1), m2 = timeToMins(t2);
                if (m1 != -1 && m2 != -1) {
                    if (m2 < m1) m2 += 1440;
                    int cMins = currentMins;
                    if (cMins < m1 - 720) cMins += 1440;
                    currentBias = m2 == m1 ? 0.5f : Math.max(0.05f, Math.min(0.95f, (float) (cMins - m1) / (m2 - m1)));
                }
            }
        }

        NodeContext ctx = new NodeContext(brandColour, previousPassed, activeAt, activeFrom, activeTo, currentBias, safeId);

        List<ServiceDetailsResponse.TimelinePoint> previousStops = getStopsBefore(liveDetails, leg.fromStationName);
        if (!previousStops.isEmpty()) {
            boolean isExpanded = expandedBeforeSet.contains(safeId);
            addToggleBtn(inflater, holder.layoutTimeline, previousStops.size() + " stations before boarding", isExpanded, previousPassed[0], brandColour, previousStops.size(), () -> {
                if (!expandedBeforeSet.add(safeId)) expandedBeforeSet.remove(safeId);
            });
            for (ServiceDetailsResponse.TimelinePoint stop : previousStops) {
                View n = createTimelineNode(inflater, holder.layoutTimeline, stop.locationName, stop.scheduledTime, null, stop, ctx.configure(false, false, false));
                n.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
                holder.layoutTimeline.addView(n);
            }
        }

        holder.layoutTimeline.addView(createTimelineNode(inflater, holder.layoutTimeline, leg.fromStationName, leg.departureTime, leg.departurePlatform, findLivePoint(liveDetails, leg.fromStationName), ctx.configure(previousStops.isEmpty(), false, true)));

        if (leg.intermediateStops != null && !leg.intermediateStops.isEmpty()) {
            addToggleBtn(inflater, holder.layoutTimeline, leg.intermediateStops.size() + " stations", leg.isExpanded, previousPassed[0], brandColour, leg.intermediateStops.size(), () -> leg.isExpanded = !leg.isExpanded);
            for (JourneyLeg.Stop stop : leg.intermediateStops) {
                View n = createTimelineNode(inflater, holder.layoutTimeline, stop.stationName, stop.time, null, findLivePoint(liveDetails, stop.stationName), ctx.configure(false, false, false));
                n.setVisibility(leg.isExpanded ? View.VISIBLE : View.GONE);
                holder.layoutTimeline.addView(n);
            }
        }

        List<ServiceDetailsResponse.TimelinePoint> subsequentStops = getStopsAfter(liveDetails, leg.toStationName);
        holder.layoutTimeline.addView(createTimelineNode(inflater, holder.layoutTimeline, leg.toStationName, leg.arrivalTime, leg.arrivalPlatform, findLivePoint(liveDetails, leg.toStationName), ctx.configure(false, subsequentStops.isEmpty(), true)));

        if (!subsequentStops.isEmpty()) {
            boolean isExpanded = expandedAfterSet.contains(safeId);
            addToggleBtn(inflater, holder.layoutTimeline, "later stations", isExpanded, previousPassed[0], brandColour, subsequentStops.size(), () -> {
                if (!expandedAfterSet.add(safeId)) expandedAfterSet.remove(safeId);
            });
            for (int i = 0; i < subsequentStops.size(); i++) {
                ServiceDetailsResponse.TimelinePoint stop = subsequentStops.get(i);
                View n = createTimelineNode(inflater, holder.layoutTimeline, stop.locationName, stop.scheduledTime, null, stop, ctx.configure(false, i == subsequentStops.size() - 1, false));
                n.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
                holder.layoutTimeline.addView(n);
            }
        }
    }

    private int timeToMins(String time) {
        if (time == null || time.equalsIgnoreCase("On time")) return -1;
        try {
            String[] p = time.replaceAll("[^0-9:]", "").trim().split(":");
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (Exception e) { return -1; }
    }

    private int getCurrentMins() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    }

    private void addToggleBtn(LayoutInflater inflater, LinearLayout container, String label, boolean isExpanded, boolean isPassed, int brandColour, int childCount, Runnable onToggle) {
        View toggleBtn = inflater.inflate(R.layout.item_timeline_toggle, container, false);
        TextView tvText = toggleBtn.findViewById(R.id.tvToggleText);
        toggleBtn.findViewById(R.id.lineToggle).setBackgroundColor(isPassed ? brandColour : COLOUR_FUTURE_LINE);

        tvText.setText(isExpanded ? "Hide stations ∧" : "View " + label + " ∨");
        toggleBtn.setTag(isExpanded);

        toggleBtn.setOnClickListener(v -> {
            boolean willBeVisible = !((boolean) v.getTag());
            v.setTag(willBeVisible);
            tvText.setText(willBeVisible ? "Hide stations ∧" : "View " + label + " ∨");
            if (onToggle != null) onToggle.run();

            AutoTransition transition = new AutoTransition();
            transition.setDuration(200);
            TransitionManager.beginDelayedTransition((ViewGroup) container.getRootView(), transition);

            int btnIndex = container.indexOfChild(v);
            for (int i = 1; i <= childCount; i++) {
                View child = container.getChildAt(btnIndex + i);
                if (child != null) child.setVisibility(willBeVisible ? View.VISIBLE : View.GONE);
            }
        });
        container.addView(toggleBtn);
    }

    private View createTimelineNode(LayoutInflater inflater, LinearLayout container, String name, String schTime, String platformText, ServiceDetailsResponse.TimelinePoint livePoint, NodeContext ctx) {
        View node = inflater.inflate(R.layout.item_timeline_node, container, false);
        TextView tvName = node.findViewById(R.id.tvStationName), tvSchTime = node.findViewById(R.id.tvSchTime), tvLiveTime = node.findViewById(R.id.tvLiveTime), tvPlatform = node.findViewById(R.id.tvPlatform);
        View lineTop = node.findViewById(R.id.lineTop), lineBottom = node.findViewById(R.id.lineBottom), dotStation = node.findViewById(R.id.dotStation);
        ImageView iconAt = node.findViewById(R.id.iconTrainAt), iconTop = node.findViewById(R.id.iconTrainTop), iconBottom = node.findViewById(R.id.iconTrainBottom);

        schTime = (livePoint != null && livePoint.scheduledTime != null && (schTime == null || schTime.equals("--:--"))) ? livePoint.scheduledTime : schTime;
        tvName.setText(name);
        tvSchTime.setText(schTime);

        if (ctx.isMajorStop) {
            tvName.setTextSize(18);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            tvSchTime.setTypeface(null, android.graphics.Typeface.BOLD);
        }

        if (ctx.isFirst) lineTop.setVisibility(View.INVISIBLE);
        if (ctx.isLast) lineBottom.setVisibility(View.INVISIBLE);

        boolean isPassed = false;
        String resolvedPlatform = (platformText != null && !platformText.trim().isEmpty()) ? platformText : (livePoint != null ? livePoint.platform : null);

        if (resolvedPlatform != null && !resolvedPlatform.trim().isEmpty()) {
            tvPlatform.setText("Platform " + resolvedPlatform);
            tvPlatform.setVisibility(View.VISIBLE);
        }

        if (livePoint != null) {
            String statusTime = livePoint.actualTime != null ? livePoint.actualTime : livePoint.estimatedTime;

            if (livePoint.actualTime != null || "Arrived".equalsIgnoreCase(livePoint.estimatedTime) || "Departed".equalsIgnoreCase(livePoint.estimatedTime)) {
                isPassed = true;
            } else {
                int ptMins = timeToMins(livePoint.estimatedTime != null && !livePoint.estimatedTime.equalsIgnoreCase("On time") ? livePoint.estimatedTime : livePoint.scheduledTime);
                if (ptMins != -1 && getCurrentMins() > ptMins) isPassed = true;
            }

            if (livePoint.isCancelled || (statusTime != null && !statusTime.equals(schTime) && !statusTime.equalsIgnoreCase("On time"))) {
                tvLiveTime.setText(livePoint.isCancelled ? "Cancelled" : (isPassed ? statusTime : "Exp. " + statusTime));
                tvLiveTime.setTextColor(Color.parseColor("#DE350B"));
                tvLiveTime.setVisibility(View.VISIBLE);
                tvSchTime.setPaintFlags(tvSchTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tvSchTime.setTextColor(Color.parseColor("#9E9E9E"));
            } else {
                tvLiveTime.setText("On time");
                tvLiveTime.setTextColor(Color.parseColor("#00875A"));
                tvLiveTime.setVisibility(View.VISIBLE);
            }
        }

        if (isPassed || ctx.previousPassed[0]) {
            lineTop.setBackgroundColor(ctx.brandColour);
            lineBottom.setBackgroundColor(isPassed ? ctx.brandColour : COLOUR_FUTURE_LINE);

            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            if (isPassed) {
                dot.setColor(ctx.brandColour);
            } else {
                dot.setColor(Color.parseColor("#121212"));
                dot.setStroke(6, ctx.brandColour);
            }
            dotStation.setBackground(dot);
        } else {
            lineTop.setBackgroundColor(COLOUR_FUTURE_LINE);
            lineBottom.setBackgroundColor(COLOUR_FUTURE_LINE);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(Color.parseColor("#121212"));
            dot.setStroke(4, COLOUR_FUTURE_DOT);
            dotStation.setBackground(dot);
        }

        ColorStateList tint = ColorStateList.valueOf(ctx.brandColour);
        if (iconAt != null) { iconAt.setVisibility(View.GONE); iconAt.setImageTintList(tint); }
        if (iconTop != null) { iconTop.setVisibility(View.GONE); iconTop.setImageTintList(tint); }
        if (iconBottom != null) { iconBottom.setVisibility(View.GONE); iconBottom.setImageTintList(tint); }

        if (livePoint != null) {
            if (ctx.activeAt != null && livePoint.locationName.equalsIgnoreCase(ctx.activeAt.locationName)) {
                if (iconAt != null) iconAt.setVisibility(View.VISIBLE);
            } else if (ctx.activeFrom != null && livePoint.locationName.equalsIgnoreCase(ctx.activeFrom.locationName) && ctx.currentBias <= 0.5f) {
                animateIconBias(iconBottom, ctx.currentBias * 2f, ctx.serviceId + "_" + livePoint.locationName + "_bottom");
            } else if (ctx.activeTo != null && livePoint.locationName.equalsIgnoreCase(ctx.activeTo.locationName) && ctx.currentBias > 0.5f) {
                animateIconBias(iconTop, (ctx.currentBias - 0.5f) * 2f, ctx.serviceId + "_" + livePoint.locationName + "_top");
            }
        }

        ctx.previousPassed[0] = isPassed;
        return node;
    }

    private void animateIconBias(ImageView icon, float targetBias, String biasKey) {
        if (icon == null) return;
        icon.setVisibility(View.VISIBLE);
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) icon.getLayoutParams();
        float startBias = previousBiasMap.getOrDefault(biasKey, 0f);

        if (startBias != targetBias) {
            android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(startBias, targetBias);
            anim.setDuration(1000);
            anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            anim.addUpdateListener(a -> {
                params.verticalBias = (float) a.getAnimatedValue();
                icon.setLayoutParams(params);
            });
            anim.start();
        } else {
            params.verticalBias = targetBias;
            icon.setLayoutParams(params);
        }
        previousBiasMap.put(biasKey, targetBias);
    }

    private List<ServiceDetailsResponse.TimelinePoint> buildFullRoute(ServiceDetailsResponse liveData) {
        List<ServiceDetailsResponse.TimelinePoint> fullRoute = new ArrayList<>();
        if (liveData.previousCallingPoints != null && !liveData.previousCallingPoints.isEmpty()) fullRoute.addAll(liveData.previousCallingPoints.get(0).callingPoint);

        ServiceDetailsResponse.TimelinePoint current = new ServiceDetailsResponse.TimelinePoint();
        current.platform = liveData.platform;
        current.locationName = liveData.locationName;
        current.scheduledTime = liveData.scheduledDepartureTime != null ? liveData.scheduledDepartureTime : liveData.scheduledArrivalTime;
        current.estimatedTime = liveData.estimatedDepartureTime != null ? liveData.estimatedDepartureTime : liveData.estimatedArrivalTime;
        current.actualTime = liveData.actualDepartureTime != null ? liveData.actualDepartureTime : liveData.actualArrivalTime;
        fullRoute.add(current);

        if (liveData.subsequentCallingPoints != null && !liveData.subsequentCallingPoints.isEmpty()) fullRoute.addAll(liveData.subsequentCallingPoints.get(0).callingPoint);
        return fullRoute;
    }

    private List<ServiceDetailsResponse.TimelinePoint> getStopsBefore(ServiceDetailsResponse liveData, String target) {
        List<ServiceDetailsResponse.TimelinePoint> res = new ArrayList<>();
        if (liveData == null || target == null) return res;
        for (ServiceDetailsResponse.TimelinePoint pt : buildFullRoute(liveData)) {
            if (target.equalsIgnoreCase(pt.locationName)) break;
            res.add(pt);
        }
        return res;
    }

    private List<ServiceDetailsResponse.TimelinePoint> getStopsAfter(ServiceDetailsResponse liveData, String target) {
        List<ServiceDetailsResponse.TimelinePoint> res = new ArrayList<>();
        if (liveData == null || target == null) return res;
        boolean found = false;
        for (ServiceDetailsResponse.TimelinePoint pt : buildFullRoute(liveData)) {
            if (found) res.add(pt);
            else if (target.equalsIgnoreCase(pt.locationName)) found = true;
        }
        return res;
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

    private class NodeContext {
        boolean isFirst, isLast, isMajorStop;
        int brandColour;
        boolean[] previousPassed;
        ServiceDetailsResponse.TimelinePoint activeAt, activeFrom, activeTo;
        float currentBias;
        String serviceId;

        NodeContext(int brandColour, boolean[] previousPassed, ServiceDetailsResponse.TimelinePoint activeAt, ServiceDetailsResponse.TimelinePoint activeFrom, ServiceDetailsResponse.TimelinePoint activeTo, float currentBias, String serviceId) {
            this.brandColour = brandColour; this.previousPassed = previousPassed;
            this.activeAt = activeAt; this.activeFrom = activeFrom; this.activeTo = activeTo;
            this.currentBias = currentBias; this.serviceId = serviceId;
        }

        NodeContext configure(boolean first, boolean last, boolean major) {
            this.isFirst = first; this.isLast = last; this.isMajorStop = major;
            return this;
        }
    }

    static class LegViewHolder extends RecyclerView.ViewHolder {
        ConstraintLayout contentContainer;
        LinearLayout layoutTimeline;
        TextView tvOperatorName, tvTrainDestination, tvTransferTitle;
        CardView cardTransfer, cardLegBackground;
        ImageView ivManagerLogo, btnMapTransfer, ivModeIcon, ivTransferIcon;

        public LegViewHolder(@NonNull View itemView) {
            super(itemView);
            contentContainer = itemView.findViewById(R.id.contentContainer);
            tvOperatorName = itemView.findViewById(R.id.tvOperatorName);
            tvTrainDestination = itemView.findViewById(R.id.tvTrainDestination);
            ivManagerLogo = itemView.findViewById(R.id.ivManagerLogo);
            ivModeIcon = itemView.findViewById(R.id.ivModeIcon);
            layoutTimeline = itemView.findViewById(R.id.layoutTimeline);
            cardTransfer = itemView.findViewById(R.id.cardTransfer);
            cardLegBackground = itemView.findViewById(R.id.cardLegBackground);
            tvTransferTitle = itemView.findViewById(R.id.tvTransferTitle);
            btnMapTransfer = itemView.findViewById(R.id.btnMapTransfer);
            ivTransferIcon = itemView.findViewById(R.id.ivTransferIcon);
        }
    }
}