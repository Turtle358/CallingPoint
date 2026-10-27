package io.github.turtle358.callingpoint.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class JourneyLeg implements Serializable {
    public String fromStationCrs;
    public String fromStationName;
    public String toStationCrs;
    public String toStationName;

    public String trainDestination;
    public String transportMode;

    public String departureTime;
    public String arrivalTime;

    public String operatorName;
    public String serviceId;

    public int durationMinutes;

    public String liveLocation;
    public String liveDepartureTime;

    public boolean isExpanded = false;
    public List<Stop> intermediateStops = new ArrayList<>();

    public long lastMatchAttemptTime = 0;

    public String lastCheckedLiveTime;
    public String departurePlatform;
    public String arrivalPlatform;

    public static class Stop implements Serializable {
        public String stationName;
        public String time;

        public Stop(String stationName, String time) {
            this.stationName = stationName;
            this.time = time;
        }
    }

    public JourneyLeg(String fromCrs, String fromName, String toCrs, String toName, String trainDest, String depTime, String arrTime, String operator, String serviceId, String mode, List<Stop> intermediateStops, String liveLocation, String liveDepTime, String departurePlatform, String arrivalPlatform) {
        this.fromStationCrs = fromCrs;
        this.fromStationName = fromName;
        this.toStationCrs = toCrs;
        this.toStationName = toName;
        this.trainDestination = trainDest;
        this.transportMode = mode;
        this.departureTime = depTime;
        this.arrivalTime = arrTime;
        this.operatorName = operator;
        this.serviceId = serviceId;
        this.durationMinutes = calculateDuration(depTime, arrTime);
        this.liveLocation = liveLocation;
        this.liveDepartureTime = liveDepTime;
        this.departurePlatform = departurePlatform;
        this.arrivalPlatform = arrivalPlatform;

        if (intermediateStops != null) {
            this.intermediateStops.addAll(intermediateStops);
        }
    }

    private int calculateDuration(String start, String end) {
        if (start == null || end == null) return 0;
        try {
            String[] startParts = start.split(":");
            String[] endParts = end.split(":");
            int startMins = Integer.parseInt(startParts[0]) * 60 + Integer.parseInt(startParts[1]);
            int endMins = Integer.parseInt(endParts[0]) * 60 + Integer.parseInt(endParts[1]);

            int diff = endMins - startMins;
            if (diff < 0) diff += 24 * 60;
            return diff;
        } catch (Exception e) {
            return 0;
        }
    }
}