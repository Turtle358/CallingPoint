package io.github.turtle358.callingpoint.ui.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Set;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.Toc;

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
        int brandColor;
        try {
            brandColor = Color.parseColor(toc.colour);
        } catch (Exception e) {
            brandColor = Color.parseColor("#333333");
        }
        holder.colorBar.setBackgroundColor(brandColor);
        boolean isTracked = trackedCodes.contains(toc.code);
        holder.toggleSwitch.setOnCheckedChangeListener(null);
        holder.toggleSwitch.setChecked(isTracked);
        updateSwitchTrackColor(holder.toggleSwitch, isTracked, brandColor);

        int finalBrandColor = brandColor;
        holder.toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            listener.onToggled(toc.code, isChecked);
            updateSwitchTrackColor(holder.toggleSwitch, isChecked, finalBrandColor);
        });

        holder.itemView.setOnClickListener(v -> holder.toggleSwitch.toggle());
    }

    @Override
    public int getItemCount() {
        return tocs.size();
    }

    private void updateSwitchTrackColor(SwitchCompat switchCompat, boolean isChecked, int brandColor) {
        if (isChecked) {
            int trackColor = ColorUtils.setAlphaComponent(brandColor, 150);
            switchCompat.setTrackTintList(ColorStateList.valueOf(trackColor));
        } else {
            int defaultColor = ContextCompat.getColor(switchCompat.getContext(), R.color.divider_color);
            switchCompat.setTrackTintList(ColorStateList.valueOf(defaultColor));
        }
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