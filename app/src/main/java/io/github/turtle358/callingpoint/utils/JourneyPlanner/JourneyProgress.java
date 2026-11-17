package io.github.turtle358.callingpoint.utils.JourneyPlanner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import io.github.turtle358.callingpoint.models.JourneyLeg;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse;

/**
 * A timetable-led progress snapshot. It NEVER assumes that a traveller is on
 * a train merely because the clock has passed the scheduled departure time.
 * 'Remaining stops' is only used when National Rail reports a confirmed passed stop.
 */
public class JourneyProgress {
    private JourneyProgress() { }

    public static class Snapshot {
        @NonNull public String headline = "Journey in progress";
        @NonNull public String detail = "Monitoring journey";
        @NonNull public String secondary = "Timetable information";
        @NonNull public String arrival = "";
        @Nullable public String liveArrivalAlert;
        @Nullable public String changeSoonAlert;
        @Nullable public String changeAlertKey;
        @Nullable public String cancelledAlert;
        public int percentage;
        public boolean finished;
        public long scheduledArrivalMillis;
    }

    @NonNull
    public static Snapshot calculate(@NonNull JourneyOption journey,
                                     @NonNull Map<String, ServiceDetailsResponse> live,
                                     long nowMillis) {
        Snapshot s = new Snapshot();
        if (journey.legs == null || journey.legs.isEmpty()) {
            s.headline = "No journey selected";
            return s;
        }
        List<JourneyLeg> legs = journey.legs;
        long start = departureMillis(journey);
        long end = endMillis(journey);
        s.scheduledArrivalMillis = end;
        if (start > 0 && end > start) {
            s.percentage = (int) Math.max(0, Math.min(100, (nowMillis - start) * 100L / (end - start)));
        }
        s.finished = end > 0 && nowMillis > end + 30 * 60_000L;
        s.arrival = safe(journey.arriveTime);

        int currentIndex = legs.size() - 1;
        long previous = start;
        for (int i = 0; i < legs.size(); i++) {
            JourneyLeg l = legs.get(i);
            if (l == null) continue;
            String providerArrival = isTrain(l)
                    ? predictionForStop(details(live, l), l.toStationName) : null;
            long arrival = l.scheduledArrivalEpochMillis;
            if (arrival <= 0 || isClock(providerArrival)) {
                arrival = clockOnOrAfter(previous,
                        isClock(providerArrival) ? providerArrival : l.arrivalTime);
            }
            if (arrival <= 0) continue;
            if (nowMillis < arrival) {
                currentIndex = i;
                break;
            }
            previous = arrival;
        }
        JourneyLeg current = legs.get(currentIndex);
        if (current == null) return s;

        JourneyLeg finalVehicle = null;
        for (JourneyLeg leg : legs) {
            if (leg != null && !JourneyChangeSummary.isWalking(leg)) finalVehicle = leg;
        }
        if (finalVehicle != null) {
            ServiceDetailsResponse finalLive = details(live, finalVehicle);
            String updated = predictionForStop(finalLive, finalVehicle.toStationName);
            int difference = minuteDifference(updated, finalVehicle.arrivalTime);
            if (difference != Integer.MIN_VALUE && difference != 0 && Math.abs(difference) < 180
                    && isTrain(finalVehicle)) {
                s.arrival = offsetClock(journey.arriveTime, difference);
                if (end > 0 && difference > 0) {
                    s.finished = nowMillis > end + difference * 60_000L + 30 * 60_000L;
                }
                if (Math.abs(difference) >= 3) {
                    s.liveArrivalAlert = "Estimated journey arrival " + s.arrival
                            + " (" + (difference > 0 ? difference + " min later" : -difference + " min earlier") + ")";
                }
            }
        }

        if (JourneyChangeSummary.isWalking(current)) {
            s.headline = "Walk to " + name(current.toStationName);
            s.detail = "Walking section · scheduled arrival " + safe(current.arrivalTime);
            s.secondary = "Arrival " + s.arrival + " · timetable estimate";
            return s;
        }
        s.headline = "Towards " + name(current.toStationName);
        s.detail = "Alight at " + name(current.toStationName);
        s.secondary = "Arrival " + s.arrival + " · timetable estimate";

        ServiceDetailsResponse currentLive = details(live, current);
        if (currentLive != null && currentLive.isCancelled) {
            s.headline = "Service cancelled";
            s.detail = name(current.fromStationName) + " → " + name(current.toStationName);
            s.secondary = "Check journey details for alternatives";
            s.cancelledAlert = "Train cancelled: " + name(current.fromStationName) + " to " + name(current.toStationName);
            return s;
        }
        if (isTrain(current) && currentLive != null) {
            StopProgress stops = stopProgress(currentLive, current.fromStationName, current.toStationName);
            if (stops.knownRemaining >= 0) {
                s.detail = (stops.knownRemaining == 1 ? "1 stop remaining" : stops.knownRemaining + " stops remaining")
                        + " · alight at " + name(current.toStationName);
            } else if (stops.total > 0) {
                s.detail = stops.total + (stops.total == 1 ? " stop on this leg" : " stops on this leg")
                        + " · live position unconfirmed";
            }
            String updatedLeg = predictionForStop(currentLive, current.toStationName);
            if (isClock(updatedLeg)) s.secondary = "Train ETA " + updatedLeg + " · journey ETA " + s.arrival;
        } else if (current.intermediateStops != null && !current.intermediateStops.isEmpty()) {
            s.detail = (current.intermediateStops.size() + 1) + " stops on this leg · progress unconfirmed";
        }

        JourneyLeg nextVehicle = null;
        for (int i = currentIndex + 1; i < legs.size(); i++) {
            JourneyLeg candidate = legs.get(i);
            if (candidate != null && !JourneyChangeSummary.isWalking(candidate)) {
                nextVehicle = candidate;
                break;
            }
        }
        if (nextVehicle != null) {
            String station = name(current.toStationName);
            String updatedLeg = predictionForStop(currentLive, current.toStationName);
            String targetClock = isClock(updatedLeg) ? updatedLeg : current.arrivalTime;
            long eventTime = clockNear(nowMillis, targetClock);
            long remaining = eventTime > 0 ? (eventTime - nowMillis) / 60_000L : Long.MAX_VALUE;
            if (remaining >= 0 && remaining <= 4) {
                s.headline = "Change soon at " + station;
                s.changeAlertKey = station + "|" + name(nextVehicle.toStationName);
                s.changeSoonAlert = "Change at " + station + " in approximately " + (remaining + 1)
                        + " min · next: " + name(nextVehicle.toStationName);
                if (!isClock(updatedLeg)) s.secondary = "Timetable estimate · verify station screens";
            } else {
                s.secondary = "Next change: " + station + (isClock(updatedLeg) ? " · train ETA " + updatedLeg : "") + " · journey ETA " + s.arrival;
            }
        }
        return s;
    }

