package io.github.turtle358.callingpoint.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse;
import io.github.turtle358.callingpoint.ui.activities.ServiceDetailsActivity;

public class PinnedServiceAdapter extends RecyclerView.Adapter<PinnedServiceAdapter.ViewHolder> {

    private final Context context;
    private final List<ServiceDetailsResponse> liveServices;
    private int lastPosition = -1;

    public PinnedServiceAdapter(Context context, List<ServiceDetailsResponse> liveServices) {
        this.context = context;
        this.liveServices = liveServices;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_pinned_service, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ServiceDetailsResponse service = liveServices.get(position);

        holder.tocColourBar.setBackgroundColor(TOCBrandHelper.getColourForToc(service.operator));
        holder.ivManagerLogo.setImageResource(TOCBrandHelper.getIconForToc(service.operator));

        String origin = service.locationName;
        if (service.previousCallingPoints != null && !service.previousCallingPoints.isEmpty()
                && service.previousCallingPoints.get(0).callingPoint != null) {
            origin = service.previousCallingPoints.get(0).callingPoint.get(0).locationName;
        }

        String destination;
        if (service.subsequentCallingPoints != null && !service.subsequentCallingPoints.isEmpty()
                && service.subsequentCallingPoints.get(0).callingPoint != null) {
            List<ServiceDetailsResponse.TimelinePoint> subsequent = service.subsequentCallingPoints.get(0).callingPoint;
            destination = subsequent.get(subsequent.size() - 1).locationName;
        } else {
            destination = service.locationName;
        }

        String routeHtml = "<b>" + origin + "</b> to <b>" + destination + "</b>";
        holder.tvRoute.setText(Html.fromHtml(routeHtml, Html.FROM_HTML_MODE_COMPACT));

        List<ServiceDetailsResponse.TimelinePoint> entireJourney = new ArrayList<>();
        if (service.previousCallingPoints != null && !service.previousCallingPoints.isEmpty() && service.previousCallingPoints.get(0).callingPoint != null) {
            entireJourney.addAll(service.previousCallingPoints.get(0).callingPoint);
        }

        ServiceDetailsResponse.TimelinePoint anchorStation = new ServiceDetailsResponse.TimelinePoint();
        anchorStation.locationName = service.locationName;
        anchorStation.scheduledTime = service.std != null ? service.std : "";
        anchorStation.estimatedTime = service.etd;
        anchorStation.actualTime = (service.atd != null) ? service.atd : service.ata;
        entireJourney.add(anchorStation);

        if (service.subsequentCallingPoints != null && !service.subsequentCallingPoints.isEmpty() && service.subsequentCallingPoints.get(0).callingPoint != null) {
            entireJourney.addAll(service.subsequentCallingPoints.get(0).callingPoint);
        }

        ServiceDetailsResponse.TimelinePoint trueNextStop = null;
        int trueNextStopIndex = -1;

        for (int i = 0; i < entireJourney.size(); i++) {
            ServiceDetailsResponse.TimelinePoint point = entireJourney.get(i);
            boolean hasPassed = point.actualTime != null && !point.actualTime.isEmpty();

            if (point.estimatedTime != null && (point.estimatedTime.equalsIgnoreCase("Arrived") || point.estimatedTime.equalsIgnoreCase("Departed"))) {
                hasPassed = true;
            }

            if (!hasPassed) {
                trueNextStop = point;
                trueNextStopIndex = i;
                break;
            }
        }

        if (trueNextStop != null) {
            holder.tvNextStop.setText("Next calling at " + trueNextStop.locationName);
            holder.tvSchTime.setText(trueNextStop.scheduledTime);

            String estTime = (trueNextStop.estimatedTime != null && !trueNextStop.estimatedTime.isEmpty()) ? trueNextStop.estimatedTime : "On time";
            holder.tvLiveStatus.setText(estTime);

            if ("On time".equalsIgnoreCase(estTime)) {
                holder.tvLiveStatus.setTextColor(Color.parseColor("#00875A"));
            } else if ("Cancelled".equalsIgnoreCase(estTime)) {
                holder.tvLiveStatus.setTextColor(Color.parseColor("#DE350B"));
            } else {
                holder.tvLiveStatus.setTextColor(Color.parseColor("#FF8C00"));
            }

            // Calculate stops and duration logic
            int stopsLeft = entireJourney.size() - trueNextStopIndex - 1;
            String durationStr = "";

            if (stopsLeft > 0) {
                ServiceDetailsResponse.TimelinePoint finalDest = entireJourney.get(entireJourney.size() - 1);
                durationStr = calculateDuration(trueNextStop.scheduledTime, finalDest.scheduledTime);
            }

            String detailsText = "";
            if (!durationStr.isEmpty()) detailsText += durationStr + " • ";

            if (stopsLeft > 0) {
                detailsText += stopsLeft + (stopsLeft == 1 ? " stop • " : " stops • ");
            } else {
                detailsText += "Last stop • ";
            }
            detailsText += service.operator;

            holder.tvServiceDetails.setText(detailsText);

        } else {
            holder.tvNextStop.setText("Arrived at " + destination);
            holder.tvSchTime.setText("--:--");
            holder.tvLiveStatus.setText("Terminated");
            holder.tvLiveStatus.setTextColor(Color.parseColor("#666666"));
            holder.tvServiceDetails.setText("Last stop • " + service.operator);
        }

        setAnimation(holder.itemView, position);

        holder.itemView.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(context, ServiceDetailsActivity.class);
            intent.putExtra("SERVICE_ID", service.customServiceID);
            intent.putExtra("USER_ORIGIN", service.locationName);
            intent.putExtra("USER_DESTINATION", destination);
            context.startActivity(intent);
        });
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

    @Override
    public int getItemCount() {
        return liveServices.size();
    }

    public void swapItems(int fromPosition, int toPosition) {
        Collections.swap(liveServices, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View tocColourBar;
        ImageView ivManagerLogo;
        TextView tvRoute, tvNextStop, tvLiveStatus, tvSchTime, tvServiceDetails;

        public ViewHolder(View v) {
            super(v);
            tocColourBar = v.findViewById(R.id.tocColourBar);
            tvRoute = v.findViewById(R.id.tvRoute);
            tvNextStop = v.findViewById(R.id.tvNextStop);
            tvLiveStatus = v.findViewById(R.id.tvLiveStatus);
            tvSchTime = v.findViewById(R.id.tvSchTime);
            tvServiceDetails = v.findViewById(R.id.tvServiceDetails);
            ivManagerLogo = v.findViewById(R.id.ivManagerLogo);
        }
    }
}