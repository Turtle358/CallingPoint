package io.github.turtle358.callingpoint.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.utils.StationHelper;

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

        // Extract the raw codes (these could be CRS or Naptan IDs)
        String fromCode = key.contains(":") ? key.split(":")[0] : key;
        String toCode = key.contains(":") ? key.split(":")[1] : "";

        // Resolve names using your helper, which handles both CRS and Naptan seamlessly
        String fromName = StationHelper.getNameFromCode(fromCode);
        if (fromName.isEmpty()) fromName = fromCode; // Fallback just in case

        String toName = "";
        if (!toCode.isEmpty()) {
            toName = StationHelper.getNameFromCode(toCode);
            if (toName.isEmpty()) toName = toCode;
        }

        holder.tvStationName.setText(fromName);

        // Fetch the correct icon (National Rail vs TfL roundel)
        holder.ivLogo.setImageResource(StationHelper.getIconForStation(fromName));

        if (!toCode.isEmpty()) {
            holder.tvDestinationName.setText("to " + toName);
            holder.tvDestinationName.setVisibility(View.VISIBLE);
        } else {
            holder.tvDestinationName.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onPinClick(fromCode, toCode));
    }

    @Override
    public int getItemCount() {
        return pinnedKeys.size();
    }

    public void swapItems(int fromPosition, int toPosition) {
        Collections.swap(pinnedKeys, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStationName, tvDestinationName;
        ImageView ivLogo;

        public ViewHolder(View v) {
            super(v);
            tvStationName = v.findViewById(R.id.tvStationName);
            tvDestinationName = v.findViewById(R.id.tvDestinationName);
            ivLogo = v.findViewById(R.id.stationBranding);
        }
    }
}