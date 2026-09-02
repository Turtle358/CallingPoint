package io.github.turtle358.callingpoint.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.SavedRoute;

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
        holder.tvRouteTitle.setText(route.getFromName() + " → " + route.getToName());

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

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRouteTitle;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRouteTitle = itemView.findViewById(R.id.tvRouteTitle);
        }
    }
}