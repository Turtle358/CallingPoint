package io.github.turtle358.callingpoint.utils.JourneyPlanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.CancellationTokenSource;

public class JourneyLocationCache {
    private static final long MAX_AGE_MS = 90_000L;
    private static final long RETRY_AFTER_MS = 12_000L;

    private static Location latest;
    private static boolean loading;
    private static long lastAttemptMs;

    private JourneyLocationCache() { }

    public static boolean hasPermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Nullable
    public static synchronized Location getFresh(@NonNull Context context) {
        return hasPermission(context) && usable(context, latest) ? new Location(latest) : null;
    }

    public static synchronized void remember(@NonNull Context context, @NonNull Location fix) {
        if (hasPermission(context) && usable(context, fix)) latest = new Location(fix);
    }

    @SuppressLint("MissingPermission")
    public static void warmIfPermitted(@NonNull Context context) {
        Context app = context.getApplicationContext();
        if (!hasPermission(app)) return;
        synchronized (JourneyLocationCache.class) {
            if (getFresh(app) != null || loading) return;
            long now = SystemClock.elapsedRealtime();
            if (lastAttemptMs != 0 && now - lastAttemptMs < RETRY_AFTER_MS) return;
            lastAttemptMs = now;
            loading = true;
        }
        try {
            LocationServices.getFusedLocationProviderClient(app).getLastLocation()
                    .addOnSuccessListener(fix -> {
                        if (usable(app, fix)) {
                            remember(app, fix);
                            finish();
                        } else {
                            fetchFresh(app);
                        }
                    })
                    .addOnFailureListener(error -> fetchFresh(app));
        } catch (RuntimeException error) {
            finish();
        }
    }

    @SuppressLint("MissingPermission")
    private static void fetchFresh(@NonNull Context app) {
        if (!hasPermission(app)) { finish(); return; }
        try {
            LocationServices.getFusedLocationProviderClient(app).getCurrentLocation(
                            LocationRequest.PRIORITY_HIGH_ACCURACY,
                            new CancellationTokenSource().getToken())
                    .addOnSuccessListener(fix -> {
                        if (fix != null) remember(app, fix);
                        finish();
                    })
                    .addOnFailureListener(error -> finish());
        } catch (RuntimeException error) {
            finish();
        }
    }

    private static synchronized void finish() { loading = false; }

    private static boolean usable(@NonNull Context context, @Nullable Location fix) {
        if (fix == null || !fix.hasAccuracy()) return false;
        long age = (SystemClock.elapsedRealtimeNanos() - fix.getElapsedRealtimeNanos()) / 1_000_000L;
        boolean fine = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return age >= 0 && age <= MAX_AGE_MS && fix.getAccuracy() <= (fine ? 125f : 1200f);
    }
}
