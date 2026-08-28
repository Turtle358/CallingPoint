package io.github.turtle358.callingpoint.ui.adapters;

import android.annotation.SuppressLint;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.StationDepartureResponse;
import io.github.turtle358.callingpoint.ui.activities.ServiceDetailsActivity;
import io.github.turtle358.callingpoint.utils.StationHelper;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;

public class ServiceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    private final Context context;
    private final List<StationDepartureResponse.TrainService> upcomingServices = new ArrayList<>();
    private final List<StationDepartureResponse.TrainService> earlierServices = new ArrayList<>();
    private boolean isEarlierExpanded = false;
    private boolean isArrivals = false;

    private String userOriginStation = "";
    private String userArrivingStation = "";
    private int lastPosition = -1;

    public ServiceAdapter(Context context, List<StationDepartureResponse.TrainService> services) {
        this.context = context;
        processServices(services);
    }

    public void updateData(List<StationDepartureResponse.TrainService> newServices, boolean isArrivals, String departingCrs, String arrivingCrs) {
        this.isArrivals = isArrivals;
        this.userOriginStation = StationHelper.getNameFromCode(departingCrs);
        this.userArrivingStation = StationHelper.getNameFromCode(arrivingCrs);
        this.lastPosition = -1;
        processServices(newServices);
        notifyDataSetChanged();
    }

    private void processServices(List<StationDepartureResponse.TrainService> allServices) {
        upcomingServices.clear();
        earlierServices.clear();
        if (allServices == null) return;

        for (StationDepartureResponse.TrainService service : allServices) {
            if (isServicePassed(service)) {
                earlierServices.add(service);
            } else {
                upcomingServices.add(service);
            }
        }
    }

    private boolean isServicePassed(StationDepartureResponse.TrainService service) {
        if ((service.atd != null && !service.atd.isEmpty()) || (service.ata != null && !service.ata.isEmpty())) {
            return true;
        }

        String schTime = service.std != null ? service.std : service.sta;
        String estTime = service.etd != null ? service.etd : service.eta;

        if ("Cancelled".equalsIgnoreCase(estTime) || "Delayed".equalsIgnoreCase(estTime)) {
            return false;
        }

        String timeToUse = (estTime != null && estTime.contains(":")) ? estTime : schTime;
        if (timeToUse == null || !timeToUse.contains(":")) return false;

        try {
            String[] parts = timeToUse.split(":");
            int targetMins = Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());

            Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
            int currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

            int diffMins = targetMins - currentMins;
            if (diffMins > 720) diffMins -= 1440;
            if (diffMins < -720) diffMins += 1440;

            return diffMins < -1;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (!earlierServices.isEmpty() && position == 0) {
            return VIEW_TYPE_HEADER;
        }
        return VIEW_TYPE_ITEM;
    }

    @Override
    public int getItemCount() {
        int count = upcomingServices.size();
        if (!earlierServices.isEmpty()) {
            count += 1;
            if (isEarlierExpanded) {
                count += earlierServices.size();
            }
        }
        return count;
    }

    private StationDepartureResponse.TrainService getServiceForPosition(int position) {
        if (earlierServices.isEmpty()) {
            return upcomingServices.get(position);
        }
        int itemIndex = position - 1;
        if (isEarlierExpanded) {
            if (itemIndex < earlierServices.size()) {
                return earlierServices.get(itemIndex);
            } else {
                return upcomingServices.get(itemIndex - earlierServices.size());
            }
        } else {
            return upcomingServices.get(itemIndex);
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View v = LayoutInflater.from(context).inflate(R.layout.item_earlier_header, parent, false);
            return new HeaderViewHolder(v);
        } else {
            View v = LayoutInflater.from(context).inflate(R.layout.item_service, parent, false);
            return new ItemViewHolder(v);
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder hHolder = (HeaderViewHolder) holder;

            if (isEarlierExpanded) {
                hHolder.tvEarlierTitle.setText("Hide previous departures");
                hHolder.ivExpandEarlier.setRotation(180f);
            } else {
                hHolder.tvEarlierTitle.setText("Show previous departures (" + earlierServices.size() + ")");
                hHolder.ivExpandEarlier.setRotation(0f);
            }

            hHolder.itemView.setOnClickListener(v -> {
                isEarlierExpanded = !isEarlierExpanded;
                hHolder.ivExpandEarlier.animate().rotation(isEarlierExpanded ? 180f : 0f).setDuration(200).start();
                if (isEarlierExpanded) {
                    hHolder.tvEarlierTitle.setText("Hide previous departures");
                    notifyItemRangeInserted(1, earlierServices.size());
                } else {
                    hHolder.tvEarlierTitle.setText("Show previous departures (" + earlierServices.size() + ")");
                    notifyItemRangeRemoved(1, earlierServices.size());
                }
            });
            return;
        }

        ItemViewHolder itemHolder = (ItemViewHolder) holder;
        StationDepartureResponse.TrainService service = getServiceForPosition(position);

        boolean isEarlier = earlierServices.contains(service);

        int brandColour = TOCBrandHelper.getColourForToc(service.operatorName);
        if (Objects.equals(TOCBrandHelper.getManagerForToc(service.operatorName), "gbr") || Objects.equals(TOCBrandHelper.getManagerForToc(service.operatorName), "national")) {
            itemHolder.ivModeIcon.setImageResource(R.drawable.ic_national_rail);
        } else {
            itemHolder.ivModeIcon.setImageResource(TOCBrandHelper.getIconForToc(service.operatorName));
        }

        GradientDrawable pillBg = new GradientDrawable();
        boolean isLightColour = ColorUtils.calculateLuminance(brandColour) > 0.5;
        pillBg.setColor(brandColour);
        pillBg.setCornerRadius(itemHolder.itemView.getContext().getResources().getDisplayMetrics().density * 8);
        itemHolder.tvOperatorName.setBackground(pillBg);
        itemHolder.tvOperatorName.setTextColor(isLightColour ? Color.BLACK : Color.WHITE);
        itemHolder.tvOperatorName.setText(service.operatorName);

        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(brandColour);
        itemHolder.dotTop.setBackground(dotBg);
        itemHolder.dotBottom.setBackground(dotBg);
        itemHolder.timelineLine.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(brandColour, 102));

        String schTime = service.std != null ? service.std : (service.sta != null ? service.sta : "--:--");
        String estTime = service.etd != null ? service.etd : (service.eta != null ? service.eta : "Unknown");
        String countdownText = calculateCountdown(schTime, estTime);

        String trueOriginName = (service.origin != null && !service.origin.isEmpty()) ? service.origin.get(0).locationName : "Unknown";
        String trueOriginTime = "--:--";
        if (service.previousCallingPoints != null && !service.previousCallingPoints.isEmpty()) {
            trueOriginTime = service.previousCallingPoints.get(0).callingPoint.get(0).st;
        } else {
            trueOriginTime = schTime;
        }

        String rawDest = (service.destination != null && !service.destination.isEmpty()) ? service.destination.get(0).locationName : "Unknown";
        String trueDestName = rawDest.replaceAll(" \\(.*?\\)", "").replace(" Underground Station", "").replace(" Station", "").trim();
        if (trueDestName.contains(" via ")) trueDestName = trueDestName.substring(0, trueDestName.indexOf(" via "));

        String trueDestTime = "--:--";
        int stopsCount = 0;
        String durationStr = "";

        boolean hasFilter = !Objects.equals(userArrivingStation, "Unknown") && userArrivingStation != null && !userArrivingStation.isEmpty();

        if (service.subsequentCallingPoints != null && !service.subsequentCallingPoints.isEmpty()) {
            List<StationDepartureResponse.CallingPoint> pts = service.subsequentCallingPoints.get(0).callingPoint;
            trueDestTime = pts.get(pts.size() - 1).st;

            if (hasFilter && !isArrivals) {
                int destIndex = -1;
                for (int i = 0; i < pts.size(); i++) {
                    if (pts.get(i).locationName.equalsIgnoreCase(userArrivingStation)) { destIndex = i; break; }
                }
                if (destIndex != -1) {
                    stopsCount = destIndex + 1;
                } else {
                    stopsCount = pts.size();
                }
            } else {
                stopsCount = pts.size();
            }
            durationStr = calculateDuration(schTime, trueDestTime);
        } else {
            trueDestTime = schTime;
        }

        if (isArrivals && service.previousCallingPoints != null && !service.previousCallingPoints.isEmpty()) {
            List<StationDepartureResponse.CallingPoint> pts = service.previousCallingPoints.get(0).callingPoint;
            if (hasFilter) {
                int originIndex = -1;
                for (int i = 0; i < pts.size(); i++) {
                    if (pts.get(i).locationName.equalsIgnoreCase(userArrivingStation)) { originIndex = i; break; }
                }
                if (originIndex != -1) {
                    stopsCount = pts.size() - originIndex;
                } else {
                    stopsCount = pts.size();
                }
            } else {
                stopsCount = pts.size();
            }
        }

        itemHolder.tvOriginStation.setText(trueOriginName);
        itemHolder.tvDepTime.setText(trueOriginTime);
        itemHolder.tvDestStation.setText(trueDestName);
        itemHolder.tvArrTime.setText(trueDestTime);

        itemHolder.tvCurrentStationTime.setText(schTime);
        itemHolder.tvPlatform.setText(service.platform != null ? "Plat " + service.platform : "Plat -");

        itemHolder.tvCurrentStationTime.setPaintFlags(itemHolder.tvCurrentStationTime.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        itemHolder.tvCurrentStationTime.setTextColor(itemHolder.defaultSchColour);
        itemHolder.tvLiveStatus.setVisibility(View.VISIBLE);

        if (isEarlier) {
            itemHolder.tvCurrentStationTime.setText(countdownText);
            itemHolder.tvCurrentStationTime.setTextColor(Color.parseColor("#888888"));
            itemHolder.tvLiveStatus.setVisibility(View.GONE);
            itemHolder.itemView.setAlpha(0.5f);
        } else {
            itemHolder.itemView.setAlpha(1.0f);

            if ("Cancelled".equalsIgnoreCase(estTime)) {
                itemHolder.tvCurrentStationTime.setPaintFlags(itemHolder.tvCurrentStationTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                itemHolder.tvLiveStatus.setText("Cancelled");
                itemHolder.tvLiveStatus.setTextColor(Color.parseColor("#DE350B"));

            } else if ("Delayed".equalsIgnoreCase(estTime)) {
                itemHolder.tvCurrentStationTime.setPaintFlags(itemHolder.tvCurrentStationTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                itemHolder.tvLiveStatus.setText("Delayed");
                itemHolder.tvLiveStatus.setTextColor(Color.parseColor("#FF8C00"));

            } else if ("On time".equalsIgnoreCase(estTime)) {
                itemHolder.tvLiveStatus.setText("On time");
                itemHolder.tvLiveStatus.setTextColor(Color.parseColor("#00875A"));

            } else {
                itemHolder.tvCurrentStationTime.setPaintFlags(itemHolder.tvCurrentStationTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                itemHolder.tvLiveStatus.setText("Exp " + estTime);
                itemHolder.tvLiveStatus.setTextColor(Color.parseColor("#FF8C00"));
            }
        }

        String callingText = "";
        if (!isArrivals) {
            callingText = hasFilter ? "Calling at " + userOriginStation + " and " + userArrivingStation : "Calling at " + userOriginStation;
        } else {
            callingText = hasFilter ? "Arriving at " + userOriginStation + " from " + userArrivingStation : "Arriving at " + userOriginStation;
        }

        String detailsText = "";
        if (!durationStr.isEmpty()) detailsText += durationStr + " • ";
        if (stopsCount > 0) {
            detailsText += stopsCount + (stopsCount == 1 ? " stop • " : " stops • ");
        }
        detailsText += callingText;

        itemHolder.tvServiceDetails.setText(detailsText);

        setAnimation(itemHolder.itemView, position);

        String finalTrueDestName = trueDestName;
        itemHolder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ServiceDetailsActivity.class);
            intent.putExtra("SERVICE_ID", service.serviceID);

            String uOrigin;
            String uDest;

            if (isArrivals) {
                uDest = userOriginStation;
                uOrigin = (userArrivingStation != null && !userArrivingStation.isEmpty() && !userArrivingStation.equals("Unknown")) ? userArrivingStation : trueOriginName;
            } else {
                uOrigin = userOriginStation;
                uDest = (userArrivingStation != null && !userArrivingStation.isEmpty() && !userArrivingStation.equals("Unknown")) ? userArrivingStation : finalTrueDestName;
            }

            intent.putExtra("USER_ORIGIN", uOrigin);
            intent.putExtra("USER_DESTINATION", uDest);
            context.startActivity(intent);
        });
    }

    private String calculateCountdown(String scheduledTime, String estimatedTime) {
        if ("Cancelled".equalsIgnoreCase(estimatedTime)) return "Cancelled";
        if ("Delayed".equalsIgnoreCase(estimatedTime)) return "Delayed";

        String timeToUse = scheduledTime;
        if (estimatedTime != null && estimatedTime.contains(":") && !estimatedTime.equalsIgnoreCase("On time")) {
            timeToUse = estimatedTime;
        }

        if (timeToUse == null || !timeToUse.contains(":")) {
            return estimatedTime != null ? estimatedTime : "--";
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.UK);
            sdf.setTimeZone(TimeZone.getTimeZone("Europe/London"));

            Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
            int currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

            Date targetDate = sdf.parse(timeToUse);
            if (targetDate == null) return estimatedTime != null ? estimatedTime : scheduledTime;

            Calendar target = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
            target.setTime(targetDate);
            int targetMins = target.get(Calendar.HOUR_OF_DAY) * 60 + target.get(Calendar.MINUTE);

            if (targetMins < currentMins && (currentMins - targetMins) > 720) {
                targetMins += 1440;
            }

            int diffMins = targetMins - currentMins;

            if (diffMins < -1) {
                return Math.abs(diffMins) + "m ago";
            } else if (diffMins <= 0) {
                return "Due";
            } else if (diffMins < 60) {
                return diffMins + " min";
            } else {
                int h = diffMins / 60;
                int m = diffMins % 60;
                return m > 0 ? h + "h " + m + "m" : h + "h";
            }
        } catch (Exception e) {
            return estimatedTime != null ? estimatedTime : scheduledTime;
        }
    }

    private void setAnimation(View viewToAnimate, int position) {
        if (position > lastPosition) {
            viewToAnimate.setAlpha(0f);
            viewToAnimate.setTranslationY(80f);
            viewToAnimate.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(350)
                    .start();
            lastPosition = position;
        } else {
            viewToAnimate.setAlpha(1f);
            viewToAnimate.setTranslationY(0f);
        }
    }

    private String calculateDuration(String start, String end) {
        if (start == null || end == null || !start.contains(":") || !end.contains(":")) return "";
        try {
            String[] sParts = start.split(":");
            String[] eParts = end.split(":");
            int sMins = Integer.parseInt(sParts[0]) * 60 + Integer.parseInt(sParts[1]);
            int eMins = Integer.parseInt(eParts[0]) * 60 + Integer.parseInt(eParts[1]);

            int diff = eMins - sMins;
            if (diff < 0) diff += 1440;

            int h = diff / 60;
            int m = diff % 60;

            if (h > 0) return h + "h " + m + "m";
            return m + "m";
        } catch (Exception e) {
            return "";
        }
    }

    public int appendData(List<StationDepartureResponse.TrainService> newServices) {
        if (newServices == null || newServices.isEmpty()) return 0;
        int addedCount = 0;
        for (StationDepartureResponse.TrainService newService : newServices) {
            boolean exists = false;
            for (StationDepartureResponse.TrainService existing : upcomingServices) {
                if (Objects.equals(existing.serviceID, newService.serviceID)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                for (StationDepartureResponse.TrainService existing : earlierServices) {
                    if (Objects.equals(existing.serviceID, newService.serviceID)) {
                        exists = true;
                        break;
                    }
                }
            }
            if (!exists) {
                if (isServicePassed(newService)) {
                    earlierServices.add(newService);
                } else {
                    upcomingServices.add(newService);
                }
                addedCount++;
            }
        }
        if (addedCount > 0) {
            notifyDataSetChanged();
        }
        return addedCount;
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        ImageView ivExpandEarlier;
        TextView tvEarlierTitle;

        public HeaderViewHolder(View v) {
            super(v);
            ivExpandEarlier = v.findViewById(R.id.ivExpandEarlier);
            tvEarlierTitle = v.findViewById(R.id.tvEarlierTitle);
        }
    }

    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        ImageView ivModeIcon;
        TextView tvOperatorName, tvCurrentStationTime, tvPlatform;
        View dotTop, timelineLine, dotBottom;
        TextView tvDepTime, tvOriginStation, tvArrTime, tvDestStation;
        TextView tvServiceDetails, tvLiveStatus;
        int defaultSchColour;

        public ItemViewHolder(View v) {
            super(v);
            ivModeIcon = v.findViewById(R.id.ivModeIcon);
            tvOperatorName = v.findViewById(R.id.tvOperatorName);
            tvCurrentStationTime = v.findViewById(R.id.tvCurrentStationTime);
            tvPlatform = v.findViewById(R.id.tvPlatform);
            dotTop = v.findViewById(R.id.dotTop);
            timelineLine = v.findViewById(R.id.timelineLine);
            dotBottom = v.findViewById(R.id.dotBottom);
            tvDepTime = v.findViewById(R.id.tvDepTime);
            tvOriginStation = v.findViewById(R.id.tvOriginStation);
            tvArrTime = v.findViewById(R.id.tvArrTime);
            tvDestStation = v.findViewById(R.id.tvDestStation);
            tvServiceDetails = v.findViewById(R.id.tvServiceDetails);
            tvLiveStatus = v.findViewById(R.id.tvLiveStatus);
            defaultSchColour = tvDepTime.getCurrentTextColor();
        }
    }
}