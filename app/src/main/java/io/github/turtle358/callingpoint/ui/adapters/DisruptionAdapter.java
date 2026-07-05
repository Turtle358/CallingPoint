package io.github.turtle358.callingpoint.ui.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.graphics.ColorUtils;
import android.graphics.Color;
import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;
import io.github.turtle358.callingpoint.models.DisruptionResponce;
import io.github.turtle358.callingpoint.ui.activities.TOCDetailActivity;

public class DisruptionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_SEPARATOR = 1;

    private List<DisruptionResponce.ServiceIndicator> itemList;
    private final Context context;

    public DisruptionAdapter(Context context, List<DisruptionResponce.ServiceIndicator> itemList) {
        this.context = context;
        this.itemList = itemList;
    }

    @Override
    public int getItemViewType(int position) {
        if ("SEPARATOR".equals(itemList.get(position).tocCode)) {
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
        DisruptionResponce.ServiceIndicator item = itemList.get(position);

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
                Intent intent = new Intent(context, TOCDetailActivity.class);
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
        }
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public void updateData(List<DisruptionResponce.ServiceIndicator> newList) {
        this.itemList = newList;
        notifyDataSetChanged();
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
        public SeparatorViewHolder(View v) {
            super(v);
            tvSeparatorTitle = v.findViewById(R.id.tvSeparatorTitle);
        }
    }
}