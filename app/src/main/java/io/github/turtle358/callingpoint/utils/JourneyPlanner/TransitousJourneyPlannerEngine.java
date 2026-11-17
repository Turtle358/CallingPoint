package io.github.turtle358.callingpoint.utils.JourneyPlanner;

import android.os.Handler;
import android.os.Looper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.turtle358.callingpoint.BuildConfig;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.GeoapifyAutocompleteAPI;
import io.github.turtle358.callingpoint.api.TransitousAPI;
import io.github.turtle358.callingpoint.models.JourneyLocation;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.utils.StationHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import static io.github.turtle358.callingpoint.utils.JourneyPlanner.TransitousJourneyMapper.text;

public class TransitousJourneyPlannerEngine {
    private static final int MAX_STATION_WALK_SECONDS = 60 * 60;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final TransitousAPI api;
    private final GeoapifyAutocompleteAPI geoapify;
    private final Map<String, String> stationCache = new LinkedHashMap<>();
    private volatile int generation;
    private volatile boolean closed;
    private Call<?> activeCall;
    private Future<?> task;

    public TransitousJourneyPlannerEngine() {
        Retrofit existing = APIClient.getClient();
        OkHttpClient client = ((OkHttpClient) existing.callFactory()).newBuilder()
                .readTimeout(75, TimeUnit.SECONDS)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", "CallingPoint/" + BuildConfig.VERSION_NAME
                                + " (+https://callingpoint.co.uk/)").build()))
                .build();
        Retrofit retrofit = existing.newBuilder().client(client).build();
        api = retrofit.create(TransitousAPI.class);
        geoapify = retrofit.create(GeoapifyAutocompleteAPI.class);
    }

    public synchronized void findRoutes(JourneyLocation from, JourneyLocation to,
                                       String date, String time, boolean arriveBy,
                                       String preference, String modes, boolean wheelchair,
                                       JourneyPlannerEngine.JourneyCallback callback) {
        cancel();
        if (closed) return;
        int version = generation;
        JourneyLocation origin = snapshot(from);
        JourneyLocation destination = snapshot(to);
        task = executor.submit(() -> {
            try {
                Map<String, String> query = new LinkedHashMap<>();
                query.put("fromPlace", resolve(origin, version));
                query.put("toPlace", resolve(destination, version));
                query.put("arriveBy", Boolean.toString(arriveBy));
                if (date != null && time != null) {
                    long instant = TransitousTime.parseExact(date + time, "yyyyMMddHHmm");
                    query.put("time", TransitousTime.format(instant, "yyyy-MM-dd'T'HH:mm:ssZ").replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2"));
                }
                query.put("transitModes", transitModes(modes));
                query.put("preTransitModes", "WALK");
                query.put("postTransitModes", "WALK");
                query.put("directModes", "WALK");
                query.put("maxPreTransitTime", Integer.toString(MAX_STATION_WALK_SECONDS));
                query.put("maxPostTransitTime", Integer.toString(MAX_STATION_WALK_SECONDS));
                query.put("numItineraries", "3");
                query.put("timeout", "60");
                query.put("pedestrianProfile", wheelchair ? "WHEELCHAIR" : "FOOT");
                query.put("useRoutedTransfers", "true");
                query.put("detailedLegs", "false");
                query.put("joinInterlinedLegs", "true");
                List<JourneyOption> journeys = TransitousJourneyMapper.parse(execute(api.plan(query), version), preference);
                for (JourneyOption journey : journeys) {
                    if (origin.type != JourneyLocation.Type.STATION) {
                        journey.legs.get(0).fromStationName = origin.displayName();
                    }
                    if (destination.type != JourneyLocation.Type.STATION) {
                        journey.legs.get(journey.legs.size() - 1).toStationName = destination.displayName();
                    }
                }
                main.post(() -> {
                    if (closed || version != generation) return;
                    if (journeys.isEmpty()) callback.onError("Transitous found no route with these options and walks of up to "
                            + (MAX_STATION_WALK_SECONDS / 60) + " minutes at either end. Try another transport mode, time or nearby station.");
                    else callback.onJourneysFound(journeys);
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (closed || version != generation) return;
                    String message = error instanceof IOException || error instanceof IllegalArgumentException
                            ? error.getMessage() : "Unable to read the Transitous journey response.";
                    callback.onError(message == null ? "Journey search failed. Tap to retry." : message);
                });
            }
        });
    }

    private String resolve(JourneyLocation location, int version) throws IOException {
        if (location.latitude != null && location.longitude != null) {
            return coordinates(location.latitude, location.longitude);
        }
        if (location.type != JourneyLocation.Type.STATION) {
            JsonObject root = execute(geoapify.suggestions(location.displayName()), version);
            List<String> matches = new ArrayList<>();
            for (JsonElement item : TransitousJourneyMapper.array(root, "results")) {
                JsonObject result = item.getAsJsonObject();
                String requested = location.displayName().replace(" ", "");
                if (location.type == JourneyLocation.Type.POSTCODE
                        && requested.equalsIgnoreCase(text(result, "postcode").replace(" ", ""))) {
                    matches.add(coordinates(result.get("lat").getAsDouble(), result.get("lon").getAsDouble()));
                }
            }
            if (!matches.isEmpty()) return matches.get(0);
            throw new IOException("Select an address or postcode suggestion with a map location for " + location.displayName() + ".");
        }
        String cached = stationCache.get(location.stableKey());
        if (cached != null) return cached;
        Map<String, String> query = new LinkedHashMap<>();
        query.put("text", location.displayName());
        query.put("type", "STOP");
        query.put("min", "49,-9");
        query.put("max", "61,2");
        query.put("numResults", "10");
        JsonArray results = execute(api.geocode(query), version);
        String expected = location.stationId;
        if (expected == null || expected.isEmpty()) expected = StationHelper.getNaptanFromName(location.displayName());
        Set<String> exact = new LinkedHashSet<>();
        String chosen = null;
        for (JsonElement item : results) {
            JsonObject result = item.getAsJsonObject();
            if (!"GB".equalsIgnoreCase(text(result, "country"))) continue;
            String id = text(result, "id");
            if (id.isEmpty()) continue;
            if (expected != null && !expected.isEmpty()
                    && (id.equals(expected) || id.endsWith("_" + expected))) {
                chosen = id;
                break;
            }
            if (StationHelper.areStationNamesEqual(location.displayName(), text(result, "name"))
                    && hasRailMode(result)) exact.add(id);
        }
        if (chosen == null && exact.size() == 1) chosen = exact.iterator().next();
        if (chosen == null) throw new IOException("Could not uniquely identify " + location.displayName()
                + " in Transitous. Select its address suggestion instead.");
        if (stationCache.size() >= 100) stationCache.clear();
        stationCache.put(location.stableKey(), chosen);
        return chosen;
    }

    private static boolean hasRailMode(JsonObject result) {
        for (JsonElement mode : TransitousJourneyMapper.array(result, "modes")) {
            String value = mode.getAsString();
            if (value.contains("RAIL") || value.equals("SUBURBAN")
                    || value.equals("SUBWAY") || value.equals("TRAM")) return true;
        }
        return false;
    }

    private static String coordinates(double lat, double lon) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Invalid journey coordinates");
        }
        return String.format(Locale.US, "%.7f,%.7f", lat, lon);
    }

    static String transitModes(String savedModes) {
        if (savedModes == null || savedModes.trim().isEmpty()) return "TRANSIT";
        Set<String> modes = new LinkedHashSet<>();
        for (String mode : savedModes.split(",")) {
            switch (mode.trim()) {
                case "national-rail":
                    modes.add("HIGHSPEED_RAIL"); modes.add("LONG_DISTANCE");
                    modes.add("NIGHT_RAIL"); modes.add("REGIONAL_RAIL"); break;
                case "overground": case "elizabeth-line": modes.add("SUBURBAN"); break;
                case "tube": case "dlr": modes.add("SUBWAY"); break;
                case "tram": modes.add("TRAM"); break;
                case "bus": modes.add("BUS"); modes.add("COACH"); break;
                default: break;
            }
        }
        if (modes.isEmpty()) throw new IllegalArgumentException("Select supported transport modes in Journey options.");
        return android.text.TextUtils.join(",", modes);
    }

    private <T> T execute(Call<T> call, int version) throws IOException {
        synchronized (this) {
            if (closed || version != generation || Thread.currentThread().isInterrupted()) throw new IOException("Search cancelled");
            activeCall = call;
        }
        try {
            Response<T> response = call.execute();
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException(describeError(call.request().url().encodedPath(), response));
            }
            return response.body();
        } finally {
            synchronized (this) { if (activeCall == call) activeCall = null; }
        }
    }

    static String describeError(String path, Response<?> response) {
        String provider = path.startsWith("/geoapify/") ? "Geoapify address lookup"
                : path.endsWith("/geocode") ? "Transitous station lookup" : "Transitous journey search";
        String message = provider + " failed (HTTP " + response.code() + ").";
        okhttp3.ResponseBody body = response.errorBody();
        if (body != null) {
            try (okhttp3.ResponseBody errorBody = body) {
                JsonElement parsed = JsonParser.parseString(errorBody.string());
                if (parsed.isJsonObject()) {
                    JsonObject error = parsed.getAsJsonObject();
                    String detail = safeErrorText(error, "error", 240);
                    String code = safeErrorText(error, "code", 64);
                    String upstreamStatus = safeErrorText(error, "upstreamStatus", 3);
                    String requestId = safeErrorText(error, "requestId", 64);
                    if (!detail.isEmpty()) message += " " + detail;
                    if (!upstreamStatus.isEmpty()) message += " Provider HTTP " + upstreamStatus + ".";
                    if (!code.isEmpty()) message += " [" + code + "]";
                    if (!requestId.isEmpty()) message += " Reference " + requestId + ".";
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }
        if (response.code() == 429) message += " Please try again later.";
        return message;
    }

    private static String safeErrorText(JsonObject object, String key, int limit) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return "";
        String text = value.getAsString().replaceAll("[\\p{Cntrl}]", " ").trim();
        return text.length() > limit ? text.substring(0, limit) : text;
    }

    private static JourneyLocation snapshot(JourneyLocation source) {
        JourneyLocation copy = new JourneyLocation();
        copy.type = source.type;
        copy.name = source.name;
        copy.stationId = source.stationId;
        copy.latitude = source.latitude;
        copy.longitude = source.longitude;
        return copy;
    }

    public synchronized void cancel() {
        generation++;
        if (activeCall != null) activeCall.cancel();
        if (task != null) task.cancel(true);
    }

    public synchronized void shutdown() {
        closed = true;
        cancel();
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }
}
