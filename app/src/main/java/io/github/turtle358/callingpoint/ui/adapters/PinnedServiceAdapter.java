package io.github.turtle358.callingpoint.ui.adapters;

import android.content.Context;
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

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse;
import io.github.turtle358.callingpoint.ui.activities.ServiceDetailsActivity;

public class PinnedServiceAdapter extends RecyclerView.Adapter<PinnedServiceAdapter.ViewHolder> {

    private final Context context;
    private final List<ServiceDetailsResponse> liveServices;

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

        holder.tvOperator.setText(service.operator);
        holder.tocColourBar.setBackgroundColor(TOCBrandHelper.getColourForToc(service.operator));
        holder.ivManagerLogo.setImageResource(TOCBrandHelper.getIconForToc(service.operator));

        String origin = service.locationName;
        if (service.previousCallingPoints != null && !service.previousCallingPoints.isEmpty()
                && service.previousCallingPoints.get(0).callingPoint != null) {
            origin = service.previousCallingPoints.get(0).callingPoint.get(0).locationName;
        }

        String destination = service.locationName;
        if (service.subsequentCallingPoints != null && !service.subsequentCallingPoints.isEmpty()
                && service.subsequentCallingPoints.get(0).callingPoint != null) {
            List<ServiceDetailsResponse.TimelinePoint> subsequent = service.subsequentCallingPoints.get(0).callingPoint;
            destination = subsequent.get(subsequent.size() - 1).locationName;
        }

        holder.tvServiceRoute.setText(origin + " to " + destination);

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
        for (ServiceDetailsResponse.TimelinePoint point : entireJourney) {
            boolean hasPassed = point.actualTime != null && !point.actualTime.isEmpty();

            if (point.estimatedTime != null && (point.estimatedTime.equalsIgnoreCase("Arrived") || point.estimatedTime.equalsIgnoreCase("Departed"))) {
                hasPassed = true;
            }

            if (!hasPassed) {
                trueNextStop = point;
                break;
            }
        }

        if (trueNextStop != null) {
            holder.tvNextStop.setText("Next: " + trueNextStop.locationName);
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
        } else {
            holder.tvNextStop.setText("Arrived at " + destination);
            holder.tvSchTime.setText("");
            holder.tvLiveStatus.setText("Terminated");
            holder.tvLiveStatus.setTextColor(Color.parseColor("#666666"));
        }

        holder.itemView.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(context, ServiceDetailsActivity.class);
            intent.putExtra("SERVICE_ID", service.customServiceID);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return liveServices.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View tocColourBar;
        ImageView ivManagerLogo;
        TextView tvServiceRoute, tvOperator, tvNextStop, tvLiveStatus, tvSchTime;

        public ViewHolder(View v) {
            super(v);
            tocColourBar = v.findViewById(R.id.tocColourBar);
            tvServiceRoute = v.findViewById(R.id.tvServiceRoute);
            tvOperator = v.findViewById(R.id.tvOperator);
            tvNextStop = v.findViewById(R.id.tvNextStop);
            tvLiveStatus = v.findViewById(R.id.tvLiveStatus);
            tvSchTime = v.findViewById(R.id.tvSchTime);
            ivManagerLogo = v.findViewById(R.id.ivManagerLogo);
        }
    }
}