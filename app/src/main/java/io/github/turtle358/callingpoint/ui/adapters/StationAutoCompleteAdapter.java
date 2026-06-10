package io.github.turtle358.callingpoint.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.utils.StationHelper;

public class StationAutoCompleteAdapter extends ArrayAdapter<String> {
    private final List<String> allStations;
    private final List<String> filteredStations;
    private final LayoutInflater inflater;

    public StationAutoCompleteAdapter(Context context, List<String> stations) {
        super(context, R.layout.item_station_suggestion, stations);
        this.allStations = new ArrayList<>(stations);
        this.filteredStations = new ArrayList<>(stations);
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return filteredStations.size();
    }

    @Nullable
    @Override
    public String getItem(int position) {
        return filteredStations.get(position);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_station_suggestion, parent, false);
        }

        TextView tv = convertView.findViewById(R.id.tvStationSuggestion);
        String stationName = getItem(position);

        if (stationName != null) {
            tv.setText(stationName);
        }

        return convertView;
    }

    public void updateStations(List<String> newStations) {
        this.allStations.clear();
        this.allStations.addAll(newStations);
        this.filteredStations.clear();
        this.filteredStations.addAll(newStations);
        setNotifyOnChange(false);
        clear();
        addAll(newStations);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<String> suggestions = new ArrayList<>();

                if (constraint == null || constraint.length() == 0) {
                    suggestions.addAll(allStations);
                } else {
                    String filterPattern = constraint.toString().toLowerCase().trim();
                    String[] keywords = filterPattern.split("\\s+");

                    for (String station : allStations) {
                        String stationLower = station.toLowerCase();
                        String crsLower = StationHelper.getCrsFromName(station).toLowerCase();
                        boolean matchesAll = true;

                        for (String keyword : keywords) {
                            if (!stationLower.contains(keyword) && (crsLower == null || !crsLower.contains(keyword))) {
                                matchesAll = false;
                                break;
                            }
                        }
                        if (matchesAll) {
                            suggestions.add(station);
                        }
                    }
                }

                results.values = suggestions;
                results.count = suggestions.size();
                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredStations.clear();
                if (results != null && results.count > 0) {
                    filteredStations.addAll((List<String>) results.values);
                }
                notifyDataSetChanged();
            }
        };
    }
}