package com.example.callingpoint;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class PinnedStationAdapter extends RecyclerView.Adapter<PinnedStationAdapter.ViewHolder> {

    private final Context context;
    private final List<String> pinnedKeys;
    private final OnPinClickListener listener;

    public interface OnPinClickListener {
        void onPinClick(String fromCrs, String toCrs);
    }

    public PinnedStationAdapter(Context context, List<String> pinnedKeys, OnPinClickListener listener) {
        this.context = context;
        this.pinnedKeys = pinnedKeys;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_pinned_station, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String key = pinnedKeys.get(position);

        String fromCrs = key.contains(":") ? key.split(":")[0] : key;
        String toCrs = key.contains(":") ? key.split(":")[1] : "";

        String fromName = fromCrs;
        String toName = toCrs;

        for (String name : StationHelper.getStationNames()) {
            if (fromCrs.equalsIgnoreCase(StationHelper.getCrsFromName(name))) fromName = name;
            if (toCrs.equalsIgnoreCase(StationHelper.getCrsFromName(name))) toName = name;
        }

        holder.tvStationName.setText(fromName);

        if (!toCrs.isEmpty()) {
            holder.tvDestinationName.setText("to " + toName);
            holder.tvDestinationName.setVisibility(View.VISIBLE);
        } else {
            holder.tvDestinationName.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onPinClick(fromCrs, toCrs));
    }

    @Override
    public int getItemCount() {
        return pinnedKeys.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStationName, tvDestinationName;

        public ViewHolder(View v) {
            super(v);
            tvStationName = v.findViewById(R.id.tvStationName);
            tvDestinationName = v.findViewById(R.id.tvDestinationName);
        }
    }
}