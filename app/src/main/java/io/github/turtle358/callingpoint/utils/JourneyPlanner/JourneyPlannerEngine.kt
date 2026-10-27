package io.github.turtle358.callingpoint.utils.JourneyPlanner

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.turtle358.callingpoint.api.APIClient
import io.github.turtle358.callingpoint.api.NationalRailAPI
import io.github.turtle358.callingpoint.models.JourneyLeg
import io.github.turtle358.callingpoint.models.JourneyOption
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse.TimelinePoint
import io.github.turtle358.callingpoint.models.StationDepartureResponse
import io.github.turtle358.callingpoint.models.StationDepartureResponse.TrainService
import io.github.turtle358.callingpoint.utils.JourneyPlanner.CityTransferManager.TransferInfo
import io.github.turtle358.callingpoint.utils.JourneyPlanner.LiveLocationEngine.getLiveLocation
import io.github.turtle358.callingpoint.utils.StationHelper
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class JourneyPlannerEngine(private val graphManager: GraphManager) {
    interface JourneyCallback {
        fun onJourneysFound(journeys: MutableList<JourneyOption?>?)
        fun onError(message: String?)
        fun onStatusUpdate(message: String?)
    }

    private val api: NationalRailAPI
    private val executor: ExecutorService
    private val mainHandler: Handler

    private var boardCache: MutableMap<String?, StationDepartureResponse?>? = null
    private var detailCache: MutableMap<String?, ServiceDetailsResponse?>? = null

    init {
        this.api = APIClient.getClient().create<NationalRailAPI>(NationalRailAPI::class.java)
        this.executor = Executors.newSingleThreadExecutor()
        this.mainHandler = Handler(Looper.getMainLooper())
    }

    fun findRoutes(originCrs: String?, destCrs: String, callback: JourneyCallback) {

        executor.execute {
            boardCache = HashMap()
            detailCache = HashMap()
            val foundJourneys: MutableList<JourneyOption?> = ArrayList()
            try {
                findDirect(originCrs, destCrs, foundJourneys)

                if (foundJourneys.size < 5) {
                    findOneChange(originCrs, destCrs, foundJourneys)
                }

                if (foundJourneys.size < 3) {
                    findCrossCity(originCrs, destCrs, foundJourneys)
                }

                if (foundJourneys.size < 3) {
                    findTwoChange(originCrs, destCrs, foundJourneys)
                }

                foundJourneys.sortWith(Comparator { j1: JourneyOption?, j2: JourneyOption? ->
                    val arr1 = if (j1!!.arriveTime != null) j1.arriveTime else "23:59"
                    val arr2 = if (j2!!.arriveTime != null) j2.arriveTime else "23:59"
                    arr1.compareTo(arr2)
                })
            } catch (e: Exception) {
                Log.e(TAG, "Network error during search", e)
            }
        }
    }

    private fun getBestTime(scheduled: String?, estimated: String?): String? {
        if (estimated == null || estimated.trim { it <= ' ' }.isEmpty()) return scheduled
        if (estimated.equals("On time", ignoreCase = true)) return scheduled
        if (estimated.equals("Delayed", ignoreCase = true) || estimated.equals(
                "Cancelled",
                ignoreCase = true
            )
        ) {
            return null
        }
        return estimated
    }

    private fun findDirect(
        originCrs: String?,
        destCrs: String,
        results: MutableList<JourneyOption?>
    ) {
        val directRes = getBoard(originCrs, 25, destCrs)
        if (directRes != null && directRes.trainServices != null) {
            for (service in directRes.trainServices) {
                val depTime = getBestTime(service.scheduledDepartureTime, service.estimatedDepartureTime)
                if (depTime == null) continue

                val arrTime = getPreciseArrivalTime(service.serviceID, destCrs)
                if (arrTime == null) continue

                val destName =
                    if (service.destination != null && !service.destination.isEmpty()) service.destination.get(
                        0
                    ).locationName else destCrs
                val intermediateStops =
                    extractIntermediateStops(service.serviceID, originCrs, destCrs)

                val legs: MutableList<JourneyLeg> = ArrayList<JourneyLeg>()
                legs.add(
                    JourneyLeg(
                        originCrs,
                        getStationName(originCrs),
                        destCrs,
                        getStationName(destCrs),
                        destName,
                        service.scheduledDepartureTime,
                        arrTime,
                        service.operatorName,
                        service.serviceID,
                        "TRAIN",
                        intermediateStops,
                        getLiveLocationStatus(service.serviceID),
                        service.estimatedDepartureTime,
                        null,
                        null
                    )
                )

                results.add(JourneyOption(legs))
                graphManager.addConnection(originCrs, destCrs)
            }
        }
    }

    private fun findOneChange(
        originCrs: String?,
        destCrs: String,
        results: MutableList<JourneyOption?>
    ) {
        val allDepRes = getBoard(originCrs, 20, null)
        if (allDepRes == null || allDepRes.trainServices == null) return

        val hubs = getPrioritizedHubs(allDepRes.trainServices, originCrs)
        var hubCount = 0

        for (hubCrs in hubs) {
            if (hubCrs == destCrs || hubCrs == originCrs) continue
            if (hubCount++ >= 8) break

            val leg2Res = getBoard(hubCrs, 50, destCrs)
            if (leg2Res != null && leg2Res.trainServices != null) {
                var leg1Count = 0
                for (t1 in allDepRes.trainServices) {
                    if (leg1Count++ >= 6) break

                    val t1Dep = getBestTime(t1.scheduledDepartureTime, t1.estimatedDepartureTime)
                    if (t1Dep == null) continue

                    if (getPreciseArrivalTime(t1.serviceID, destCrs) != null) continue

                    val t1ArrTime = getPreciseArrivalTime(t1.serviceID, hubCrs)
                    if (t1ArrTime == null) continue

                    for (t2 in leg2Res.trainServices) {
                        val t2DepTime = getBestTime(t2.scheduledDepartureTime, t2.estimatedDepartureTime)
                        if (t2DepTime == null) continue

                        if (hasValidTransferTime(t1ArrTime, t2DepTime, 5, 180)) {
                            val t2ArrTime = getPreciseArrivalTime(t2.serviceID, destCrs)
                            if (t2ArrTime == null) continue

                            val t1Dest =
                                if (t1.destination != null && !t1.destination.isEmpty()) t1.destination.get(
                                    0
                                ).locationName else hubCrs
                            val t2Dest =
                                if (t2.destination != null && !t2.destination.isEmpty()) t2.destination.get(
                                    0
                                ).locationName else destCrs

                            val stops1 = extractIntermediateStops(t1.serviceID, originCrs, hubCrs)
                            val stops2 = extractIntermediateStops(t2.serviceID, hubCrs, destCrs)

                            val legs: MutableList<JourneyLeg> = ArrayList<JourneyLeg>()
                            legs.add(
                                JourneyLeg(
                                    originCrs,
                                    getStationName(originCrs),
                                    hubCrs,
                                    getStationName(hubCrs),
                                    t1Dest,
                                    t1.scheduledDepartureTime,
                                    t1ArrTime,
                                    t1.operatorName,
                                    t1.serviceID,
                                    "TRAIN",
                                    stops1,
                                    getLiveLocationStatus(t1.serviceID),
                                    t1.estimatedDepartureTime,
                                    null,
                                    null
                                )
                            )
                            legs.add(
                                JourneyLeg(
                                    hubCrs,
                                    getStationName(hubCrs),
                                    destCrs,
                                    getStationName(destCrs),
                                    t2Dest,
                                    t2.scheduledDepartureTime,
                                    t2ArrTime,
                                    t2.operatorName,
                                    t2.serviceID,
                                    "TRAIN",
                                    stops2,
                                    getLiveLocationStatus(t2.serviceID),
                                    t2.estimatedDepartureTime,
                                    null,
                                    null
                                )
                            )

                            if (isLogicalJourney(legs)) {
                                results.add(JourneyOption(legs))
                                graphManager.addConnection(originCrs, hubCrs)
                                graphManager.addConnection(hubCrs, destCrs)
                                break
                            }
                        }
                    }
                }
                if (results.size >= 10) return
            }
        }
    }

    private fun findTwoChange(
        originCrs: String?,
        destCrs: String,
        results: MutableList<JourneyOption?>
    ) {
        val allOrigin = getBoard(originCrs, 15, null)
        if (allOrigin == null || allOrigin.trainServices == null) return

        val hub1s = getPrioritizedHubs(allOrigin.trainServices, originCrs)
        var h1Count = 0

        for (hub1 in hub1s) {
            if (hub1 == destCrs || hub1 == originCrs) continue
            if (h1Count++ >= 5) break

            val allHub1 = getBoard(hub1, 20, null)
            if (allHub1 == null || allHub1.trainServices == null) continue

            val hub2s = getPrioritizedHubs(allHub1.trainServices, hub1)
            var h2Count = 0

            for (hub2 in hub2s) {
                if (hub2 == destCrs || hub2 == originCrs || hub2 == hub1) continue
                if (h2Count++ >= 10) break

                val hub2ToDest = getBoard(hub2, 30, destCrs)
                if (hub2ToDest == null || hub2ToDest.trainServices == null || hub2ToDest.trainServices.isEmpty()) continue

                for (t1 in allOrigin.trainServices) {
                    if (getBestTime(t1.scheduledDepartureTime, t1.estimatedDepartureTime) == null) continue
                    if (getPreciseArrivalTime(t1.serviceID, destCrs) != null) continue
                    if (getPreciseArrivalTime(t1.serviceID, hub2) != null) continue

                    val t1ArrTime = getPreciseArrivalTime(t1.serviceID, hub1)
                    if (t1ArrTime == null) continue

                    for (t2 in allHub1.trainServices) {
                        val t2DepTime = getBestTime(t2.scheduledDepartureTime, t2.estimatedDepartureTime)
                        if (t2DepTime == null) continue
                        if (getPreciseArrivalTime(t2.serviceID, destCrs) != null) continue

                        if (hasValidTransferTime(t1ArrTime, t2DepTime, 5, 120)) {
                            val t2ArrTime = getPreciseArrivalTime(t2.serviceID, hub2)
                            if (t2ArrTime == null) continue

                            for (t3 in hub2ToDest.trainServices) {
                                val t3DepTime = getBestTime(t3.scheduledDepartureTime, t3.estimatedDepartureTime)
                                if (t3DepTime == null) continue

                                if (hasValidTransferTime(t2ArrTime, t3DepTime, 5, 120)) {
                                    val t3ArrTime = getPreciseArrivalTime(t3.serviceID, destCrs)
                                    if (t3ArrTime == null) continue

                                    val t1Dest =
                                        if (t1.destination != null && !t1.destination.isEmpty()) t1.destination.get(
                                            0
                                        ).locationName else hub1
                                    val t2Dest =
                                        if (t2.destination != null && !t2.destination.isEmpty()) t2.destination.get(
                                            0
                                        ).locationName else hub2
                                    val t3Dest =
                                        if (t3.destination != null && !t3.destination.isEmpty()) t3.destination.get(
                                            0
                                        ).locationName else destCrs

                                    val stops1 =
                                        extractIntermediateStops(t1.serviceID, originCrs, hub1)
                                    val stops2 = extractIntermediateStops(t2.serviceID, hub1, hub2)
                                    val stops3 =
                                        extractIntermediateStops(t3.serviceID, hub2, destCrs)

                                    val legs: MutableList<JourneyLeg> = ArrayList<JourneyLeg>()
                                    legs.add(
                                        JourneyLeg(
                                            originCrs,
                                            getStationName(originCrs),
                                            hub1,
                                            getStationName(hub1),
                                            t1Dest,
                                            t1.scheduledDepartureTime,
                                            t1ArrTime,
                                            t1.operatorName,
                                            t1.serviceID,
                                            "TRAIN",
                                            stops1,
                                            getLiveLocationStatus(t1.serviceID),
                                            t1.estimatedDepartureTime,
                                            null,
                                            null
                                        )
                                    )
                                    legs.add(
                                        JourneyLeg(
                                            hub1,
                                            getStationName(hub1),
                                            hub2,
                                            getStationName(hub2),
                                            t2Dest,
                                            t2.scheduledDepartureTime,
                                            t2ArrTime,
                                            t2.operatorName,
                                            t2.serviceID,
                                            "TRAIN",
                                            stops2,
                                            getLiveLocationStatus(t2.serviceID),
                                            t2.estimatedDepartureTime,
                                            null,
                                            null
                                        )
                                    )
                                    legs.add(
                                        JourneyLeg(
                                            hub2,
                                            getStationName(hub2),
                                            destCrs,
                                            getStationName(destCrs),
                                            t3Dest,
                                            t3.scheduledDepartureTime,
                                            t3ArrTime,
                                            t3.operatorName,
                                            t3.serviceID,
                                            "TRAIN",
                                            stops3,
                                            getLiveLocationStatus(t3.serviceID),
                                            t3.estimatedDepartureTime,
                                            null,
                                            null
                                        )
                                    )

                                    if (isLogicalJourney(legs)) {
                                        results.add(JourneyOption(legs))
                                        graphManager.addConnection(originCrs, hub1)
                                        graphManager.addConnection(hub1, hub2)
                                        graphManager.addConnection(hub2, destCrs)
                                        if (results.size >= 10) return
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findCrossCity(
        originCrs: String?,
        destCrs: String,
        results: MutableList<JourneyOption?>
    ) {
        val allOrigin = getBoard(originCrs, 20, null)
        if (allOrigin == null || allOrigin.trainServices == null) return

        val terminalsReached: MutableSet<String> = HashSet<String>()

        for (t1 in allOrigin.trainServices) {
            val destCode =
                if (t1.destination != null && !t1.destination.isEmpty()) t1.destination.get(0).crs else null
            if (destCode != null && !CityTransferManager.getTransfersFrom(destCode)?.isEmpty()!!) {
                terminalsReached.add(destCode)
            }
        }

        for (terminal1 in terminalsReached) {
            val possibleTransfers = CityTransferManager.getTransfersFrom(terminal1)

            for (transfer in possibleTransfers?.entries!!) {
                val terminal2 = transfer.key
                val transferInfo: TransferInfo = transfer.value!!

                val term2Board = getBoard(terminal2, 30, destCrs)
                if (term2Board == null || term2Board.trainServices == null) continue

                for (t1 in allOrigin.trainServices) {
                    if (getBestTime(t1.scheduledDepartureTime, t1.estimatedDepartureTime) == null) continue
                    if (getPreciseArrivalTime(t1.serviceID, destCrs) != null) continue

                    val t1ArrTime = getPreciseArrivalTime(t1.serviceID, terminal1)
                    if (t1ArrTime == null) continue

                    val transferStartTime = addMinutesToTime(t1ArrTime, 3)

                    val arrivedAtTerm2Time =
                        addMinutesToTime(transferStartTime, transferInfo.timeMins)

                    for (t2 in term2Board.trainServices) {
                        val t2DepTime = getBestTime(t2.scheduledDepartureTime, t2.estimatedDepartureTime)
                        if (t2DepTime == null) continue

                        if (hasValidTransferTime(arrivedAtTerm2Time, t2DepTime, 3, 120)) {
                            val t2ArrTime = getPreciseArrivalTime(t2.serviceID, destCrs)
                            if (t2ArrTime == null) continue

                            val t1TrainDest =
                                if (t1.destination != null && !t1.destination.isEmpty()) t1.destination.get(
                                    0
                                ).locationName else terminal1
                            val t2TrainDest =
                                if (t2.destination != null && !t2.destination.isEmpty()) t2.destination.get(
                                    0
                                ).locationName else destCrs

                            val stops1 =
                                extractIntermediateStops(t1.serviceID, originCrs, terminal1)
                            val stops2 = extractIntermediateStops(t2.serviceID, terminal2, destCrs)

                            val legs: MutableList<JourneyLeg> = ArrayList<JourneyLeg>()
                            legs.add(
                                JourneyLeg(
                                    originCrs,
                                    getStationName(originCrs),
                                    terminal1,
                                    getStationName(terminal1),
                                    t1TrainDest,
                                    t1.scheduledDepartureTime,
                                    t1ArrTime,
                                    t1.operatorName,
                                    t1.serviceID,
                                    "TRAIN",
                                    stops1,
                                    getLiveLocationStatus(t1.serviceID),
                                    t1.estimatedDepartureTime,
                                    null,
                                    null
                                )
                            )
                            legs.add(
                                JourneyLeg(
                                    terminal1,
                                    getStationName(terminal1),
                                    terminal2,
                                    getStationName(terminal2),
                                    getStationName(terminal2),
                                    transferStartTime,
                                    arrivedAtTerm2Time,
                                    transferInfo.operatorName,
                                    transferInfo.serviceId,
                                    "TRANSFER",
                                    ArrayList<JourneyLeg.Stop?>(),
                                    null,
                                    null,
                                    null,
                                    null
                                )
                            )

                            legs.add(
                                JourneyLeg(
                                    terminal2,
                                    getStationName(terminal2),
                                    destCrs,
                                    getStationName(destCrs),
                                    t2TrainDest,
                                    t2.scheduledDepartureTime,
                                    t2ArrTime,
                                    t2.operatorName,
                                    t2.serviceID,
                                    "TRAIN",
                                    stops2,
                                    getLiveLocationStatus(t2.serviceID),
                                    t2.estimatedDepartureTime,
                                    null,
                                    null
                                )
                            )

                            if (isLogicalJourney(legs)) {
                                results.add(JourneyOption(legs))
                                if (results.size >= 10) return
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isLogicalJourney(legs: MutableList<JourneyLeg>): Boolean {
        val visitedStations: MutableSet<String?> = HashSet<String?>()
        val usedServices: MutableSet<String?> = HashSet<String?>()
        var lastStation: String? = null

        for (leg in legs) {
            if ("TRANSFER" == leg.transportMode || "TUBE/WALK" == leg.transportMode) {
                lastStation = leg.toStationName
                continue
            }

            if (leg.serviceId != null) {
                if (!usedServices.add(leg.serviceId)) return false
            }

            if (lastStation == null || !lastStation.equals(
                    leg.fromStationName,
                    ignoreCase = true
                )
            ) {
                if (!visitedStations.add(leg.fromStationName.lowercase(Locale.getDefault()))) return false
            }

            for (stop in leg.intermediateStops) {
                if (!visitedStations.add(stop.stationName.lowercase(Locale.getDefault()))) return false
            }

            if (!visitedStations.add(leg.toStationName.lowercase(Locale.getDefault()))) return false
            lastStation = leg.toStationName
        }

        return true
    }

    private fun getBoard(crs: String?, rows: Int, filter: String?): StationDepartureResponse? {
        val cacheKey = crs + "_" + rows + "_" + (if (filter != null) filter else "ALL")
        if (boardCache!!.containsKey(cacheKey)) return boardCache!!.get(cacheKey)

        try {
            val filterType = if (filter != null) "to" else null
            val res = api.getStationDepartures(crs, rows, filter, filterType, 0, 120).execute()
            if (res.isSuccessful() && res.body() != null) {
                boardCache!!.put(cacheKey, res.body())
                return res.body()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cache miss/fail for board: " + cacheKey)
        }
        return null
    }

    private fun getPreciseArrivalTime(serviceId: String?, targetCrs: String): String? {
        var details: ServiceDetailsResponse? = null

        if (detailCache!!.containsKey(serviceId)) {
            details = detailCache!!.get(serviceId)
        } else {
            try {
                val res = api.getServiceDetails(serviceId).execute()
                if (res.isSuccessful() && res.body() != null) {
                    details = res.body()
                    detailCache!!.put(serviceId, details)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed details for " + serviceId)
            }
        }

        if (details != null) {
            if (details.subsequentCallingPoints != null && !details.subsequentCallingPoints.isEmpty()) {
                for (cp in details.subsequentCallingPoints.get(0).callingPoint) {
                    val cpCrs =
                        if (cp.crs != null) cp.crs else StationHelper.getCrsFromName(cp.locationName)
                    if (targetCrs.equals(cpCrs, ignoreCase = true)) {
                        return getBestTime(cp.scheduledTime, cp.estimatedTime)
                    }
                }
            }
            val destCrs =
                if (details.crs != null) details.crs else StationHelper.getCrsFromName(details.locationName)
            if (targetCrs.equals(destCrs, ignoreCase = true)) {
                return getBestTime(details.scheduledArrivalTime, details.estimatedArrivalTime)
            }
        }
        return null
    }

    private fun extractIntermediateStops(
        serviceId: String?,
        fromCrs: String?,
        toCrs: String?
    ): MutableList<JourneyLeg.Stop?> {
        val stops: MutableList<JourneyLeg.Stop?> = ArrayList<JourneyLeg.Stop?>()
        val details = detailCache!!.get(serviceId)
        if (details == null) return stops

        val allPoints: MutableList<TimelinePoint> = ArrayList<TimelinePoint>()

        if (details.previousCallingPoints != null && !details.previousCallingPoints.isEmpty()) {
            allPoints.addAll(details.previousCallingPoints.get(0).callingPoint)
        }

        val currentStation = TimelinePoint()
        currentStation.locationName = details.locationName
        currentStation.crs =
            if (details.crs != null) details.crs else StationHelper.getCrsFromName(details.locationName)
        currentStation.scheduledTime = if (details.scheduledDepartureTime != null) details.scheduledDepartureTime else details.scheduledArrivalTime
        currentStation.estimatedTime = if (details.estimatedDepartureTime != null) details.estimatedDepartureTime else details.estimatedArrivalTime
        allPoints.add(currentStation)

        if (details.subsequentCallingPoints != null && !details.subsequentCallingPoints.isEmpty()) {
            allPoints.addAll(details.subsequentCallingPoints.get(0).callingPoint)
        }

        var isRecording = false
        for (cp in allPoints) {
            val cpCrs =
                if (cp.crs != null) cp.crs else StationHelper.getCrsFromName(cp.locationName)
            if (cpCrs == null) continue

            if (cpCrs.equals(toCrs, ignoreCase = true)) {
                break
            }

            if (isRecording) {
                var time = getBestTime(cp.scheduledTime, cp.estimatedTime)
                if (time == null) time = "Cancelled"
                stops.add(JourneyLeg.Stop(cp.locationName, time))
            }

            if (cpCrs.equals(fromCrs, ignoreCase = true)) {
                isRecording = true
            }
        }
        return stops
    }

    private fun getLiveLocationStatus(serviceId: String?): String? {
        val details = detailCache!!.get(serviceId)
        if (details != null) {
            return getLiveLocation(details)
        }
        return null
    }

    private fun getPrioritizedHubs(
        services: MutableList<TrainService>?,
        origin: String?
    ): MutableList<String> {
        val prioritizedHubs: MutableList<String> = ArrayList<String>()
        val seen: MutableSet<String?> = HashSet<String?>()

        for (knownHub in graphManager.getConnections(origin)!!) {
            if (seen.add(knownHub)) knownHub?.let { prioritizedHubs.add(it) }
        }

        if (services != null) {
            for (service in services) {
                if (service.destination != null && !service.destination.isEmpty()) {
                    val destCrsCode = service.destination.get(0).crs
                    if (destCrsCode != null && seen.add(destCrsCode)) {
                        prioritizedHubs.add(destCrsCode)
                    }
                }
            }
        }
        return prioritizedHubs
    }

    private fun hasValidTransferTime(
        arrival: String?,
        departure: String?,
        minMins: Int,
        maxMins: Int
    ): Boolean {
        if (arrival == null || departure == null || !arrival.contains(":") || !departure.contains(":")) return false
        try {
            val arrParts: Array<String?> =
                arrival.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val depParts: Array<String?> =
                departure.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            val arrMins =
                arrParts[0]!!.trim { it <= ' ' }.toInt() * 60 + arrParts[1]!!.trim { it <= ' ' }
                    .toInt()
            val depMins =
                depParts[0]!!.trim { it <= ' ' }.toInt() * 60 + depParts[1]!!.trim { it <= ' ' }
                    .toInt()

            var diff = depMins - arrMins
            if (diff < 0) diff += 24 * 60

            return diff >= minMins && diff <= maxMins
        } catch (e: Exception) {
            return false
        }
    }

    private fun addMinutesToTime(time: String?, minsToAdd: Int): String {
        if (time == null || !time.contains(":")) return "00:00"
        try {
            val parts: Array<String?> =
                time.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val mins = parts[0]!!.trim { it <= ' ' }.toInt() * 60 + parts[1]!!.trim { it <= ' ' }
                .toInt() + minsToAdd
            val h = (mins / 60) % 24
            val m = mins % 60
            return String.format("%02d:%02d", h, m)
        } catch (e: Exception) {
            return time
        }
    }

    fun shutdown() {
        executor.shutdown()
    }

    private fun getStationName(crs: String?): String? {
        if (crs == null) return ""
        val upperCrs = crs.uppercase(Locale.getDefault())

        val directName = StationHelper.getNameFromCode(upperCrs)
        if (directName != null && !directName.isEmpty()) {
            return directName
        }
        if (crsToNameMap == null) {
            crsToNameMap = HashMap<String?, String?>()
            val allNames = StationHelper.getStationNames()
            if (allNames != null) {
                for (name in allNames) {
                    val code = StationHelper.getCrsFromName(name)
                    if (code != null) {
                        crsToNameMap!!.put(code.uppercase(Locale.getDefault()), name)
                    }
                }
            }
        }
        return crsToNameMap!!.getOrDefault(upperCrs, upperCrs)
    }

    companion object {
        private const val TAG = "JourneyPlanner"
        private var crsToNameMap: MutableMap<String?, String?>? = null
    }
}