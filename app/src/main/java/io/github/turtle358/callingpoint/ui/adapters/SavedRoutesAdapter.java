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
        String destinationSource = route.getToSource();
        String destinationName = route.getToName();
        io.github.turtle358.callingpoint.models.JourneyLocation destinationLocation = route.getToLocation();
        String destinationLabel = destinationName == null ? "" : destinationName.trim();
        boolean isCurrent = "CURRENT".equalsIgnoreCase(destinationSource)
                || (destinationLocation != null && destinationLocation.type ==
                io.github.turtle358.callingpoint.models.JourneyLocation.Type.CURRENT_LOCATION)
                || "Current location".equalsIgnoreCase(destinationLabel);
        boolean isHome = "HOME".equalsIgnoreCase(destinationSource)
                || "Home".equalsIgnoreCase(destinationLabel);
        boolean isWork = "WORK".equalsIgnoreCase(destinationSource)
                || "Work".equalsIgnoreCase(destinationLabel);
        boolean isNonStation = destinationLocation != null
                && destinationLocation.type != io.github.turtle358.callingpoint.models.JourneyLocation.Type.STATION;

        int drawable;
        int tintColor = 0;
        if (isCurrent) {
            drawable = R.drawable.ic_saved_current_location;
            tintColor = R.color.brand_primary;
        } else if (isHome) {
            drawable = R.drawable.ic_home;
            tintColor = R.color.brand_primary;
        } else if (isWork) {
            drawable = R.drawable.ic_work;
            tintColor = R.color.brand_primary;
        } else if (isNonStation || "ADDRESS".equalsIgnoreCase(destinationSource)
                || "POSTCODE".equalsIgnoreCase(destinationSource)) {
            drawable = R.drawable.ic_location_pin;
            tintColor = R.color.brand_primary;
        } else {
            drawable = StationHelper.getIconForStation(destinationLabel);
            if (drawable == 0) drawable = R.drawable.ic_train;
        }
        holder.serviceBranding.setImageResource(drawable);
        if (tintColor != 0) {
            holder.serviceBranding.setColorFilter(androidx.core.content.ContextCompat.getColor(
                    holder.itemView.getContext(), tintColor));
        } else {
            holder.serviceBranding.clearColorFilter();
        }
        holder.serviceBranding.setContentDescription("Destination: " + destinationLabel);

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