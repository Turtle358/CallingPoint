package io.github.turtle358.callingpoint.models;

import java.io.Serializable;
import java.util.List;
public class JourneyOption implements Serializable {
    public List<JourneyLeg> legs;
    public String departTime;
    public String arriveTime;
    public String duration;
    public String changesSummary;
    public String liveDepartTime;

    public JourneyOption(List<JourneyLeg> legs) {
        this.legs = legs;
        calculateSummaries();
    }

    private void calculateSummaries() {
        if (legs == null || legs.isEmpty()) return;

        this.departTime = legs.get(0).departureTime;
        this.arriveTime = legs.get(legs.size() - 1).arrivalTime;
        this.liveDepartTime = legs.get(0).liveDepartureTime;

        int totalMinutes = 0;
        for (JourneyLeg leg : legs) {
            totalMinutes += leg.durationMinutes;
        }

        for (int i = 0; i < legs.size() - 1; i++) {
            JourneyLeg currentLeg = legs.get(i);
            JourneyLeg nextLeg = legs.get(i + 1);
            totalMinutes += calculateTransferTime(currentLeg.arrivalTime, nextLeg.departureTime);
        }

        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        this.duration = (hours > 0 ? hours + "h " : "") + mins + "m";

        int changes = legs.size() - 1;
        if (changes == 0) {
            this.changesSummary = "Direct";
        } else if (changes == 1) {
            this.changesSummary = "1 Change via " + legs.get(0).toStationName;
        } else {
            this.changesSummary = changes + " Changes";
        }
    }

    private int calculateTransferTime(String arrival, String nextDeparture) {
        if (arrival == null || nextDeparture == null) return 0;
        try {
            String[] arrParts = arrival.split(":");
            String[] depParts = nextDeparture.split(":");
            int arrMins = Integer.parseInt(arrParts[0]) * 60 + Integer.parseInt(arrParts[1]);
            int depMins = Integer.parseInt(depParts[0]) * 60 + Integer.parseInt(depParts[1]);

            int diff = depMins - arrMins;
            if (diff < 0) diff += 24 * 60;
            return diff;
        } catch (Exception e) {
            return 0;
        }
    }
}