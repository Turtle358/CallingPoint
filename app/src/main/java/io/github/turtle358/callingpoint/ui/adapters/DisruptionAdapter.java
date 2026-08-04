package io.github.turtle358.callingpoint.ui.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.graphics.ColorUtils;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;
import io.github.turtle358.callingpoint.models.DisruptionResponce;
import io.github.turtle358.callingpoint.ui.activities.TOCDisruptionDetailActivity;

public class DisruptionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_SEPARATOR = 1;

    private List<DisruptionResponce.ServiceIndicator> originalList;
    private final List<DisruptionResponce.ServiceIndicator> displayedList;
    private final Context context;
    private final SharedPreferences prefs;
    private boolean isTflExpanded;

    public DisruptionAdapter(Context context, List<DisruptionResponce.ServiceIndicator> itemList) {
        this.context = context;
        this.prefs = context.getSharedPreferences("TFL_PREFS", Context.MODE_PRIVATE);
        this.isTflExpanded = prefs.getBoolean("TFL_EXPANDED_DISRUPTIONS", true);
        this.originalList = itemList != null ? itemList : new ArrayList<>();
        this.displayedList = new ArrayList<>();
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
        if ("SEPARATOR".equals(displayedList.get(position).tocCode)) {
            return TYPE_SEPARATOR;
        }
        return TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SEPARATOR) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_separator, parent, false);
            return new SeparatorViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_disruption, parent, false);
            return new DisruptionViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DisruptionResponce.ServiceIndicator item = displayedList.get(position);

        if (holder instanceof DisruptionViewHolder) {
            DisruptionViewHolder dHolder = (DisruptionViewHolder) holder;
            dHolder.tvTocName.setText(item.tocName);
            String acronym = TOCBrandHelper.getAcronymForCode(item.tocCode);
            dHolder.tvTocCode.setText(acronym != null ? acronym : item.tocCode);
            dHolder.tvStatus.setText(item.status);

            int brandColour = TOCBrandHelper.getColourForToc(item.tocName);
            dHolder.tvTocCode.setBackgroundTintList(ColorStateList.valueOf(brandColour));

            boolean isLightColour = ColorUtils.calculateLuminance(brandColour) > 0.5;
            dHolder.tvTocCode.setTextColor(isLightColour ? Color.BLACK : Color.WHITE);

            dHolder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, TOCDisruptionDetailActivity.class);
                intent.putExtra("TOC_NAME", item.tocName);
                intent.putExtra("TOC_CODE", item.tocCode);
                intent.putExtra("TOC_STATUS", item.status);

                com.google.gson.Gson gson = new com.google.gson.Gson();
                intent.putExtra("TOC_ACTIVE_JSON", gson.toJson(item.activeIncidents));
                intent.putExtra("TOC_PLANNED_JSON", gson.toJson(item.plannedIncidents));

                context.startActivity(intent);
            });
        } else if (holder instanceof SeparatorViewHolder) {
            SeparatorViewHolder sHolder = (SeparatorViewHolder) holder;
            sHolder.tvSeparatorTitle.setText(item.tocName);

            sHolder.ivSeparatorArrow.setRotation(isTflExpanded ? 180f : 0f);

            sHolder.itemView.setOnClickListener(v -> {
                isTflExpanded = !isTflExpanded;
                prefs.edit().putBoolean("TFL_EXPANDED_DISRUPTIONS", isTflExpanded).apply();

                sHolder.ivSeparatorArrow.animate().rotation(isTflExpanded ? 180f : 0f).setDuration(300).start();

                filterList(true);
            });
        }
    }

    @Override
    public int getItemCount() {
        return displayedList.size();
    }

    public void updateData(List<DisruptionResponce.ServiceIndicator> newList) {
        this.originalList = newList != null ? newList : new ArrayList<>();
        filterList(false);
    }

    private void filterList(boolean animate) {
        List<DisruptionResponce.ServiceIndicator> newList = new ArrayList<>();
        boolean inTflSection = false;

        for (DisruptionResponce.ServiceIndicator item : originalList) {
            if ("SEPARATOR".equals(item.tocCode)) {
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

    public static class DisruptionViewHolder extends RecyclerView.ViewHolder {
        TextView tvTocName, tvTocCode, tvStatus;
        public DisruptionViewHolder(View v) {
            super(v);
            tvTocName = v.findViewById(R.id.tvTocName);
            tvTocCode = v.findViewById(R.id.tvTocCode);
            tvStatus = v.findViewById(R.id.tvStatus);
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