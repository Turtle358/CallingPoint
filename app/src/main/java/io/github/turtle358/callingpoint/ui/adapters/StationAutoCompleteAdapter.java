package io.github.turtle358.callingpoint.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
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
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.utils.RecentStationsManager;
import io.github.turtle358.callingpoint.utils.StationHelper;

public class StationAutoCompleteAdapter extends ArrayAdapter<String> {
    private final List<String> allStations;
    private final List<String> filteredStations;
    private final LayoutInflater inflater;
    private final RecentStationsManager recentManager;
    private String currentConstraint = "";

    public StationAutoCompleteAdapter(Context context, List<String> stations) {
        super(context, R.layout.item_station_suggestion, new ArrayList<>());
        this.recentManager = new RecentStationsManager(context);
        this.allStations = new ArrayList<>(stations);
        this.filteredStations = new ArrayList<>(recentManager.getRecentStations());
        this.inflater = LayoutInflater.from(context);
    }

    private String normalize(String input) {
        if (input == null) return "";
        return input.toLowerCase().replaceAll("[^a-z0-9 ]", "");
    }

    private int[] getHighlightBounds(String original, String constraint) {
        String normOrig = original.toLowerCase();
        String normConst = constraint.toLowerCase();

        int exactStart = normOrig.indexOf(normConst);
        if (exactStart != -1) {
            return new int[]{exactStart, exactStart + normConst.length()};
        }

        String strippedConst = normConst.replaceAll("[^a-z0-9]", "");
        if (strippedConst.isEmpty()) return null;

        int matchLen = 0;
        int startIdx = -1;

        for (int i = 0; i < original.length(); i++) {
            char c = Character.toLowerCase(original.charAt(i));
            if (String.valueOf(c).matches("[a-z0-9]")) {
                if (matchLen < strippedConst.length() && c == strippedConst.charAt(matchLen)) {
                    if (matchLen == 0) startIdx = i;
                    matchLen++;
                    if (matchLen == strippedConst.length()) {
                        return new int[]{startIdx, i + 1};
                    }
                } else if (matchLen > 0) {
                    matchLen = 0;
                    startIdx = -1;
                    if (c == strippedConst.charAt(0)) {
                        startIdx = i;
                        matchLen = 1;
                    }
                }
            }
        }
        return null;
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
        View divider = convertView.findViewById(R.id.dividerSuggestion);

        String stationName = getItem(position);

        if (divider != null) {
            divider.setVisibility(position == getCount() - 1 ? View.INVISIBLE : View.VISIBLE);
        }

        if (stationName != null) {
            if (currentConstraint.isEmpty()) {
                tv.setText(stationName);
                ivLogo.setImageResource(android.R.drawable.ic_menu_recent_history);
                ivLogo.setColorFilter(Color.parseColor("#888888"));
            } else {
                ivLogo.setImageResource(StationHelper.getIconForStation(stationName));
                ivLogo.clearColorFilter();

                int[] bounds = getHighlightBounds(stationName, currentConstraint);
                if (bounds != null) {
                    SpannableString spannable = new SpannableString(stationName);
                    int brandColor = ContextCompat.getColor(getContext(), R.color.brand_primary);

                    spannable.setSpan(new StyleSpan(Typeface.BOLD), bounds[0], bounds[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new ForegroundColorSpan(brandColor), bounds[0], bounds[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    tv.setText(spannable);
                } else {
                    tv.setText(stationName);
                }
            }
        }
        return convertView;
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
                    suggestions.addAll(recentManager.getRecentStations());
                } else {
                    currentConstraint = constraint.toString().toLowerCase().trim();
                    String normalizedConstraint = normalize(currentConstraint);
                    String[] keywords = normalizedConstraint.split("\\s+");

                    for (String station : allStations) {
                        String normalizedStation = normalize(station);
                        String crsLower = StationHelper.getCrsFromName(station).toLowerCase();
                        boolean matchesAll = true;

                        for (String keyword : keywords) {
                            if (!normalizedStation.contains(keyword) && (crsLower == null || !crsLower.contains(keyword))) {
                                matchesAll = false;
                                break;
                            }
                        }
                        if (matchesAll) {
                            suggestions.add(station);
                        }
                    }
                }

                java.util.Collections.sort(suggestions, (s1, s2) -> {
                    if (currentConstraint.isEmpty()) return 0;

                    String n1 = normalize(s1);
                    String n2 = normalize(s2);
                    String nc = normalize(currentConstraint);

                    boolean s1Starts = n1.startsWith(nc);
                    boolean s2Starts = n2.startsWith(nc);

                    if (s1Starts && !s2Starts) return -1;
                    if (!s1Starts && s2Starts) return 1;

                    if (s1Starts && s2Starts) {
                        if (s1.length() != s2.length()) {
                            return Integer.compare(s1.length(), s2.length());
                        }
                    }
                    return s1.compareToIgnoreCase(s2);
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