package io.github.turtle358.callingpoint.utils.JourneyPlanner

import io.github.turtle358.callingpoint.models.ServiceDetailsResponse
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse.TimelinePoint

object LiveLocationEngine {
    @JvmStatic
    fun getLiveLocation(details: ServiceDetailsResponse?): String? {
        if (details == null || details.isCancelled) return "Service Cancelled"

        val allStops: MutableList<TimelinePoint> = ArrayList<TimelinePoint>()

        if (details.previousCallingPoints != null && !details.previousCallingPoints.isEmpty()) {
            allStops.addAll(details.previousCallingPoints.get(0).callingPoint)
        }

        val current = TimelinePoint()
        current.locationName = details.locationName
        current.atd = details.atd
        current.ata = details.ata
        allStops.add(current)

        if (details.subsequentCallingPoints != null && !details.subsequentCallingPoints.isEmpty()) {
            allStops.addAll(details.subsequentCallingPoints.get(0).callingPoint)
        }

        for (i in allStops.indices.reversed()) {
            val stop = allStops.get(i)

            if (stop.atd != null && !stop.atd.isEmpty()) {
                return "Departed " + stop.locationName
            }
            if (stop.ata != null && !stop.ata.isEmpty()) {
                return "At " + stop.locationName
            }
        }

        return null
    }
}