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

    public interface OnRouteClickListener {
        void onRouteClick(SavedRoute route);
    }

    public interface OnRouteRemoveListener {
        void onRouteRemove(SavedRoute route, int position);
    }

    public SavedRoutesAdapter(List<SavedRoute> routes, OnRouteClickListener listener, OnRouteRemoveListener removeListener) {
        this.routes = routes;
        this.listener = listener;
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
        holder.textOrigin.setText(route.getFromName());
        holder.textDestination.setText(route.getToName());
        holder.serviceBranding.setImageResource(StationHelper.getIconForStation(route.getFromName()));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onRouteClick(route);
        });
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


        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textOrigin = itemView.findViewById(R.id.tv_origin);
            textDestination = itemView.findViewById(R.id.tv_destination);
            serviceBranding = itemView.findViewById(R.id.serviceBranding);
        }
    }
}