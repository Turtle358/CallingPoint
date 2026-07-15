package io.github.turtle358.callingpoint.ui.adapters;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageView;
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
    private String currentConstraint = "";

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
        ImageView ivLogo = convertView.findViewById(R.id.ivStationLogo);
        String stationName = getItem(position);

        if (stationName != null) {
            if (!currentConstraint.isEmpty() && stationName.toLowerCase().contains(currentConstraint)) {
                int startPos = stationName.toLowerCase().indexOf(currentConstraint);
                int endPos = startPos + currentConstraint.length();

                SpannableString spannable = new SpannableString(stationName);
                spannable.setSpan(new StyleSpan(Typeface.BOLD), startPos, endPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                tv.setText(spannable);
            } else {
                tv.setText(stationName);
            }


            ivLogo.setImageResource(StationHelper.getIconForStation(stationName));
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
                    currentConstraint = "";
                    suggestions.addAll(allStations);
                } else {
                    currentConstraint = constraint.toString().toLowerCase().trim();
                    String[] keywords = currentConstraint.split("\\s+");

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

                java.util.Collections.sort(suggestions, new java.util.Comparator<String>() {
                    @Override
                    public int compare(String s1, String s2) {
                        boolean s1Starts = s1.toLowerCase().startsWith(currentConstraint);
                        boolean s2Starts = s2.toLowerCase().startsWith(currentConstraint);

                        if (s1Starts && !s2Starts) return -1;
                        if (!s1Starts && s2Starts) return 1;

                        if (s1Starts && s2Starts) {
                            if (s1.length() != s2.length()) {
                                return Integer.compare(s1.length(), s2.length());
                            }
                        }

                        return s1.compareToIgnoreCase(s2);
                    }
                });

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