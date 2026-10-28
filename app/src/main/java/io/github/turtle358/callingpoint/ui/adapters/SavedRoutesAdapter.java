package io.github.turtle358.callingpoint.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.SavedRoute;
import io.github.turtle358.callingpoint.utils.StationHelper;

public class SavedRoutesAdapter extends RecyclerView.Adapter<SavedRoutesAdapter.ViewHolder> {

    private final List<SavedRoute> routes;
    private final OnRouteClickListener listener;
    private final OnRouteRemoveListener removeListener;

    public interface OnRouteClickListener {
        void onRouteClick(SavedRoute route);
    }

    public interface OnRouteRemoveListener {
        void onRouteRemove(SavedRoute route, int position);
    }

    public SavedRoutesAdapter(List<SavedRoute> routes, OnRouteClickListener listener, OnRouteRemoveListener removeListener) {
        this.routes = routes;
        this.listener = listener;
        this.removeListener = removeListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_route, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SavedRoute route = routes.get(position);
        holder.textOrigin.setText(shortName(route.getFromName()));
        holder.textDestination.setText(shortName(route.getToName()));
        holder.openButton.setOnClickListener(v -> openRoute(holder));
        String source = route.getFromSource();
        if ("CURRENT".equalsIgnoreCase(source)
                || (route.getFromLocation() != null
                && route.getFromLocation().type == io.github.turtle358.callingpoint.models.JourneyLocation.Type.CURRENT_LOCATION)
                || "Current location".equalsIgnoreCase(route.getFromName())) {
            holder.serviceBranding.setImageResource(R.drawable.ic_saved_current_location);
            holder.serviceBranding.setColorFilter(androidx.core.content.ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.brand_primary));
        } else if ("HOME".equalsIgnoreCase(source)) {
            holder.serviceBranding.setImageResource(R.drawable.ic_home);
            holder.serviceBranding.setColorFilter(androidx.core.content.ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.brand_primary));
        } else if ("WORK".equalsIgnoreCase(source)) {
            holder.serviceBranding.setImageResource(R.drawable.ic_work);
            holder.serviceBranding.setColorFilter(androidx.core.content.ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.brand_primary));
        } else if (route.getFromLocation() != null &&
                   route.getFromLocation().type != io.github.turtle358.callingpoint.models.JourneyLocation.Type.STATION) {
            holder.serviceBranding.setImageResource(android.R.drawable.ic_menu_mapmode);
            holder.serviceBranding.setColorFilter(androidx.core.content.ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.text_secondary));
        } else {
            holder.serviceBranding.setImageResource(StationHelper.getIconForStation(route.getFromName()));
            holder.serviceBranding.clearColorFilter();
        }

        holder.itemView.setOnClickListener(v -> openRoute(holder));
    }

    private void openRoute(@NonNull ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION && listener != null) {
            listener.onRouteClick(routes.get(position));
        }
    }

    private static String shortName(String name) {
        if (name == null) return "";
        String[] parts = name.trim().split(",");
        return parts.length == 0 ? name.trim() : parts[0].trim();
    }

    public List<SavedRoute> getRoutes() {
        return routes;
    }

    @Override
    public int getItemCount() {
        return routes.size();
    }

    public void swapItems(int fromPosition, int toPosition) {
        java.util.Collections.swap(routes, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textOrigin;
        TextView textDestination;
        ImageView serviceBranding;
        ImageButton openButton;


        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textOrigin = itemView.findViewById(R.id.tv_origin);
            textDestination = itemView.findViewById(R.id.tv_destination);
            serviceBranding = itemView.findViewById(R.id.serviceBranding);
            openButton = itemView.findViewById(R.id.btnOpenSavedRoute);
        }
    }
}