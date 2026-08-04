package io.github.turtle358.callingpoint.ui.adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.Toc;

public class TocSettingAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_SEPARATOR = 1;

    private List<Toc> originalList;
    private final List<Toc> displayedList;
    private final Set<String> trackedCodes;
    private final OnTocToggledListener listener;
    private final SharedPreferences prefs;
    private boolean isTflExpanded;

    public interface OnTocToggledListener {
        void onToggled(String tocCode, boolean isTracked);
    }

    public TocSettingAdapter(Context context, List<Toc> tocs, Set<String> trackedCodes, OnTocToggledListener listener) {
        this.prefs = context.getSharedPreferences("TFL_PREFS", Context.MODE_PRIVATE);
        this.isTflExpanded = prefs.getBoolean("TFL_EXPANDED_SETTINGS", true);
        this.originalList = tocs != null ? tocs : new ArrayList<>();
        this.displayedList = new ArrayList<>();
        this.trackedCodes = trackedCodes;
        this.listener = listener;
        filterList(false);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator != null) {
            animator.setAddDuration(300);
            animator.setRemoveDuration(300);
            animator.setMoveDuration(300);
            animator.setChangeDuration(300);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if ("SEPARATOR".equals(displayedList.get(position).code)) {
            return TYPE_SEPARATOR;
        }
        return TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SEPARATOR) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_separator, parent, false);
            return new SeparatorViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_toc_setting, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Toc toc = displayedList.get(position);

        if (holder instanceof ItemViewHolder) {
            ItemViewHolder itemHolder = (ItemViewHolder) holder;
            itemHolder.tvName.setText(toc.name);
            int brandColor;
            try {
                brandColor = Color.parseColor(toc.colour);
            } catch (Exception e) {
                brandColor = Color.parseColor("#333333");
            }
            itemHolder.colorBar.setBackgroundColor(brandColor);

            boolean isTracked = trackedCodes.contains(toc.code);
            itemHolder.toggleSwitch.setOnCheckedChangeListener(null);
            itemHolder.toggleSwitch.setChecked(isTracked);
            updateSwitchTrackColor(itemHolder.toggleSwitch, isTracked, brandColor);

            int finalBrandColor = brandColor;
            itemHolder.toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                listener.onToggled(toc.code, isChecked);
                updateSwitchTrackColor(itemHolder.toggleSwitch, isChecked, finalBrandColor);
            });

            itemHolder.itemView.setOnClickListener(v -> itemHolder.toggleSwitch.toggle());

        } else if (holder instanceof SeparatorViewHolder) {
            SeparatorViewHolder sepHolder = (SeparatorViewHolder) holder;
            sepHolder.tvSeparatorTitle.setText(toc.name);

            sepHolder.ivSeparatorArrow.setRotation(isTflExpanded ? 180f : 0f);

            sepHolder.itemView.setOnClickListener(v -> {
                isTflExpanded = !isTflExpanded;
                prefs.edit().putBoolean("TFL_EXPANDED_SETTINGS", isTflExpanded).apply();

                sepHolder.ivSeparatorArrow.animate().rotation(isTflExpanded ? 180f : 0f).setDuration(300).start();

                filterList(true);
            });
        }
    }

    @Override
    public int getItemCount() {
        return displayedList.size();
    }

    public void updateData(List<Toc> newTocs) {
        this.originalList = newTocs != null ? newTocs : new ArrayList<>();
        filterList(false);
    }

    private void filterList(boolean animate) {
        List<Toc> newList = new ArrayList<>();
        boolean inTflSection = false;

        for (Toc item : originalList) {
            if ("SEPARATOR".equals(item.code)) {
                inTflSection = true;
                newList.add(item);
                continue;
            }
            if (inTflSection && !isTflExpanded) {
                continue;
            }
            newList.add(item);
        }

        if (animate) {
            int oldSize = displayedList.size();
            int newSize = newList.size();

            displayedList.clear();
            displayedList.addAll(newList);

            if (newSize < oldSize) {
                notifyItemRangeRemoved(newSize, oldSize - newSize);
            } else if (newSize > oldSize) {
                notifyItemRangeInserted(oldSize, newSize - oldSize);
            }
        } else {
            displayedList.clear();
            displayedList.addAll(newList);
            notifyDataSetChanged();
        }
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

    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        View colorBar;
        TextView tvName;
        SwitchCompat toggleSwitch;

        public ItemViewHolder(View itemView) {
            super(itemView);
            colorBar = itemView.findViewById(R.id.tocSettingColorBar);
            tvName = itemView.findViewById(R.id.tocSettingName);
            toggleSwitch = itemView.findViewById(R.id.tocSettingSwitch);
        }
    }

    public static class SeparatorViewHolder extends RecyclerView.ViewHolder {
        TextView tvSeparatorTitle;
        ImageView ivSeparatorArrow;

        public SeparatorViewHolder(View v) {
            super(v);
            tvSeparatorTitle = v.findViewById(R.id.tvSeparatorTitle);
            ivSeparatorArrow = v.findViewById(R.id.ivSeparatorArrow);
        }
    }
}