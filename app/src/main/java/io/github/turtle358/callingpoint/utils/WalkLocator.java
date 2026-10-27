package io.github.turtle358.callingpoint.utils;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WalkLocator {

    private static final String TAG = "WalkLocator";
    private static final double LAT_MIN = 49.8, LAT_MAX = 60.9;
    private static final double LON_MIN = -8.7, LON_MAX = 1.8;

    private static final int MAX_CANDIDATES = 8;

    private static final double DEDUPE_METRES = 100;

    private static final double MAX_SANE_WALK_METRES = 8000;

    private static final Map<String, GeoPoint[]> CACHE = new ConcurrentHashMap<>();

    private WalkLocator() {}

    public static GeoPoint[] resolve(Context context, String origin, String dest) {
        if (origin == null || dest == null) return null;

        String key = origin + "|" + dest;
        GeoPoint[] cached = CACHE.get(key);
        if (cached != null) return cached;

        if (!Geocoder.isPresent()) return null;
        Geocoder geocoder = new Geocoder(context);

        List<GeoPoint> originCandidates = candidates(geocoder, origin);
        List<GeoPoint> destCandidates = candidates(geocoder, dest);
        if (originCandidates.isEmpty() || destCandidates.isEmpty()) return null;

        GeoPoint bestA = null, bestB = null;
        double bestDist = Double.MAX_VALUE;
        for (GeoPoint a : originCandidates) {
            for (GeoPoint b : destCandidates) {
                double d = a.distanceToAsDouble(b);
                if (d < bestDist) {
                    bestDist = d;
                    bestA = a;
                    bestB = b;
                }
            }
        }

        if (bestDist > MAX_SANE_WALK_METRES) {
            Log.w(TAG, "Suspicious walk '" + origin + "' -> '" + dest + "': "
                    + Math.round(bestDist) + " m apart");
        }

        GeoPoint[] result = new GeoPoint[]{bestA, bestB};
        CACHE.put(key, result);
        return result;
    }

    private static List<GeoPoint> candidates(Geocoder geocoder, String name) {
        List<GeoPoint> points = new ArrayList<>();
        for (String query : queryVariants(name)) {
            try {
                List<Address> results = geocoder.getFromLocationName(
                        query, MAX_CANDIDATES, LAT_MIN, LON_MIN, LAT_MAX, LON_MAX);
                if (results == null) continue;
                for (Address a : results) {
                    if (!a.hasLatitude() || !a.hasLongitude()) continue;
                    GeoPoint p = new GeoPoint(a.getLatitude(), a.getLongitude());
                    boolean duplicate = false;
                    for (GeoPoint existing : points) {
                        if (existing.distanceToAsDouble(p) < DEDUPE_METRES) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) points.add(p);
                }
            } catch (Exception e) {
                Log.w(TAG, "Geocode failed for '" + query + "'", e);
            }
        }
        Log.d(TAG, "'" + name + "' -> " + points.size() + " candidates");
        return points;
    }

    private static List<String> queryVariants(String name) {
        List<String> variants = new ArrayList<>();
        variants.add(name);

        String bracketsToComma = name.replaceAll("\\s*\\(([^)]+)\\)", ", $1").trim();
        if (!bracketsToComma.equals(name)) variants.add(bracketsToComma);
        String lower = name.toLowerCase();
        if (!name.contains(",") && !name.contains("(")
                && !lower.contains("station") && !lower.contains("stop")) {
            variants.add(name + " railway station");
        }

        int comma = bracketsToComma.indexOf(',');
        if (comma > 0) {
            int second = bracketsToComma.indexOf(',', comma + 1);
            if (second > 0) variants.add(bracketsToComma.substring(0, second).trim());
        }
        return variants;
    }
}