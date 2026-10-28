package io.github.turtle358.callingpoint.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;

public class JourneyLocation {
    public enum Type { STATION, ADDRESS, POSTCODE, CURRENT_LOCATION }

    public Type type;
    public String name;
    public String subtitle;
    public String stationId;
    public Double latitude;
    public Double longitude;

    public JourneyLocation() { }

    @NonNull
    public static JourneyLocation station(@NonNull String name, @Nullable String tflStopId) {
        JourneyLocation loc = new JourneyLocation();
        loc.type = Type.STATION;
        loc.name = name;
        loc.subtitle = "Station";
        loc.stationId = tflStopId;
        return loc;
    }

    @NonNull
    public static JourneyLocation address(@NonNull String name, @Nullable String detail,
                                          double lat, double lon) {
        JourneyLocation loc = new JourneyLocation();
        loc.type = Type.ADDRESS;
        loc.name = name;
        loc.subtitle = detail;
        loc.latitude = lat;
        loc.longitude = lon;
        return loc;
    }

    @NonNull
    public static JourneyLocation postcode(@NonNull String postcode) {
        JourneyLocation loc = new JourneyLocation();
        loc.type = Type.POSTCODE;
        loc.name = postcode;
        loc.subtitle = "UK postcode";
        return loc;
    }

    @NonNull
    public static JourneyLocation currentLocation(double lat, double lon) {
        JourneyLocation loc = address("Current location", "Your device's location", lat, lon);
        loc.type = Type.CURRENT_LOCATION;
        return loc;
    }

    @NonNull
    public String tflValue() {
        if (latitude != null && longitude != null) {
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                throw new IllegalArgumentException("Invalid location coordinates");
            }
            return String.format(Locale.US, "%.6f,%.6f", latitude, longitude);
        }
        if (stationId != null && !stationId.trim().isEmpty()) return stationId.trim();
        if (name != null && !name.trim().isEmpty()) return name.trim();
        throw new IllegalArgumentException("No journey location selected");
    }

    @NonNull
    public String displayName() { return name == null ? "Unknown place" : name; }

    @NonNull
    public String stableKey() {
        if (type == Type.CURRENT_LOCATION) return "current";
        if (stationId != null && !stationId.isEmpty()) return "id:" + stationId;
        if (latitude != null && longitude != null) {
            return String.format(Locale.US, "geo:%.5f:%.5f", latitude, longitude);
        }
        return "name:" + displayName().toLowerCase(Locale.UK);
    }
}