    public static long departureMillis(@NonNull JourneyOption j) {
        if (j.legs != null && !j.legs.isEmpty() && j.legs.get(0).scheduledDepartureEpochMillis > 0) {
            return j.legs.get(0).scheduledDepartureEpochMillis;
        }
        return clockOnJourneyDate(j.departureDate, j.departTime);
    }

    public static long endMillis(@NonNull JourneyOption j) {
        if (j.legs != null && !j.legs.isEmpty()) {
            long end = j.legs.get(j.legs.size() - 1).scheduledArrivalEpochMillis;
            if (end > 0) return end;
        }
        long start = departureMillis(j);
        return clockOnOrAfter(start, j.arriveTime);
    }

    private static long clockOnJourneyDate(String date, String clock) {
        if (!isClock(clock)) return -1;
        Calendar c = Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/London"));
        if (date != null && date.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
            try {
                c.set(Integer.parseInt(date.substring(0, 4)),
                      Integer.parseInt(date.substring(5, 7)) - 1,
                      Integer.parseInt(date.substring(8, 10)));
            } catch (RuntimeException ignored) { }
        }
        c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(clock.substring(0, 2)));
        c.set(Calendar.MINUTE, Integer.parseInt(clock.substring(3, 5)));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long clockOnOrAfter(long base, String time) {
        if (base < 0 || !isClock(time)) return -1;
        Calendar c = Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/London"));
        c.setTimeInMillis(base);
        c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(time.substring(0, 2)));
        c.set(Calendar.MINUTE, Integer.parseInt(time.substring(3, 5)));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() < base - 2 * 60_000L) c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    private static long clockNear(long now, String time) {
        if (!isClock(time)) return -1;
        Calendar c = Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/London"));
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(time.substring(0, 2)));
        c.set(Calendar.MINUTE, Integer.parseInt(time.substring(3, 5)));
        c.set(Calendar.SECOND, 0);
        long result = c.getTimeInMillis();
        if (result - now > 12 * 3600_000L) result -= 24 * 3600_000L;
        if (now - result > 12 * 3600_000L) result += 24 * 3600_000L;
        return result;
    }

    private static boolean isClock(String v) {
        if (v == null || !v.matches("\\d{2}:\\d{2}")) return false;
        int h = Integer.parseInt(v.substring(0,2)), m = Integer.parseInt(v.substring(3));
        return h < 24 && m < 60;
    }
    private static String safe(String v) { return v == null ? "--:--" : v; }
    private static String name(String v) { return v == null ? "your stop" : v.trim(); }
    private static String offsetClock(String original, int difference) {
        if (!isClock(original)) return safe(original);
        int t = (Integer.parseInt(original.substring(0, 2)) * 60
                + Integer.parseInt(original.substring(3)) + difference + 1440) % 1440;
        return String.format(Locale.UK, "%02d:%02d", t / 60, t % 60);
    }
    private static int minuteDifference(String actual, String scheduled) {
        if (!isClock(actual) || !isClock(scheduled)) return Integer.MIN_VALUE;
        int a = Integer.parseInt(actual.substring(0,2)) * 60 + Integer.parseInt(actual.substring(3));
        int b = Integer.parseInt(scheduled.substring(0,2)) * 60 + Integer.parseInt(scheduled.substring(3));
        int d = a - b;
        if (d > 720) d -= 1440;
        if (d < -720) d += 1440;
        return d;
    }
    private static boolean isTrain(JourneyLeg leg) {
        return leg != null && "TRAIN".equalsIgnoreCase(leg.transportMode);
    }
    @Nullable private static ServiceDetailsResponse details(Map<String, ServiceDetailsResponse> data, JourneyLeg leg) {
        return leg.serviceId == null ? null : data.get(leg.serviceId);
    }
    @Nullable private static String predictionForStop(ServiceDetailsResponse details, String targetName) {
        if (details == null) return null;
        for (ServiceDetailsResponse.TimelinePoint point : allPoints(details)) {
            if (point != null && sameStation(point.locationName, targetName) && !point.isCancelled) {
                if (isClock(point.actualTime)) return point.actualTime;
                if (isClock(point.estimatedTime)) return point.estimatedTime;
                return null;
            }
        }
        return null;
    }
    private static boolean sameStation(String a, String b) {
        if (a == null || b == null) return false;
        return a.replaceAll("(?i)( rail| underground)? station$", "").trim()
                .equalsIgnoreCase(b.replaceAll("(?i)( rail| underground)? station$", "").trim());
    }
    private static List<ServiceDetailsResponse.TimelinePoint> allPoints(ServiceDetailsResponse details) {
        List<ServiceDetailsResponse.TimelinePoint> points = new ArrayList<>();
        if (details.previousCallingPoints != null && !details.previousCallingPoints.isEmpty()
                && details.previousCallingPoints.get(0).callingPoint != null)
            points.addAll(details.previousCallingPoints.get(0).callingPoint);
        ServiceDetailsResponse.TimelinePoint station = new ServiceDetailsResponse.TimelinePoint();
        station.locationName = details.locationName;
        station.scheduledTime = details.scheduledDepartureTime != null ? details.scheduledDepartureTime : details.scheduledArrivalTime;
        station.estimatedTime = details.estimatedDepartureTime != null ? details.estimatedDepartureTime : details.estimatedArrivalTime;
        station.actualTime = details.actualDepartureTime != null ? details.actualDepartureTime : details.actualArrivalTime;
        points.add(station);
        if (details.subsequentCallingPoints != null && !details.subsequentCallingPoints.isEmpty()
                && details.subsequentCallingPoints.get(0).callingPoint != null)
            points.addAll(details.subsequentCallingPoints.get(0).callingPoint);
        return points;
    }
    private static class StopProgress {
        int total;
        int knownRemaining = -1;
    }
    private static StopProgress stopProgress(ServiceDetailsResponse details, String from, String to) {
        StopProgress result = new StopProgress();
        List<ServiceDetailsResponse.TimelinePoint> points = allPoints(details);
        int fromIndex = -1, toIndex = -1;
        for (int i = 0; i < points.size(); i++) {
            ServiceDetailsResponse.TimelinePoint p = points.get(i);
            if (p == null) continue;
            if (fromIndex < 0 && sameStation(p.locationName, from)) fromIndex = i;
            if (fromIndex >= 0 && sameStation(p.locationName, to)) { toIndex = i; break; }
        }
        if (fromIndex < 0 || toIndex <= fromIndex) return result;
        result.total = toIndex - fromIndex;
        int lastConfirmed = -1;
        for (int i = fromIndex; i <= toIndex; i++) {
            ServiceDetailsResponse.TimelinePoint p = points.get(i);
            if (p != null && (isClock(p.actualTime)
                    || "Departed".equalsIgnoreCase(p.estimatedTime)
                    || "Arrived".equalsIgnoreCase(p.estimatedTime))) lastConfirmed = i;
        }
        if (lastConfirmed >= fromIndex) result.knownRemaining = Math.max(0, toIndex - lastConfirmed);
        return result;
    }
}
