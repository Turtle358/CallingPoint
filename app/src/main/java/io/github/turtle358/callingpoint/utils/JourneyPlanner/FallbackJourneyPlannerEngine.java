package io.github.turtle358.callingpoint.utils.JourneyPlanner;

import android.os.Handler;
import android.os.Looper;
import io.github.turtle358.callingpoint.models.JourneyLocation;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.models.JourneyLeg;
import io.github.turtle358.callingpoint.utils.StationHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FallbackJourneyPlannerEngine {
    private static final int TFL = 0;
    private static final int TRANSITOUS = 1;
    private static final int RAIL = 2;
    private final TflJourneyPlannerEngine tfl;
    private final TransitousJourneyPlannerEngine transitous;
    private final JourneyPlannerEngine rail;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Search current;
    private boolean closed;

    public FallbackJourneyPlannerEngine(TflJourneyPlannerEngine tfl,
                                       TransitousJourneyPlannerEngine transitous,
                                       JourneyPlannerEngine rail) {
        this.tfl = tfl;
        this.transitous = transitous;
        this.rail = rail;
    }

    public void findRoutes(JourneyLocation from, JourneyLocation to, String date, String time,
                           boolean arriveBy, String preference, String modes, boolean wheelchair,
                           JourneyPlannerEngine.JourneyCallback callback) {
        cancel();
        if (closed) return;
        Search search = new Search(copy(from), copy(to), date, time,
                arriveBy, preference, modes, wheelchair, callback);
        current = search;
        deadline(search, TFL, 15000);
        try {
            tfl.findRoutes(search.from.tflValue(), search.to.tflValue(), date, time,
                    arriveBy ? "arriving" : "departing", preference, modes,
                    wheelchair ? "noSolidStairs" : null, true, callback(search, TFL));
        } catch (RuntimeException error) {
            failed(search, TFL, "TfL could not start the journey search.");
        }
        if (!active(search)) return;
        deadline(search, TRANSITOUS, 90000);
        try {
            transitous.findRoutes(search.from, search.to, date, time, arriveBy,
                    preference, modes, wheelchair, callback(search, TRANSITOUS));
        } catch (RuntimeException error) {
            failed(search, TRANSITOUS, "Transitous could not start the journey search.");
        }
    }

    private void startRail(Search search) {
        if (!active(search) || search.railStarted) return;
        search.railStarted = true;
        String from = StationHelper.getCrsFromName(search.from.displayName());
        String to = StationHelper.getCrsFromName(search.to.displayName());
        if (search.from.type != JourneyLocation.Type.STATION || search.to.type != JourneyLocation.Type.STATION
                || from == null || from.isEmpty() || to == null || to.isEmpty()) {
            finishError(search, "The National Rail fallback needs two rail stations. Try selecting a nearby station.");
            return;
        }
        if (search.date != null || search.time != null || search.arriveBy) {
            finishError(search, "The National Rail fallback supports depart-now searches. Try leaving now.");
            return;
        }
        if (search.wheelchair) {
            finishError(search, "The National Rail fallback cannot check the selected accessibility requirement.");
            return;
        }
        if (search.modes != null && !Arrays.asList(search.modes.split(",")).contains("national-rail")) {
            finishError(search, "The National Rail fallback needs Train enabled in Journey options.");
            return;
        }
        deadline(search, RAIL, 25000);
        try {
            rail.findRoutes(from, to, callback(search, RAIL));
        } catch (RuntimeException error) {
            failed(search, RAIL, "National Rail could not start the fallback search.");
        }
    }

    private JourneyPlannerEngine.JourneyCallback callback(Search search, int provider) {
        return new JourneyPlannerEngine.JourneyCallback() {
            @Override public void onJourneysFound(List<JourneyOption> journeys) {
                if (!active(search) || search.finished[provider]) return;
                List<JourneyOption> valid = new ArrayList<>();
                if (journeys != null) {
                    for (JourneyOption journey : journeys) {
                        if (journey == null || journey.legs == null || journey.legs.isEmpty()) continue;
                        if (provider == RAIL && search.modes != null && containsTransfer(journey)) continue;
                        valid.add(journey);
                    }
                }
                if (valid.isEmpty()) {
                    failed(search, provider, providerName(provider) + " returned no journeys.");
                    return;
                }
                cancel();
                search.callback.onJourneysFound(valid);
            }
            @Override public void onError(String message) { failed(search, provider, message); }
            @Override public void onStatusUpdate(String message) { }
        };
    }

    private static boolean containsTransfer(JourneyOption journey) {
        for (JourneyLeg leg : journey.legs) if ("TRANSFER".equalsIgnoreCase(leg.transportMode)) return true;
        return false;
    }

    private void deadline(Search search, int provider, long timeout) {
        Runnable timer = () -> failed(search, provider, providerName(provider) + " search timed out.");
        search.timers[provider] = timer;
        handler.postDelayed(timer, timeout);
    }

    private void failed(Search search, int provider, String message) {
        if (!active(search) || search.finished[provider]) return;
        search.finished[provider] = true;
        if (search.timers[provider] != null) handler.removeCallbacks(search.timers[provider]);
        search.errors[provider] = message == null ? providerName(provider) + " search failed." : message;
        if (provider == TFL) tfl.cancel();
        else if (provider == TRANSITOUS) transitous.cancel();
        else rail.cancel();
        if (provider == RAIL) finishError(search, null);
        else if (search.finished[TFL] && search.finished[TRANSITOUS]) startRail(search);
    }

    private void finishError(Search search, String explanation) {
        if (!active(search)) return;
        List<String> errors = new ArrayList<>();
        for (String error : search.errors) if (error != null) errors.add(error);
        if (explanation != null) errors.add(explanation);
        cancel();
        search.callback.onError(android.text.TextUtils.join("\n", errors));
    }

    private boolean active(Search search) { return !closed && current == search; }

    private static String providerName(int provider) {
        return provider == TFL ? "TfL" : provider == TRANSITOUS ? "Transitous" : "National Rail";
    }

    public void cancel() {
        Search previous = current;
        current = null;
        if (previous != null) {
            for (Runnable timer : previous.timers) if (timer != null) handler.removeCallbacks(timer);
        }
        tfl.cancel();
        transitous.cancel();
        rail.cancel();
    }

    public void shutdown() {
        closed = true;
        cancel();
        tfl.shutdown();
        transitous.shutdown();
        rail.shutdown();
    }

    private static JourneyLocation copy(JourneyLocation original) {
        JourneyLocation copy = new JourneyLocation();
        copy.type = original.type;
        copy.name = original.name;
        copy.stationId = original.stationId;
        copy.latitude = original.latitude;
        copy.longitude = original.longitude;
        return copy;
    }

    private static class Search {
        final JourneyLocation from, to;
        final String date, time, preference, modes;
        final boolean arriveBy, wheelchair;
        final JourneyPlannerEngine.JourneyCallback callback;
        final boolean[] finished = new boolean[3];
        final String[] errors = new String[3];
        final Runnable[] timers = new Runnable[3];
        boolean railStarted;

        Search(JourneyLocation from, JourneyLocation to, String date, String time,
               boolean arriveBy, String preference, String modes, boolean wheelchair,
               JourneyPlannerEngine.JourneyCallback callback) {
            this.from = from;
            this.to = to;
            this.date = date;
            this.time = time;
            this.arriveBy = arriveBy;
            this.preference = preference;
            this.modes = modes;
            this.wheelchair = wheelchair;
            this.callback = callback;
        }
    }
}
