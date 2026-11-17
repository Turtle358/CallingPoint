package io.github.turtle358.callingpoint.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import io.github.turtle358.callingpoint.models.JourneyLocation;

public class JourneySavedPlaces {
    public static final String HOME = "home";
    public static final String WORK = "work";
    private final Gson gson = new Gson();
    private final SharedPreferences prefs;

    public JourneySavedPlaces(@NonNull Context context) {
        prefs = context.getSharedPreferences("journey_locations_v1", Context.MODE_PRIVATE);
    }

    @Nullable
    public JourneyLocation get(@NonNull String slot) {
        String raw = prefs.getString(slot, null);
        if (raw == null) return null;
        try { return gson.fromJson(raw, JourneyLocation.class); }
        catch (RuntimeException ignored) { return null; }
    }

    public void save(@NonNull String slot, @NonNull JourneyLocation location) {
        if (!HOME.equals(slot) && !WORK.equals(slot)) throw new IllegalArgumentException("Unknown place slot");
        if (location.type == JourneyLocation.Type.CURRENT_LOCATION)
            throw new IllegalArgumentException("Use an address/station for Home or Work");
        prefs.edit().putString(slot, gson.toJson(location)).apply();
    }

    public void clear(@NonNull String slot) {
        prefs.edit().remove(slot).apply();
    }

    @NonNull
    public List<JourneyLocation> recents() {
        String raw = prefs.getString("recents", "[]");
        Type type = new TypeToken<List<JourneyLocation>>(){}.getType();
        try {
            List<JourneyLocation> values = gson.fromJson(raw, type);
            return values == null ? new ArrayList<>() : new ArrayList<>(values);
        } catch (RuntimeException ignored) { return new ArrayList<>(); }
    }

    public void remember(@NonNull JourneyLocation loc) {
        if (loc.type == JourneyLocation.Type.CURRENT_LOCATION) return;
        List<JourneyLocation> list = recents();
        for (int i = list.size() - 1; i >= 0; i--) {
            JourneyLocation old = list.get(i);
            if (old != null && old.stableKey().equals(loc.stableKey())) list.remove(i);
        }
        list.add(0, loc);
        if (list.size() > 8) list = new ArrayList<>(list.subList(0, 8));
        prefs.edit().putString("recents", gson.toJson(list)).apply();
    }
}
