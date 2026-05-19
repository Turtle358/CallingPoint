package com.example.delaywatcher;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class DeparturesAdapter extends RecyclerView.Adapter<DeparturesAdapter.ViewHolder> {
    private final Context context;
    private List<StationDepartureResponse.TrainService> services;

    public DeparturesAdapter(Context context, List<StationDepartureResponse.TrainService> services) {
        this.context = context;
        this.services = services;
    }

    public void updateData(List<StationDepartureResponse.TrainService> newServices) {
        this.services = newServices;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_departure, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StationDepartureResponse.TrainService service = services.get(position);

        holder.tvSchTime.setText(service.std);
        holder.tvOperator.setText(service.operatorName);
        holder.tvPlatform.setText(service.platform != null ? "Plat " + service.platform : "---");

        if (service.destination != null && !service.destination.isEmpty()) {
            holder.tvDestination.setText(service.destination.get(0).locationName);
        } else {
            holder.tvDestination.setText("Unknown");
        }

        holder.tvLiveStatus.setText(service.etd);
        if ("On time".equalsIgnoreCase(service.etd)) {
            holder.tvLiveStatus.setTextColor(Color.parseColor("#00875A"));
        } else if ("Cancelled".equalsIgnoreCase(service.etd)) {
            holder.tvLiveStatus.setTextColor(Color.parseColor("#DE350B"));
        } else {
            holder.tvLiveStatus.setTextColor(Color.parseColor("#FF8C00"));
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ServiceDetailsActivity.class);
            intent.putExtra("SERVICE_ID", service.serviceID);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return services != null ? services.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSchTime, tvDestination, tvOperator, tvLiveStatus, tvPlatform;
        public ViewHolder(View v) {
            super(v);
            tvSchTime = v.findViewById(R.id.tvSchTime);
            tvDestination = v.findViewById(R.id.tvDestination);
            tvOperator = v.findViewById(R.id.tvOperator);
            tvLiveStatus = v.findViewById(R.id.tvLiveStatus);
            tvPlatform = v.findViewById(R.id.tvPlatform);
        }
    }
}