package io.github.turtle358.callingpoint.ui.adapters;

import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

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
            holder.btnMapTransfer.setVisibility(View.VISIBLE);

            holder.tvTransferTitle.setText("Walk " + currentLeg.durationMinutes + " min");
            holder.ivTransferIcon.setImageResource(R.drawable.ic_walk);

            holder.cardTransfer.setOnClickListener(v -> {
                try {
                    String destQuery = java.net.URLEncoder.encode(currentLeg.toStationName + " Station, London", StandardCharsets.UTF_8);
                    String uriString = "https://www.google.com/maps/dir/?api=1&destination=" + destQuery + "&travelmode=walking";
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uriString));
                    v.getContext().startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(v.getContext(), "Unable to open Google Maps", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        holder.headerOperator.setVisibility(View.VISIBLE);
        holder.layoutTimeline.setVisibility(View.VISIBLE);
        holder.cardTransfer.setVisibility(View.GONE);

        boolean isBus = "BUS".equalsIgnoreCase(currentLeg.transportMode)
                || "bus".equalsIgnoreCase(currentLeg.transportMode)
                || (currentLeg.operatorName != null && currentLeg.operatorName.matches("^[a-zA-Z]{0,2}\\d+[a-zA-Z]?$") && !currentLeg.operatorName.equalsIgnoreCase("c2c"));

        String lookupName = isBus ? "bus" : currentLeg.operatorName;
        int brandColour = TOCBrandHelper.getColourForToc(lookupName);

        if (isBus) {
            String busTitle = currentLeg.operatorName;
            if (busTitle != null && !busTitle.toLowerCase().contains("bus")) {
                busTitle = "Bus " + busTitle;
            }
            holder.tvOperatorName.setText(busTitle);
        } else {
            holder.tvOperatorName.setText(currentLeg.operatorName);
        }

        holder.tvTrainDestination.setText("to " + currentLeg.trainDestination);

        if (holder.ivModeIcon != null) {
            holder.ivModeIcon.setImageResource(lookupName.equalsIgnoreCase("bus") ? R.drawable.ic_bus : R.drawable.ic_train);
        }
        holder.ivManagerLogo.setImageResource(TOCBrandHelper.getIconForToc(lookupName));
        holder.ivManagerLogo.setOnClickListener(v -> TOCBrandHelper.showTOCInfoDialog(v.getContext(), lookupName));

        holder.tvLiveLocation.setVisibility(View.GONE);

        holder.layoutTimeline.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
        boolean[] previousPassed = {true};
        ServiceDetailsResponse liveDetails = liveDataMap.get(currentLeg.serviceId);

        ServiceDetailsResponse.TimelinePoint activeAtPoint = null;
        ServiceDetailsResponse.TimelinePoint activeBetweenFrom = null;
        ServiceDetailsResponse.TimelinePoint activeBetweenTo = null;
        float currentBias = 0.5f;

        Calendar now = Calendar.getInstance();
        int currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        if (liveDetails != null && !liveDetails.isCancelled) {
            List<ServiceDetailsResponse.TimelinePoint> fullRoute = buildFullRoute(liveDetails);
            int lastPassedIdx = -1;

            for (int i = 0; i < fullRoute.size(); i++) {
                ServiceDetailsResponse.TimelinePoint pt = fullRoute.get(i);

                String bestTime = pt.actualTime != null ? pt.actualTime : (pt.estimatedTime != null && !pt.estimatedTime.equalsIgnoreCase("On time") ? pt.estimatedTime : pt.scheduledTime);
                int ptMins = timeToMins(bestTime);

                if ("Arrived".equalsIgnoreCase(pt.estimatedTime)) {
                    activeAtPoint = pt;
                    lastPassedIdx = i;
                } else if ("Departed".equalsIgnoreCase(pt.estimatedTime) || pt.actualTime != null) {
                    lastPassedIdx = i;
                } else if (ptMins != -1) {
                    if (currentMins == ptMins) {
                        activeAtPoint = pt;
                        lastPassedIdx = i;
                    } else if (currentMins > ptMins) {
                        lastPassedIdx = i;
                    }
                }
            }

            if (activeAtPoint == null && lastPassedIdx >= 0 && lastPassedIdx < fullRoute.size() - 1) {
                activeBetweenFrom = fullRoute.get(lastPassedIdx);
                activeBetweenTo = fullRoute.get(lastPassedIdx + 1);

                String t1 = activeBetweenFrom.actualTime != null ? activeBetweenFrom.actualTime : (activeBetweenFrom.estimatedTime != null && !activeBetweenFrom.estimatedTime.equalsIgnoreCase("On time") ? activeBetweenFrom.estimatedTime : activeBetweenFrom.scheduledTime);
                String t2 = activeBetweenTo.estimatedTime != null && !activeBetweenTo.estimatedTime.equalsIgnoreCase("On time") ? activeBetweenTo.estimatedTime : activeBetweenTo.scheduledTime;

                int m1 = timeToMins(t1);
                int m2 = timeToMins(t2);

                if (m1 != -1 && m2 != -1) {
                    if (m2 < m1) m2 += 24 * 60;
                    int cMins = currentMins;
                    if (cMins < m1 - 12 * 60) cMins += 24 * 60;

                    if (m2 == m1) {
                        currentBias = 0.5f;
                    } else {
                        float bias = (float) (cMins - m1) / (m2 - m1);
                        currentBias = Math.max(0.05f, Math.min(0.95f, bias));
                    }
                }
            }
        }

        String safeServiceId = currentLeg.serviceId != null ? currentLeg.serviceId : "unknown";

        List<ServiceDetailsResponse.TimelinePoint> previousStops = getStopsBefore(liveDetails, currentLeg.fromStationName);
        if (!previousStops.isEmpty()) {
            boolean isBeforeExpanded = expandedBeforeSet.contains(safeServiceId);
            addToggleBtn(inflater, holder.layoutTimeline, previousStops.size() + " stations before boarding", isBeforeExpanded, previousPassed[0], brandColour, previousStops.size(), () -> {
                if (expandedBeforeSet.contains(safeServiceId)) expandedBeforeSet.remove(safeServiceId);
                else expandedBeforeSet.add(safeServiceId);
            });

            for (ServiceDetailsResponse.TimelinePoint stop : previousStops) {
                View node = createTimelineNode(inflater, holder.layoutTimeline, stop.locationName, stop.scheduledTime, stop, false, false, brandColour, previousPassed, false, activeAtPoint, activeBetweenFrom, activeBetweenTo, currentBias, safeServiceId);
                node.setVisibility(isBeforeExpanded ? View.VISIBLE : View.GONE);
                holder.layoutTimeline.addView(node);
            }
        }

        ServiceDetailsResponse.TimelinePoint boardingPoint = findLivePoint(liveDetails, currentLeg.fromStationName);
        View originNode = createTimelineNode(inflater, holder.layoutTimeline, currentLeg.fromStationName, currentLeg.departureTime, boardingPoint, previousStops.isEmpty(), false, brandColour, previousPassed, true, activeAtPoint, activeBetweenFrom, activeBetweenTo, currentBias, safeServiceId);
        holder.layoutTimeline.addView(originNode);

        if (currentLeg.intermediateStops != null && !currentLeg.intermediateStops.isEmpty()) {
            addToggleBtn(inflater, holder.layoutTimeline, currentLeg.intermediateStops.size() + " stations", currentLeg.isExpanded, previousPassed[0], brandColour, currentLeg.intermediateStops.size(), () -> {
                currentLeg.isExpanded = !currentLeg.isExpanded;
            });

            for (JourneyLeg.Stop stop : currentLeg.intermediateStops) {
                ServiceDetailsResponse.TimelinePoint stopPoint = findLivePoint(liveDetails, stop.stationName);
                View node = createTimelineNode(inflater, holder.layoutTimeline, stop.stationName, stop.time, stopPoint, false, false, brandColour, previousPassed, false, activeAtPoint, activeBetweenFrom, activeBetweenTo, currentBias, safeServiceId);
                node.setVisibility(currentLeg.isExpanded ? View.VISIBLE : View.GONE);
                holder.layoutTimeline.addView(node);
            }
        }

        ServiceDetailsResponse.TimelinePoint alightPoint = findLivePoint(liveDetails, currentLeg.toStationName);
        List<ServiceDetailsResponse.TimelinePoint> subsequentStops = getStopsAfter(liveDetails, currentLeg.toStationName);

        View destNode = createTimelineNode(inflater, holder.layoutTimeline, currentLeg.toStationName, currentLeg.arrivalTime, alightPoint, false, subsequentStops.isEmpty(), brandColour, previousPassed, true, activeAtPoint, activeBetweenFrom, activeBetweenTo, currentBias, safeServiceId);
        holder.layoutTimeline.addView(destNode);

        if (!subsequentStops.isEmpty()) {
            boolean isAfterExpanded = expandedAfterSet.contains(safeServiceId);
            addToggleBtn(inflater, holder.layoutTimeline, "later stations", isAfterExpanded, previousPassed[0], brandColour, subsequentStops.size(), () -> {
                if (expandedAfterSet.contains(safeServiceId)) expandedAfterSet.remove(safeServiceId);
                else expandedAfterSet.add(safeServiceId);
            });

            for (int i = 0; i < subsequentStops.size(); i++) {
                ServiceDetailsResponse.TimelinePoint stop = subsequentStops.get(i);
                boolean isFinalNode = (i == subsequentStops.size() - 1);
                View node = createTimelineNode(inflater, holder.layoutTimeline, stop.locationName, stop.scheduledTime, stop, false, isFinalNode, brandColour, previousPassed, false, activeAtPoint, activeBetweenFrom, activeBetweenTo, currentBias, safeServiceId);
                node.setVisibility(isAfterExpanded ? View.VISIBLE : View.GONE);
                holder.layoutTimeline.addView(node);
            }
        }

        if (position < legs.size() - 1) {
            JourneyLeg nextLeg = legs.get(position + 1);
            if (!"TUBE/WALK".equals(nextLeg.transportMode)) {
                holder.cardTransfer.setVisibility(View.VISIBLE);
                holder.btnMapTransfer.setVisibility(View.GONE);
                holder.tvTransferTitle.setText("Change at " + currentLeg.toStationName);
                holder.ivTransferIcon.setImageResource(R.drawable.ic_transfer);
            }
        }
    }

    private int timeToMins(String time) {
        if (time == null || time.equalsIgnoreCase("On time")) return -1;
        time = time.replaceAll("[^0-9:]", "").trim();
        if (!time.contains(":")) return -1;
        try {
            String[] p = time.split(":");
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (Exception e) { return -1; }
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
                if (child != null) child.setVisibility(willBeVisible ? View.VISIBLE : View.GONE);
            }
        });
        container.addView(toggleBtn);
    }

    private View createTimelineNode(LayoutInflater inflater, LinearLayout container, String name, String schTime, ServiceDetailsResponse.TimelinePoint livePoint, boolean isFirst, boolean isLast, int brandColour, boolean[] previousPassed, boolean isMajorStop, ServiceDetailsResponse.TimelinePoint activeAtPoint, ServiceDetailsResponse.TimelinePoint activeBetweenFrom, ServiceDetailsResponse.TimelinePoint activeBetweenTo, float currentBias, String serviceId) {
        View node = inflater.inflate(R.layout.item_timeline_node, container, false);

        TextView tvName = node.findViewById(R.id.tvStationName);
        TextView tvSchTime = node.findViewById(R.id.tvSchTime);
        TextView tvLiveTime = node.findViewById(R.id.tvLiveTime);
        TextView tvPlatform = node.findViewById(R.id.tvPlatform);
        View lineTop = node.findViewById(R.id.lineTop);
        View lineBottom = node.findViewById(R.id.lineBottom);
        View dotStation = node.findViewById(R.id.dotStation);

        ImageView iconTrainAt = node.findViewById(R.id.iconTrainAt);
        ImageView iconTrainTop = node.findViewById(R.id.iconTrainTop);
        ImageView iconTrainBottom = node.findViewById(R.id.iconTrainBottom);

        if (livePoint != null && livePoint.scheduledTime != null && !livePoint.scheduledTime.trim().isEmpty()) {
            if ("--:--".equals(schTime) || schTime == null || schTime.trim().isEmpty()) {
                schTime = livePoint.scheduledTime;
            }
        }

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
            } else {
                String bestTime = livePoint.estimatedTime != null && !livePoint.estimatedTime.equalsIgnoreCase("On time") ? livePoint.estimatedTime : livePoint.scheduledTime;
                int ptMins = timeToMins(bestTime);
                Calendar now = Calendar.getInstance();
                int currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
                if (ptMins != -1 && currentMins > ptMins) {
                    isPassed = true;
                }
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

        if (iconTrainAt != null) {
            iconTrainAt.setVisibility(View.GONE);
            iconTrainAt.setImageTintList(android.content.res.ColorStateList.valueOf(brandColour));
        }
        if (iconTrainTop != null) {
            iconTrainTop.setVisibility(View.GONE);
            iconTrainTop.setImageTintList(android.content.res.ColorStateList.valueOf(brandColour));
        }
        if (iconTrainBottom != null) {
            iconTrainBottom.setVisibility(View.GONE);
            iconTrainBottom.setImageTintList(android.content.res.ColorStateList.valueOf(brandColour));
        }

        if (livePoint != null) {
            if (activeAtPoint != null && livePoint.locationName.equalsIgnoreCase(activeAtPoint.locationName)) {
                if (iconTrainAt != null) iconTrainAt.setVisibility(View.VISIBLE);
            } else if (activeBetweenFrom != null && livePoint.locationName.equalsIgnoreCase(activeBetweenFrom.locationName)) {
                if (currentBias <= 0.5f && iconTrainBottom != null) {
                    iconTrainBottom.setVisibility(View.VISIBLE);
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) iconTrainBottom.getLayoutParams();

                    float targetBias = currentBias * 2f;
                    String biasKey = serviceId + "_" + livePoint.locationName + "_bottom";
                    float startBias = previousBiasMap.containsKey(biasKey) ? previousBiasMap.get(biasKey) : 0f;

                    if (startBias != targetBias) {
                        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(startBias, targetBias);
                        anim.setDuration(1000);
                        anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
                        anim.addUpdateListener(a -> {
                            params.verticalBias = (float) a.getAnimatedValue();
                            iconTrainBottom.setLayoutParams(params);
                        });
                        anim.start();
                    } else {
                        params.verticalBias = targetBias;
                        iconTrainBottom.setLayoutParams(params);
                    }
                    previousBiasMap.put(biasKey, targetBias);
                }
            } else if (activeBetweenTo != null && livePoint.locationName.equalsIgnoreCase(activeBetweenTo.locationName)) {
                if (currentBias > 0.5f && iconTrainTop != null) {
                    iconTrainTop.setVisibility(View.VISIBLE);
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) iconTrainTop.getLayoutParams();

                    float targetBias = (currentBias - 0.5f) * 2f;
                    String biasKey = serviceId + "_" + livePoint.locationName + "_top";
                    float startBias = previousBiasMap.containsKey(biasKey) ? previousBiasMap.get(biasKey) : 0f;

                    if (startBias != targetBias) {
                        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(startBias, targetBias);
                        anim.setDuration(1000);
                        anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
                        anim.addUpdateListener(a -> {
                            params.verticalBias = (float) a.getAnimatedValue();
                            iconTrainTop.setLayoutParams(params);
                        });
                        anim.start();
                    } else {
                        params.verticalBias = targetBias;
                        iconTrainTop.setLayoutParams(params);
                    }
                    previousBiasMap.put(biasKey, targetBias);
                }
            }
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
            if (found) results.add(pt);
            else if (targetStation.equalsIgnoreCase(pt.locationName)) found = true;
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
        current.scheduledTime = liveData.scheduledDepartureTime != null ? liveData.scheduledDepartureTime : liveData.scheduledArrivalTime;
        current.estimatedTime = liveData.estimatedDepartureTime != null ? liveData.estimatedDepartureTime : liveData.estimatedArrivalTime;
        current.actualTime = liveData.actualDepartureTime != null ? liveData.actualDepartureTime : liveData.actualArrivalTime;
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

    static class LegViewHolder extends RecyclerView.ViewHolder {
        LinearLayout headerOperator, layoutTimeline;
        TextView tvOperatorName, tvTrainDestination, tvLiveLocation;
        CardView cardTransfer;
        TextView tvTransferTitle;
        ImageView ivManagerLogo, btnMapTransfer, ivModeIcon, ivTransferIcon;

        public LegViewHolder(@NonNull View itemView) {
            super(itemView);
            headerOperator = itemView.findViewById(R.id.headerOperator);
            tvOperatorName = itemView.findViewById(R.id.tvOperatorName);
            tvTrainDestination = itemView.findViewById(R.id.tvTrainDestination);
            tvLiveLocation = itemView.findViewById(R.id.tvLiveLocation);
            ivManagerLogo = itemView.findViewById(R.id.ivManagerLogo);
            ivModeIcon = itemView.findViewById(R.id.ivModeIcon);
            layoutTimeline = itemView.findViewById(R.id.layoutTimeline);
            cardTransfer = itemView.findViewById(R.id.cardTransfer);
            tvTransferTitle = itemView.findViewById(R.id.tvTransferTitle);
            btnMapTransfer = itemView.findViewById(R.id.btnMapTransfer);
            ivTransferIcon = itemView.findViewById(R.id.ivTransferIcon);
        }
    }
}