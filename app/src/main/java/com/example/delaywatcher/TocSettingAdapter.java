package com.example.delaywatcher;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Set;

public class TocSettingAdapter extends RecyclerView.Adapter<TocSettingAdapter.ViewHolder> {

    private final List<Toc> tocs;
    private final Set<String> trackedCodes;
    private final OnTocToggledListener listener;

    public interface OnTocToggledListener {
        void onToggled(String tocCode, boolean isTracked);
    }

    public TocSettingAdapter(List<Toc> tocs, Set<String> trackedCodes, OnTocToggledListener listener) {
        this.tocs = tocs;
        this.trackedCodes = trackedCodes;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_toc_setting, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Toc toc = tocs.get(position);

        holder.tvName.setText(toc.name);
        try {
            holder.colorBar.setBackgroundColor(Color.parseColor(toc.colour));
        } catch (Exception e) {
            holder.colorBar.setBackgroundColor(Color.parseColor("#333333"));
        }

        // Remove listener temporarily so we don't trigger saves while scrolling
        holder.toggleSwitch.setOnCheckedChangeListener(null);
        holder.toggleSwitch.setChecked(trackedCodes.contains(toc.code));

        holder.toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            listener.onToggled(toc.code, isChecked);
        });

        // Allow tapping anywhere on the row to flip the switch
        holder.itemView.setOnClickListener(v -> holder.toggleSwitch.toggle());
    }

    @Override
    public int getItemCount() {
        return tocs.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View colorBar;
        TextView tvName;
        SwitchCompat toggleSwitch;

        public ViewHolder(View itemView) {
            super(itemView);
            colorBar = itemView.findViewById(R.id.tocSettingColorBar);
            tvName = itemView.findViewById(R.id.tocSettingName);
            toggleSwitch = itemView.findViewById(R.id.tocSettingSwitch);
        }
    }
}