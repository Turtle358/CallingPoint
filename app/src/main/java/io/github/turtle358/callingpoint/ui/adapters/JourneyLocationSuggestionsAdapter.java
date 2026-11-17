package io.github.turtle358.callingpoint.ui.adapters;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyLocation;
import io.github.turtle358.callingpoint.utils.StationHelper;

public class JourneyLocationSuggestionsAdapter extends RecyclerView.Adapter<JourneyLocationSuggestionsAdapter.Holder> {
    public interface OnSelect { void select(@NonNull JourneyLocation location); }
    private final List<JourneyLocation> items = new ArrayList<>();
    private final OnSelect listener;
    private String query = "";

    public JourneyLocationSuggestionsAdapter(@NonNull OnSelect listener) { this.listener = listener; }

    public void show(@NonNull List<JourneyLocation> locations, @NonNull String q, boolean recent) {
        items.clear();
        items.addAll(locations);
        query = q;
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_journey_location_suggestion, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        JourneyLocation loc = items.get(position);
        holder.name.setText(highlight(loc.displayName(), query, holder.itemView));
        String detail = loc.subtitle == null ? "" : loc.subtitle;
        holder.detail.setText(detail);
        holder.detail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
        if (loc.type == JourneyLocation.Type.STATION) {
            String stationName = loc.displayName()
                    .replace(" Underground Station", "").replace(" Rail Station", "");
            int brandIcon = StationHelper.getIconForStation(stationName);
            holder.icon.setImageResource(brandIcon == 0 ? R.drawable.ic_train : brandIcon);
            holder.icon.clearColorFilter();
        } else {
            holder.icon.setImageResource(android.R.drawable.ic_menu_mylocation);
            holder.icon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));
        }
        holder.itemView.setOnClickListener(v -> listener.select(loc));
    }

    private CharSequence highlight(String title, String q, View view) {
        if (q == null || q.isEmpty()) return title;
        int start = title.toLowerCase(Locale.UK).indexOf(q.toLowerCase(Locale.UK));
        if (start < 0) return title;
        SpannableString value = new SpannableString(title);
        value.setSpan(new StyleSpan(Typeface.BOLD), start, start + q.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        value.setSpan(new ForegroundColorSpan(ContextCompat.getColor(view.getContext(), R.color.brand_primary)),
                start, start + q.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return value;
    }
    @Override public int getItemCount() { return items.size(); }
    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name, detail;
        Holder(View v) {
            super(v);
            icon = v.findViewById(R.id.ivJourneyLocationIcon);
            name = v.findViewById(R.id.tvJourneyLocationName);
            detail = v.findViewById(R.id.tvJourneyLocationDetail);
        }
    }
}
