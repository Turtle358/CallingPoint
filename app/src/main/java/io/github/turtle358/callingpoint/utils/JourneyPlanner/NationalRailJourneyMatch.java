package io.github.turtle358.callingpoint.utils.JourneyPlanner;

import io.github.turtle358.callingpoint.models.JourneyLeg;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.models.StationDepartureResponse;
import io.github.turtle358.callingpoint.utils.StationHelper;
import java.util.List;

/** Shared matching rules for the details screen and background journey tracking. */
public class NationalRailJourneyMatch {
    private NationalRailJourneyMatch() { }

    public static String crs(String code, String name) {
        if (code != null && code.matches("[A-Za-z]{3}")
                && !StationHelper.getNameFromCode(code).isEmpty()) return code;
        return StationHelper.getCrsFromName(name == null ? "" : name);
    }

    public static long departure(JourneyOption journey, JourneyLeg target) {
        if (target.scheduledDepartureEpochMillis > 0) return target.scheduledDepartureEpochMillis;
        if (journey == null || journey.legs == null || journey.departureDate == null
                || journey.departureDate.length() < 10) return 0;
        try {
            String day = journey.departureDate.substring(0, 10);
            java.util.Calendar calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/London"));
            calendar.setTimeInMillis(TransitousTime.parseExact(day, "yyyy-MM-dd"));
            int previous = -1;
            for (JourneyLeg leg : journey.legs) {
                int dep = minutes(leg.departureTime);
                int arr = minutes(leg.arrivalTime);
                if (dep < 0 || arr < 0) return 0;
                if (previous >= 0 && dep < previous) calendar.add(java.util.Calendar.DATE, 1);
                calendar.set(java.util.Calendar.HOUR_OF_DAY, dep / 60);
                calendar.set(java.util.Calendar.MINUTE, dep % 60);
                if (leg == target) return calendar.getTimeInMillis();
                if (arr < dep) calendar.add(java.util.Calendar.DATE, 1);
                previous = arr;
            }
        } catch (IllegalArgumentException ignored) { }
        return 0;
    }

    public static boolean inBoardWindow(JourneyOption journey, JourneyLeg leg, long now) {
        long departure = departure(journey, leg);
        return departure > 0 && departure >= now - 120 * 60000L && departure <= now + 120 * 60000L;
    }

    public static int offset(JourneyOption journey, JourneyLeg leg, long now) {
        return (int) Math.max(-120, Math.min(120, (departure(journey, leg) - now) / 60000 - 10));
    }

    public static StationDepartureResponse.TrainService select(
            List<StationDepartureResponse.TrainService> services, JourneyLeg leg) {
        if (services == null) return null;
        StationDepartureResponse.TrainService candidate = null;
        int bestDifference = Integer.MAX_VALUE;
        boolean ambiguous = false;
        int departure = minutes(leg.departureTime);
        if (departure < 0) return null;
        for (StationDepartureResponse.TrainService service : services) {
            if (service.serviceID == null || service.serviceID.trim().isEmpty()) continue;
            int scheduled = minutes(service.scheduledDepartureTime);
            if (scheduled < 0) continue;
            int difference = Math.abs(departure - scheduled);
            difference = Math.min(difference, 1440 - difference);
            if (difference > 2) continue;
            if (difference < bestDifference) {
                candidate = service;
                bestDifference = difference;
                ambiguous = false;
            } else if (difference == bestDifference && candidate != null
                    && !candidate.serviceID.equals(service.serviceID)) ambiguous = true;
        }
        return ambiguous ? null : candidate;
    }

    private static int minutes(String time) {
        if (time == null || !time.matches("\\d{2}:\\d{2}")) return -1;
        int hour = Integer.parseInt(time.substring(0, 2));
        int minute = Integer.parseInt(time.substring(3, 5));
        return hour < 24 && minute < 60 ? hour * 60 + minute : -1;
    }
}
