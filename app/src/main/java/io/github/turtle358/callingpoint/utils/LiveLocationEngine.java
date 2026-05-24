package io.github.turtle358.callingpoint.utils;

import java.util.ArrayList;
import java.util.List;

import io.github.turtle358.callingpoint.models.ServiceDetailsResponse;

public class LiveLocationEngine {

    public static String getLiveLocation(ServiceDetailsResponse details) {
        if (details == null || details.isCancelled) return "Service Cancelled";

        // 1. Flatten the entire route into a single chronological list
        List<ServiceDetailsResponse.TimelinePoint> allStops = new ArrayList<>();

        if (details.previousCallingPoints != null && !details.previousCallingPoints.isEmpty()) {
            allStops.addAll(details.previousCallingPoints.get(0).callingPoint);
        }

        ServiceDetailsResponse.TimelinePoint current = new ServiceDetailsResponse.TimelinePoint();
        current.locationName = details.locationName;
        current.atd = details.atd;
        current.ata = details.ata;
        allStops.add(current);

        if (details.subsequentCallingPoints != null && !details.subsequentCallingPoints.isEmpty()) {
            allStops.addAll(details.subsequentCallingPoints.get(0).callingPoint);
        }

        for (int i = allStops.size() - 1; i >= 0; i--) {
            ServiceDetailsResponse.TimelinePoint stop = allStops.get(i);

            if (stop.atd != null && !stop.atd.isEmpty()) {
                if (i < allStops.size() - 1) {
                    ServiceDetailsResponse.TimelinePoint nextStop = allStops.get(i + 1);
                    return "Between " + stop.locationName + " and " + nextStop.locationName;
                } else {
                    return "Departed " + stop.locationName;
                }
            }
            if (stop.ata != null && !stop.ata.isEmpty()) {
                return "At " + stop.locationName;
            }
        }
        if (!allStops.isEmpty()) {
            return "Awaiting departure from " + allStops.get(0).locationName;
        }

        return "Location unknown";
    }
}