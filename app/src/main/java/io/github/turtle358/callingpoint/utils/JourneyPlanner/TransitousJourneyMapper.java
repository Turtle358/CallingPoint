package io.github.turtle358.callingpoint.utils.JourneyPlanner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.turtle358.callingpoint.models.JourneyLeg;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.utils.StationHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TransitousJourneyMapper {
    private TransitousJourneyMapper() { }

    public static List<JourneyOption> parse(JsonObject root, String preference) {
        List<JourneyOption> journeys = new ArrayList<>();
        append(journeys, array(root, "itineraries"));
        append(journeys, array(root, "direct"));
        if ("leastinterchange".equals(preference)) {
            journeys.sort(Comparator.comparingInt(j -> j.transitousTransfers));
        } else if ("leastwalking".equals(preference)) {
            journeys.sort(Comparator.comparingLong(j -> j.transitousWalkingSeconds));
        }
        return journeys;
    }

    private static void append(List<JourneyOption> output, JsonArray itineraries) {
        for (JsonElement element : itineraries) {
            JsonObject itinerary = element.getAsJsonObject();
            List<JourneyLeg> legs = new ArrayList<>();
            boolean cancelled = false;
            long walking = 0;
            for (JsonElement item : array(itinerary, "legs")) {
                JsonObject source = item.getAsJsonObject();
                JsonObject from = source.getAsJsonObject("from");
                JsonObject to = source.getAsJsonObject("to");
                if (flag(source, "cancelled") || flag(from, "cancelled") || flag(to, "cancelled")) {
                    cancelled = true;
                    break;
                }
                String mode = mode(text(source, "mode"));
                String fromName = canonicalName(text(from, "name"));
                String toName = canonicalName(text(to, "name"));
                long scheduledStart = TransitousTime.parse(text(source, "scheduledStartTime"));
                long scheduledEnd = TransitousTime.parse(text(source, "scheduledEndTime"));
                long start = TransitousTime.parse(text(source, "startTime"));
                long end = TransitousTime.parse(text(source, "endTime"));
                String operator = text(source, "agencyName");
                String route = text(source, "displayName");
                if (route.isEmpty()) route = text(source, "routeShortName");
                if ("TUBE/WALK".equals(mode)) operator = "Walking";
                else if (!"TRAIN".equals(mode)) operator = route.isEmpty() ? operator : route;
                if (operator.isEmpty()) operator = text(source, "mode");
                List<JourneyLeg.Stop> stops = new ArrayList<>();
                for (JsonElement stopElement : array(source, "intermediateStops")) {
                    JsonObject stop = stopElement.getAsJsonObject();
                    if (flag(stop, "cancelled")) continue;
                    String at = text(stop, "scheduledDeparture");
                    if (at.isEmpty()) at = text(stop, "scheduledArrival");
                    stops.add(new JourneyLeg.Stop(canonicalName(text(stop, "name")),
                            at.isEmpty() ? "--:--" : TransitousTime.format(TransitousTime.parse(at), "HH:mm")));
                }
                JourneyLeg leg = new JourneyLeg(crs(fromName), fromName, crs(toName), toName,
                        text(source, "headsign"), TransitousTime.format(scheduledStart, "HH:mm"),
                        TransitousTime.format(scheduledEnd, "HH:mm"), operator, null, mode, stops,
                        null, TransitousTime.format(start, "HH:mm"), text(from, "track"), text(to, "track"));
                leg.transitousTripId = text(source, "tripId");
                leg.scheduledDepartureEpochMillis = scheduledStart;
                leg.scheduledArrivalEpochMillis = scheduledEnd;
                leg.departureEpochMillis = start;
                leg.arrivalEpochMillis = end;
                leg.transitousRealtime = flag(source, "realTime");
                leg.fromLatitude = from.get("lat").getAsDouble();
                leg.fromLongitude = from.get("lon").getAsDouble();
                leg.toLatitude = to.get("lat").getAsDouble();
                leg.toLongitude = to.get("lon").getAsDouble();
                leg.durationMinutes = (int) Math.max(0, (scheduledEnd - scheduledStart) / 60000);
                legs.add(leg);
                if ("TUBE/WALK".equals(mode)) walking += Math.max(0, (end - start) / 1000);
            }
            if (cancelled || legs.isEmpty()) continue;
            JourneyOption journey = new JourneyOption(legs);
            long start = TransitousTime.parse(text(itinerary, "startTime"));
            long end = TransitousTime.parse(text(itinerary, "endTime"));
            journey.departureDate = TransitousTime.format(legs.get(0).scheduledDepartureEpochMillis, "yyyy-MM-dd'T'HH:mm:ss");
            journey.transitousTransfers = itinerary.has("transfers") ? itinerary.get("transfers").getAsInt() : 0;
            journey.transitousWalkingSeconds = walking;
            journey.changesSummary = journey.transitousTransfers == 0 ? "Direct"
                    : journey.transitousTransfers + (journey.transitousTransfers == 1 ? " Change" : " Changes");
            long minutes = Math.max(0, (end - start) / 60000);
            journey.duration = (minutes >= 60 ? minutes / 60 + "h " : "") + minutes % 60 + "m";
            output.add(journey);
        }
    }

    private static String mode(String value) {
        switch (value) {
            case "WALK": return "TUBE/WALK";
            case "BUS": case "COACH": return "BUS";
            case "SUBWAY": case "TRAM": return "TUBE";
            case "RAIL": case "HIGHSPEED_RAIL": case "LONG_DISTANCE": case "NIGHT_RAIL":
            case "REGIONAL_RAIL": case "REGIONAL_FAST_RAIL": case "SUBURBAN": return "TRAIN";
            default: return value;
        }
    }

    static String canonicalName(String name) {
        for (String known : StationHelper.getStationNames()) {
            if (StationHelper.areStationNamesEqual(known, name)) return known;
        }
        return name;
    }

    private static String crs(String name) {
        String code = StationHelper.getCrsFromName(name);
        return code == null ? "" : code;
    }

    public static String text(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        return object.get(key).getAsString();
    }

    static JsonArray array(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray()
                ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static boolean flag(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
    }
}
