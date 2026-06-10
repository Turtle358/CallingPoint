package io.github.turtle358.callingpoint.ui.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Objects;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.utils.StationHelper;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;
import io.github.turtle358.callingpoint.models.StationDepartureResponse;
import io.github.turtle358.callingpoint.ui.activities.ServiceDetailsActivity;

public class DeparturesAdapter extends RecyclerView.Adapter<DeparturesAdapter.ViewHolder> {
    private final Context context;
    private List<StationDepartureResponse.TrainService> services;
    private boolean isArrivals = false;

    private String userOriginStation = "";
    private String userArrivingStation = "";
    private int lastPosition = -1;

    public DeparturesAdapter(Context context, List<StationDepartureResponse.TrainService> services) {
        this.context = context;
        this.services = services;
    }

    public void updateData(List<StationDepartureResponse.TrainService> newServices, boolean isArrivals, String departingCrs, String arrivingCrs) {
        this.services = newServices;
        this.isArrivals = isArrivals;
        this.userOriginStation = StationHelper.getNameFromCrs(departingCrs);
        this.userArrivingStation = StationHelper.getNameFromCrs(arrivingCrs);
        this.lastPosition = -1;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_departure, parent, false);
        return new ViewHolder(v);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StationDepartureResponse.TrainService service = services.get(position);

        holder.tocColourBar.setBackgroundColor(TOCBrandHelper.getColourForToc(service.operatorName));
        holder.ivManagerLogo.setImageResource(TOCBrandHelper.getIconForToc(service.operatorName));

        String schTime = service.std != null ? service.std : (service.sta != null ? service.sta : "--:--");
        String estTime = service.etd != null ? service.etd : (service.eta != null ? service.eta : "Unknown");

        holder.tvSchTime.setText(schTime);
        holder.tvLiveStatus.setText(estTime);

        if ("On time".equalsIgnoreCase(estTime)) {
            holder.tvLiveStatus.setTextColor(Color.parseColor("#00875A"));
        } else if ("Cancelled".equalsIgnoreCase(estTime)) {
            holder.tvLiveStatus.setTextColor(Color.parseColor("#DE350B"));
        } else {
            holder.tvLiveStatus.setTextColor(Color.parseColor("#FF8C00"));
        }

        holder.tvPlatform.setText(service.platform != null ? "Platform " + service.platform : "Platform -");

        String originName = (service.origin != null && !service.origin.isEmpty()) ? service.origin.get(0).locationName : "Unknown";
        String destName = (service.destination != null && !service.destination.isEmpty()) ? service.destination.get(0).locationName : "Unknown";

        String routeHtml = "<b>" + originName + "</b> to <b>" + destName + "</b>";
        holder.tvRoute.setText(android.text.Html.fromHtml(routeHtml, android.text.Html.FROM_HTML_MODE_COMPACT));

        String durationStr = "";
        int stopsCount = 0;

        if (!isArrivals && service.subsequentCallingPoints != null && !service.subsequentCallingPoints.isEmpty()) {
            List<StationDepartureResponse.CallingPoint> pts = service.subsequentCallingPoints.get(0).callingPoint;
            stopsCount = pts.size();
            if (!Objects.equals(userArrivingStation, "Unknown")) {
                holder.tvCallingAt.setText("Calling at " + userOriginStation + " and " + userArrivingStation);
            } else {
                holder.tvCallingAt.setText("Calling at " + userOriginStation);
            }

            holder.tvCallingAt.setVisibility(View.VISIBLE);

            durationStr = calculateDuration(schTime, pts.get(pts.size() - 1).st);

        } else if (isArrivals && service.previousCallingPoints != null && !service.previousCallingPoints.isEmpty()) {
            List<StationDepartureResponse.CallingPoint> pts = service.previousCallingPoints.get(0).callingPoint;
            stopsCount = pts.size();
            if(userArrivingStation != "Unknown") {
                holder.tvCallingAt.setText("Arriving at " + userOriginStation + " from " + userArrivingStation);
            } else {
                holder.tvCallingAt.setText("Arriving at " + userOriginStation);
            }

            holder.tvCallingAt.setVisibility(View.VISIBLE);

            durationStr = calculateDuration(pts.get(0).st, schTime);

        } else {
            holder.tvCallingAt.setVisibility(View.GONE);
        }

        String detailsText = "";
        if (!durationStr.isEmpty()) detailsText += durationStr + " • ";
        detailsText += stopsCount + (stopsCount == 1 ? " stop • " : " stops • ");
        detailsText += service.operatorName;

        holder.tvServiceDetails.setText(detailsText);

        setAnimation(holder.itemView, position);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ServiceDetailsActivity.class);
            intent.putExtra("SERVICE_ID", service.serviceID);
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
    public int getItemCount() { return services != null ? services.size() : 0; }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View tocColourBar;
        ImageView ivManagerLogo;
        TextView tvSchTime, tvLiveStatus, tvPlatform, tvRoute, tvCallingAt, tvServiceDetails;

        public ViewHolder(View v) {
            super(v);
            tocColourBar = v.findViewById(R.id.tocColourBar);
            ivManagerLogo = v.findViewById(R.id.ivManagerLogo);
            tvSchTime = v.findViewById(R.id.tvSchTime);
            tvLiveStatus = v.findViewById(R.id.tvLiveStatus);
            tvPlatform = v.findViewById(R.id.tvPlatform);
            tvRoute = v.findViewById(R.id.tvRoute);
            tvCallingAt = v.findViewById(R.id.tvCallingAt);
            tvServiceDetails = v.findViewById(R.id.tvServiceDetails);
        }
    }
}