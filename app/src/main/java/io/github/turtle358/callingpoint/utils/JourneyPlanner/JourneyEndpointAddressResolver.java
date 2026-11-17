package io.github.turtle358.callingpoint.utils.JourneyPlanner;

import android.os.Handler;
import android.os.Looper;
import com.google.gson.JsonObject;
import io.github.turtle358.callingpoint.api.APIClient;
import io.github.turtle358.callingpoint.api.GeoapifyReverseAPI;
import io.github.turtle358.callingpoint.models.JourneyLeg;
import io.github.turtle358.callingpoint.models.JourneyLocation;
import io.github.turtle358.callingpoint.models.JourneyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class JourneyEndpointAddressResolver {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final GeoapifyReverseAPI api;
    private final Map<String, String> cache = new LinkedHashMap<>();
    private final Map<String, Call<JsonObject>> pending = new LinkedHashMap<>();
    private int generation;
    private Endpoint origin, destination;

    public JourneyEndpointAddressResolver() {
        Retrofit existing = APIClient.getClient();
        OkHttpClient client = ((OkHttpClient) existing.callFactory()).newBuilder()
                .callTimeout(4, TimeUnit.SECONDS).build();
        api = existing.newBuilder().client(client).build().create(GeoapifyReverseAPI.class);
    }

    public void resolve(JourneyLocation from, JourneyLocation to, Runnable onChanged) {
        cancel();
        origin = Endpoint.of(from);
        destination = Endpoint.of(to);
        int version = generation;
        lookup(origin, version, onChanged);
        lookup(destination, version, onChanged);
    }

    private void lookup(Endpoint endpoint, int version, Runnable onChanged) {
        if (endpoint == null || cache.containsKey(endpoint.key) || pending.containsKey(endpoint.key)) return;
        Call<JsonObject> call = api.reverse(endpoint.lat, endpoint.lon);
        pending.put(endpoint.key, call);
        call.enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                String address = null;
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JsonObject body = response.body();
                        if (body.has("address") && body.get("address").isJsonPrimitive()) {
                            String value = body.get("address").getAsString().trim();
                            if (!value.isEmpty()) address = value;
                        }
                    }
                } catch (RuntimeException ignored) {
                } finally {
                    if (response.errorBody() != null) response.errorBody().close();
                }
                final String resolved = address;
                main.post(() -> {
                    if (version != generation) return;
                    pending.remove(endpoint.key);
                    if (resolved == null) return;
                    if (cache.size() >= 100) cache.clear();
                    cache.put(endpoint.key, resolved);
                    onChanged.run();
                });
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable error) {
                main.post(() -> { if (version == generation) pending.remove(endpoint.key); });
            }
        });
    }

    public boolean apply(List<JourneyOption> journeys) {
        boolean changed = false;
        if (journeys == null) return false;
        for (JourneyOption journey : journeys) {
            if (journey == null || journey.legs == null || journey.legs.isEmpty()) continue;
            JourneyLeg first = journey.legs.get(0);
            JourneyLeg last = journey.legs.get(journey.legs.size() - 1);
            String fromAddress = origin == null ? null : cache.get(origin.key);
            String toAddress = destination == null ? null : cache.get(destination.key);
            if (fromAddress != null && origin.placeholder.equals(first.fromStationName)) {
                first.fromStationName = fromAddress;
                changed = true;
            }
            if (toAddress != null && destination.placeholder.equals(last.toStationName)) {
                last.toStationName = toAddress;
                changed = true;
            }
        }
        return changed;
    }

    public void cancel() {
        generation++;
        for (Call<JsonObject> call : new ArrayList<>(pending.values())) call.cancel();
        pending.clear();
        origin = destination = null;
        main.removeCallbacksAndMessages(null);
    }

    private static class Endpoint {
        final double lat, lon;
        final String key, placeholder;
        private Endpoint(JourneyLocation location) {
            lat = location.latitude;
            lon = location.longitude;
            key = String.format(Locale.US, "%.7f,%.7f", lat, lon);
            placeholder = location.displayName();
        }
        static Endpoint of(JourneyLocation location) {
            if (location == null || location.type != JourneyLocation.Type.CURRENT_LOCATION
                    || location.latitude == null || location.longitude == null
                    || !Double.isFinite(location.latitude) || !Double.isFinite(location.longitude)
                    || Math.abs(location.latitude) > 90 || Math.abs(location.longitude) > 180) return null;
            return new Endpoint(location);
        }
    }
}
