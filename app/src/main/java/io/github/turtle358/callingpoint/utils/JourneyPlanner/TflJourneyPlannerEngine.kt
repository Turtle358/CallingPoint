package io.github.turtle358.callingpoint.utils.JourneyPlanner

import android.os.Handler
import android.os.Looper
import com.google.gson.JsonObject
import io.github.turtle358.callingpoint.api.APIClient
import io.github.turtle358.callingpoint.api.NationalRailAPI
import io.github.turtle358.callingpoint.api.TflAPI
import io.github.turtle358.callingpoint.models.JourneyLeg
import io.github.turtle358.callingpoint.models.JourneyOption
import io.github.turtle358.callingpoint.utils.JourneyPlanner.JourneyPlannerEngine.JourneyCallback
import io.github.turtle358.callingpoint.utils.StationHelper
import io.github.turtle358.callingpoint.utils.TOCBrandHelper
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TflJourneyPlannerEngine {
    private val tflApi: TflAPI
    private val nationalRailApi: NationalRailAPI
    private val mainHandler: Handler
    private val executor: ExecutorService?

    init {
        this.tflApi = APIClient.getClient().create(TflAPI::class.java)
        this.nationalRailApi =
            APIClient.getClient().create(NationalRailAPI::class.java)
        this.mainHandler = Handler(Looper.getMainLooper())
        this.executor = Executors.newSingleThreadExecutor()
    }

    fun findRoutes(
        fromNaptan: String?,
        toNaptan: String?,
        date: String?,
        time: String?,
        timeIs: String?,
        callback: JourneyCallback
    ) {
        mainHandler.post( { callback.onStatusUpdate("Searching TfL network...") })

        tflApi.getJourneyResults(fromNaptan, toNaptan, false, date, time, timeIs)
            .enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    if (response.isSuccessful && response.body() != null) {
                        executor!!.execute( {
                            try {
                                val options = parseTflResponse(response.body()!!)

                                if (isToday(date)) {
                                    mainHandler.post { callback.onStatusUpdate("Matching National Rail services...") }
                                    enrichNationalRailServices(options)
                                }

                                mainHandler.post {
                                    if (options.isEmpty()) {
                                        callback.onError("No TfL routes found.")
                                    } else {
                                        callback.onJourneysFound(options as MutableList<JourneyOption?>?)
                                    }
                                }
                            } catch (_: Exception) {
                                mainHandler.post { callback.onError("Error processing journey data.") }
                            }
                        })
                    } else {
                        mainHandler.post { callback.onError("TfL Planner unavailable.") }
                    }
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    mainHandler.post { callback.onError("Network error contacting TfL.") }
                }
            })
    }

    private fun isToday(dateStr: String?): Boolean {
        if (dateStr == null || dateStr.trim { it <= ' ' }.isEmpty()) {
            return true
        }
        try {
            val sdf = SimpleDateFormat("yyyyMMdd", Locale.UK)
            val todayStr = sdf.format(Date())
            return todayStr == dateStr.trim { it <= ' ' }
        } catch (_: Exception) {
            return true
        }
    }

    private fun enrichNationalRailServices(options: MutableList<JourneyOption>) {
        for (option in options) {
            if (option.legs == null) continue
            for (leg in option.legs) {
                if ("TRAIN" != leg.transportMode) continue

                val fromCrs = resolveCrs(leg.fromStationName)
                val toCrs = resolveCrs(leg.toStationName)

                if (fromCrs != null && toCrs != null) {
                    try {
                        val timeOffset = calculateTimeOffset(leg.departureTime)

                        val res = nationalRailApi
                            .getStationDepartures(fromCrs, 40, toCrs, "to", timeOffset, 120)
                            .execute()

                        if (res.isSuccessful() && res.body() != null && res.body()!!.trainServices != null) {
                            for (service in res.body()!!.trainServices) {
                                if (service.std != null && isTimeMatching(
                                        service.std,
                                        leg.departureTime,
                                        2
                                    )
                                ) {
                                    leg.serviceId = service.serviceID

                                    if (service.operatorName != null && !service.operatorName.isEmpty()) {
                                        val matchedToc =
                                            TOCBrandHelper.getTocByName(service.operatorName)
                                        if (matchedToc != null) {
                                            leg.operatorName = matchedToc.name
                                        }
                                    }
                                    break
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun resolveCrs(stationName: String?): String? {
        if (stationName == null) return null
        var crs = StationHelper.getCrsFromName(stationName)
        if (crs == null && stationName.startsWith("London ")) {
            crs = StationHelper.getCrsFromName(stationName.replace("London ", ""))
        }
        if (crs == null && !stationName.startsWith("London ")) {
            crs = StationHelper.getCrsFromName("London " + stationName)
        }
        return crs
    }

    private fun calculateTimeOffset(departureTimeStr: String?): Int {
        if (departureTimeStr == null || !departureTimeStr.contains(":")) return 0
        try {
            val parts =
                departureTimeStr.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val depMins =
                parts[0].trim { it <= ' ' }.toInt() * 60 + parts[1].trim { it <= ' ' }.toInt()

            val now = Calendar.getInstance()
            val currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            val diff = depMins - currentMins

            val offset = diff - 10
            return max(-120, min(120, offset))
        } catch (_: Exception) {
            return 0
        }
    }

    private fun isTimeMatching(time1: String, time2: String, toleranceMins: Int): Boolean {
        try {
            val p1 = time1.trim { it <= ' ' }.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            val p2 = time2.trim { it <= ' ' }.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()

            val m1 = p1[0].trim { it <= ' ' }.toInt() * 60 + p1[1].trim { it <= ' ' }.toInt()
            val m2 = p2[0].trim { it <= ' ' }.toInt() * 60 + p2[1].trim { it <= ' ' }.toInt()

            return abs(m1 - m2) <= toleranceMins
        } catch (e: Exception) {
            return time1.trim { it <= ' ' }.equals(time2.trim { it <= ' ' }, ignoreCase = true)
        }
    }

    private fun parseTflResponse(root: JsonObject): MutableList<JourneyOption> {
        val options: MutableList<JourneyOption> = ArrayList<JourneyOption>()
        if (!root.has("journeys")) return options

        val journeys = root.getAsJsonArray("journeys")
        for (jElem in journeys) {
            val jObj = jElem.getAsJsonObject()
            if (!jObj.has("legs")) continue

            var journeyStartDate: String? = null
            if (jObj.has("startDateTime")) {
                journeyStartDate = jObj.get("startDateTime").getAsString()
            }

            val legsData = jObj.getAsJsonArray("legs")
            val parsedLegs: MutableList<JourneyLeg?> = ArrayList<JourneyLeg?>()

            for (legElem in legsData) {
                val legObj = legElem.getAsJsonObject()

                val depPoint = legObj.getAsJsonObject("departurePoint")
                val arrPoint = legObj.getAsJsonObject("arrivalPoint")
                val modeInfo = legObj.getAsJsonObject("mode")

                var fromName = if (depPoint.has("commonName")) depPoint.get("commonName")
                    .getAsString() else "Unknown"
                var toName = if (arrPoint.has("commonName")) arrPoint.get("commonName")
                    .getAsString() else "Unknown"

                val modeId = if (modeInfo.has("id")) modeInfo.get("id").getAsString() else "unknown"
                var specificLineId = modeId
                val depTimeStr = if (legObj.has("departureTime")) legObj.get("departureTime")
                    .getAsString() else ""
                val arrTimeStr =
                    if (legObj.has("arrivalTime")) legObj.get("arrivalTime").getAsString() else ""
                val depTime = extractTime(depTimeStr)
                val arrTime = extractTime(arrTimeStr)

                var dest: String? = toName

                if (legObj.has("routeOptions")) {
                    val routeOptions = legObj.getAsJsonArray("routeOptions")
                    if (routeOptions.size() > 0) {
                        val firstOption = routeOptions.get(0).getAsJsonObject()

                        if (firstOption.has("directions") && firstOption.getAsJsonArray("directions")
                                .size() > 0
                        ) {
                            dest = firstOption.getAsJsonArray("directions").get(0).getAsString()
                        }

                        if (firstOption.has("lineIdentifier")) {
                            val lineIdentifier = firstOption.getAsJsonObject("lineIdentifier")
                            if (lineIdentifier.has("id")) {
                                specificLineId = lineIdentifier.get("id").getAsString()
                            }
                        }
                    }
                }

                val operatorName: String?
                val transportMode: String?

                if ("bus".equals(modeId, ignoreCase = true)) {
                    transportMode = "BUS"
                    operatorName = "Bus " + specificLineId.uppercase(Locale.getDefault())
                } else if ("walking".equals(modeId, ignoreCase = true)) {
                    transportMode = "TUBE/WALK"
                    operatorName = "Walking"
                } else if ("tube".equals(modeId, ignoreCase = true) || "dlr".equals(
                        modeId,
                        ignoreCase = true
                    )
                    || "overground".equals(modeId, ignoreCase = true) || "tram".equals(
                        modeId,
                        ignoreCase = true
                    )
                    || "elizabeth-line".equals(modeId, ignoreCase = true) || "cable-car".equals(
                        modeId,
                        ignoreCase = true
                    )
                ) {
                    transportMode = "TUBE"
                    operatorName = formatOperatorSlug(specificLineId) + " Line"
                } else {
                    transportMode = "TRAIN"
                    val tocCode = TOCBrandHelper.getTocCodeForTflId(specificLineId)
                    var toc = TOCBrandHelper.getTocByName(tocCode)
                    if (toc == null) toc = TOCBrandHelper.getTocByName(specificLineId)
                    operatorName = if (toc != null) toc.name else formatOperatorSlug(specificLineId)
                }

                fromName = fromName.replace(" Underground Station", "").replace(" Rail Station", "")
                toName = toName.replace(" Underground Station", "").replace(" Rail Station", "")

                val intermediateStops: MutableList<JourneyLeg.Stop?> = ArrayList<JourneyLeg.Stop?>()
                if (legObj.has("path")) {
                    val pathObj = legObj.getAsJsonObject("path")
                    if (pathObj.has("stopPoints")) {
                        val stopPointsArray = pathObj.getAsJsonArray("stopPoints")

                        for (stopElem in stopPointsArray) {
                            val stopPointObj = stopElem.getAsJsonObject()
                            var stopName = if (stopPointObj.has("name")) stopPointObj.get("name")
                                .getAsString() else "Unknown"
                            stopName = stopName.replace(" Underground Station", "")
                                .replace(" Rail Station", "")

                            if (!stopName.equals(fromName, ignoreCase = true) && !stopName.equals(
                                    toName,
                                    ignoreCase = true
                                )
                            ) {
                                intermediateStops.add(JourneyLeg.Stop(stopName, "--:--"))
                            }
                        }
                    }
                }

                val leg = JourneyLeg(
                    if (depPoint.has("naptanId")) depPoint.get("naptanId").getAsString() else "",
                    fromName,
                    if (arrPoint.has("naptanId")) arrPoint.get("naptanId").getAsString() else "",
                    toName,
                    dest,
                    depTime,
                    arrTime,
                    operatorName,
                    null,
                    transportMode,
                    intermediateStops,
                    null,
                    depTime
                )
                parsedLegs.add(leg)
            }
            if (!parsedLegs.isEmpty()) {
                val option = JourneyOption(parsedLegs)
                option.departureDate = journeyStartDate
                options.add(option)
            }
        }
        return options
    }

    private fun formatOperatorSlug(slug: String?): String {
        if (slug == null || slug.isEmpty()) return "National Rail"
        val words = slug.replace("-", " ").split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        val sb = StringBuilder()
        for (w in words) {
            if (w.length > 0) {
                sb.append(w.get(0).uppercaseChar())
                    .append(w.substring(1).lowercase(Locale.getDefault()))
                    .append(" ")
            }
        }
        return sb.toString().trim { it <= ' ' }
    }

    private fun extractTime(isoDate: String?): String {
        if (isoDate == null || isoDate.length < 16) return "--:--"
        try {
            return isoDate.substring(11, 16)
        } catch (e: Exception) {
            return "--:--"
        }
    }

    fun shutdown() {
        if (executor != null) {
            executor.shutdown()
        }
    }
}