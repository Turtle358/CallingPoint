package io.github.turtle358.callingpoint.utils;

import android.net.Uri;
import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.github.turtle358.callingpoint.models.JourneyLeg;
import io.github.turtle358.callingpoint.models.JourneyOption;

public final class SharedJourneyLink {
    private static final String BASE_URL = "https://app.callingpoint.co.uk/journey";
    private static final String PREFIX = "v1.";
    private static final int MAX_FRAGMENT_LENGTH = 12000;
    private static final int MAX_JSON_BYTES = 90_000;
    private static final int MAX_LEGS = 20;
    private static final int MAX_STOPS_PER_LEG = 200;
    private static final int MAX_TOTAL_STOPS = 400;
    private static final Gson GSON = new Gson();

    private static final String[] JOURNEY_FIELDS = {
            "departureDate", "departTime", "arriveTime", "duration", "changesSummary"
    };
    private static final String[] LEG_FIELDS = {
            "fromStationCode", "fromStationName", "toStationCode", "toStationName",
            "trainDestination", "departureTime", "arrivalTime", "operatorName",
            "serviceId", "transportMode", "departurePlatform", "arrivalPlatform",
            "durationMinutes", "intermediateStops"
    };
    private static final String[] STOP_FIELDS = { "stationName", "time" };

    private SharedJourneyLink() { }

    public static String create(JourneyOption journey) throws IOException {
        if (journey == null || journey.legs == null || journey.legs.isEmpty()
                || journey.legs.size() > MAX_LEGS) {
            throw new IllegalArgumentException("Invalid journey");
        }
        JsonObject snapshot = new JsonObject();
        JsonObject source = GSON.toJsonTree(journey).getAsJsonObject();
        copyFields(source, snapshot, JOURNEY_FIELDS);

        JsonArray legs = new JsonArray();
        int totalStops = 0;
        for (JourneyLeg leg : journey.legs) {
            if (leg == null || !hasText(leg.fromStationName) || !hasText(leg.toStationName)) {
                throw new IllegalArgumentException("Journey is missing station names");
            }
            JsonObject srcLeg = GSON.toJsonTree(leg).getAsJsonObject();
            JsonObject dstLeg = new JsonObject();
            copyFields(srcLeg, dstLeg, LEG_FIELDS);
            totalStops += sanitiseStops(dstLeg);
            if (totalStops > MAX_TOTAL_STOPS) {
                throw new IllegalArgumentException("Journey contains too many stops");
            }
            legs.add(dstLeg);
        }
        snapshot.add("legs", legs);
        byte[] json = GSON.toJson(snapshot).getBytes(StandardCharsets.UTF_8);
        if (json.length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Journey data is too large");
        }

        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(packed)) {
            gzip.write(json);
        }
        String data = Base64.encodeToString(packed.toByteArray(),
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        if (PREFIX.length() + data.length() > MAX_FRAGMENT_LENGTH) {
            throw new IllegalArgumentException("Journey link is too long for reliable sharing");
        }
        return BASE_URL + "#" + PREFIX + data;
    }

    public static JourneyOption parse(Uri uri) throws IOException {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())
                || !"app.callingpoint.co.uk".equalsIgnoreCase(uri.getHost())
                || !"/journey".equals(uri.getPath())) {
            throw new IllegalArgumentException("Not a CallingPoint journey link");
        }
        String fragment = uri.getFragment();
        if (fragment == null || !fragment.startsWith(PREFIX)
                || fragment.length() > MAX_FRAGMENT_LENGTH) {
            throw new IllegalArgumentException("Missing or unsupported journey data");
        }
        String encoded = fragment.substring(PREFIX.length());
        if (encoded.isEmpty() || !encoded.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid journey encoding");
        }

        byte[] packed;
        try {
            packed = Base64.decode(encoded, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid Base64 journey encoding", ex);
        }
        ByteArrayOutputStream unpacked = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(packed))) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = gzip.read(buffer)) != -1) {
                if (unpacked.size() + n > MAX_JSON_BYTES) {
                    throw new IllegalArgumentException("Decompressed journey exceeds size limit");
                }
                unpacked.write(buffer, 0, n);
            }
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(new String(unpacked.toByteArray(), StandardCharsets.UTF_8));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Journey JSON is invalid", ex);
        }
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Invalid journey format");
        JsonObject json = parsed.getAsJsonObject();
        JsonElement legsValue = json.get("legs");
        if (legsValue == null || !legsValue.isJsonArray()) {
            throw new IllegalArgumentException("No journey legs found");
        }
        JsonArray incomingLegs = legsValue.getAsJsonArray();
        if (incomingLegs.size() < 1 || incomingLegs.size() > MAX_LEGS) {
            throw new IllegalArgumentException("Invalid leg count");
        }

        JsonObject safe = new JsonObject();
        copyFields(json, safe, JOURNEY_FIELDS);
        JsonArray safeLegs = new JsonArray();
        int totalStops = 0;
        for (JsonElement element : incomingLegs) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Invalid leg");
            JsonObject inputLeg = element.getAsJsonObject();
            requireShortString(inputLeg, "fromStationName", 160);
            requireShortString(inputLeg, "toStationName", 160);
            requireShortString(inputLeg, "transportMode", 32);
            JsonObject safeLeg = new JsonObject();
            copyFields(inputLeg, safeLeg, LEG_FIELDS);
            totalStops += sanitiseStops(safeLeg);
            if (totalStops > MAX_TOTAL_STOPS) throw new IllegalArgumentException("Too many stops");
            safeLegs.add(safeLeg);
        }
        safe.add("legs", safeLegs);
        try {
            JourneyOption result = GSON.fromJson(safe, JourneyOption.class);
            if (result == null || result.legs == null || result.legs.isEmpty()) {
                throw new IllegalArgumentException("Journey could not be reconstructed");
            }
            return result;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Unable to reconstruct journey", ex);
        }
    }

    private static void copyFields(JsonObject from, JsonObject to, String[] keys) {
        for (String key : keys) {
            if (from.has(key) && !from.get(key).isJsonNull()) {
                to.add(key, from.get(key));
            }
        }
    }

    private static int sanitiseStops(JsonObject leg) {
        if (!leg.has("intermediateStops") || leg.get("intermediateStops").isJsonNull()) return 0;
        if (!leg.get("intermediateStops").isJsonArray()) {
            throw new IllegalArgumentException("Invalid intermediate stops");
        }
        JsonArray stops = leg.getAsJsonArray("intermediateStops");
        if (stops.size() > MAX_STOPS_PER_LEG) {
            throw new IllegalArgumentException("Too many intermediate stops");
        }
        JsonArray safeStops = new JsonArray();
        for (JsonElement stop : stops) {
            if (!stop.isJsonObject()) throw new IllegalArgumentException("Invalid stop");
            JsonObject src = stop.getAsJsonObject();
            requireShortString(src, "stationName", 160);
            JsonObject dst = new JsonObject();
            copyFields(src, dst, STOP_FIELDS);
            safeStops.add(dst);
        }
        leg.add("intermediateStops", safeStops);
        return safeStops.size();
    }

    private static void requireShortString(JsonObject obj, String name, int max) {
        JsonElement value = obj.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || !hasText(value.getAsString()) || value.getAsString().length() > max) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    private static boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }
}