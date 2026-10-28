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
    private val tflApi: TflAPI = APIClient.getClient().create(TflAPI::class.java)
    private val nationalRailApi: NationalRailAPI = APIClient.getClient().create(NationalRailAPI::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun findRoutes(
        fromNaptan: String?, toNaptan: String?, date: String?, time: String?, timeIs: String?,
        journeyPreference: String?, modes: String?, accessibilityPreference: String?, callback: JourneyCallback
    ) = findRoutes(fromNaptan, toNaptan, date, time, timeIs, journeyPreference,
        modes, accessibilityPreference, false, callback)

    fun findRoutes(
        fromNaptan: String?, toNaptan: String?, date: String?, time: String?, timeIs: String?,
        journeyPreference: String?, modes: String?, accessibilityPreference: String?,
        nationalSearch: Boolean, callback: JourneyCallback
    ) {
        tflApi.getJourneyResults(fromNaptan, toNaptan, nationalSearch, date, time, timeIs, journeyPreference, modes, accessibilityPreference)
            .enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    if (response.isSuccessful && response.body() != null) {
                        executor.execute {
                            try {
                                val options = parseTflResponse(response.body()!!)
                                if (isToday(date)) enrichNationalRailServices(options)

                                mainHandler.post {
                                    if (options.isEmpty()) callback.onError("No TfL routes found.")
                                    else callback.onJourneysFound(options as MutableList<JourneyOption?>)
                                }
                            } catch (e: Exception) {
                                mainHandler.post { callback.onError("Error processing journey data.") }
                            }
                        }
                    } else {
                        val detail = when (response.code()) {
                            300 -> "TfL couldn't identify one of the locations. Please select a specific suggestion."
                            400 -> "TfL rejected the journey locations or routing options (400)."
                            401, 403 -> "TfL API access was rejected (${response.code()}); check the proxy/API key."
                            404 -> "TfL couldn't plan a route here (404)."
                            429 -> "TfL request limit reached (429). Please try again later."
                            in 500..599 -> "TfL service is currently unavailable (${response.code()})."
                            else -> "TfL route request failed (HTTP ${response.code()})."
                        }
                        mainHandler.post { callback.onError(detail) }
                    }
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    mainHandler.post { callback.onError("Network error contacting TfL.") }
                }
            })
    }

    private fun isToday(dateStr: String?): Boolean {
        if (dateStr.isNullOrBlank()) return true
        return try {
            SimpleDateFormat("yyyyMMdd", Locale.UK).format(Date()) == dateStr.trim()
        } catch (e: Exception) { true }
    }

    private fun enrichNationalRailServices(options: MutableList<JourneyOption>) {
        options.flatMap { it.legs ?: emptyList() }
            .filter { it.transportMode == "TRAIN" }
            .forEach { leg ->
                val fromCrs = resolveCrs(leg.fromStationName) ?: return@forEach
                val toCrs = resolveCrs(leg.toStationName) ?: return@forEach

                try {
                    val timeOffset = calculateTimeOffset(leg.departureTime)
                    val res = nationalRailApi.getStationDepartures(fromCrs, 40, toCrs, "to", timeOffset, 120).execute()

                    val matchedService = res.body()?.trainServices?.find {
                        it.scheduledDepartureTime != null && isTimeMatching(it.scheduledDepartureTime, leg.departureTime, 2)
                    }

                    matchedService?.let { service ->
                        leg.serviceId = service.serviceID
                        service.operatorName?.let { op ->
                            leg.operatorName = TOCBrandHelper.getTocByName(op)?.name ?: op
                        }

                        if (leg.departurePlatform.isNullOrEmpty()) {
                            leg.departurePlatform = service.platform
                        }

                        if (leg.arrivalPlatform.isNullOrEmpty()) {
                            try {
                                val arrOffset = calculateTimeOffset(leg.arrivalTime) - 15
                                val arrRes = nationalRailApi.getStationDepartures(toCrs, 50, null, null, arrOffset, 120).execute()

                                val arrMatch = arrRes.body()?.trainServices?.find {
                                    it.serviceID == service.serviceID ||
                                            (it.operatorName == service.operatorName &&
                                                    (isTimeMatching(it.scheduledArrivalTime ?: "", leg.arrivalTime, 4) ||
                                                            isTimeMatching(it.scheduledDepartureTime ?: "", leg.arrivalTime, 4)))
                                }

                                arrMatch?.platform?.let {
                                    leg.arrivalPlatform = it
                                }
                            } catch (e: Exception) {}
                        }
                    }
                } catch (e: Exception) {}
            }
    }

    private fun resolveCrs(stationName: String?): String? {
        if (stationName == null) return null
        return StationHelper.getCrsFromName(stationName)
            ?: StationHelper.getCrsFromName(stationName.removePrefix("London "))
            ?: StationHelper.getCrsFromName("London $stationName")
    }

    private fun calculateTimeOffset(timeStr: String?): Int {
        if (timeStr == null || !timeStr.contains(":")) return 0
        return try {
            val (h, m) = timeStr.split(":").map { it.trim().toInt() }
            val now = Calendar.getInstance()
            val currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val offset = (h * 60 + m) - currentMins - 10
            max(-120, min(120, offset))
        } catch (e: Exception) { 0 }
    }

    private fun isTimeMatching(time1: String, time2: String, toleranceMins: Int): Boolean {
        return try {
            val (h1, m1) = time1.split(":").map { it.trim().toInt() }
            val (h2, m2) = time2.split(":").map { it.trim().toInt() }
            abs((h1 * 60 + m1) - (h2 * 60 + m2)) <= toleranceMins
        } catch (e: Exception) {
            time1.trim().equals(time2.trim(), ignoreCase = true)
        }
    }

    private fun parseTflResponse(root: JsonObject): MutableList<JourneyOption> {
        val options = mutableListOf<JourneyOption>()
        val journeys = root.getAsJsonArray("journeys") ?: return options

        for (jElem in journeys) {
            val jObj = jElem.asJsonObject
            val legsData = jObj.getAsJsonArray("legs") ?: continue
            val journeyStartDate = jObj.get("startDateTime")?.asString

            val parsedLegs = legsData.map { legElem ->
                val legObj = legElem.asJsonObject
                val depPoint = legObj.getAsJsonObject("departurePoint")
                val arrPoint = legObj.getAsJsonObject("arrivalPoint")
                val modeInfo = legObj.getAsJsonObject("mode")

                var fromName = depPoint.get("commonName")?.asString?.removeSuffix(" Underground Station")?.removeSuffix(" Rail Station") ?: "Unknown"
                var toName = arrPoint.get("commonName")?.asString?.removeSuffix(" Underground Station")?.removeSuffix(" Rail Station") ?: "Unknown"

                val depPlatform = depPoint.get("platformName")?.asString?.replace("(?i)Platform |point ".toRegex(), "")?.trim() ?: ""
                val arrPlatform = arrPoint.get("platformName")?.asString?.replace("(?i)Platform |point ".toRegex(), "")?.trim() ?: ""

                val modeId = modeInfo.get("id")?.asString ?: "unknown"
                var specificLineId = modeId
                var dest: String? = toName

                legObj.getAsJsonArray("routeOptions")?.firstOrNull()?.asJsonObject?.let { firstOption ->
                    dest = firstOption.getAsJsonArray("directions")?.firstOrNull()?.asString ?: dest
                    specificLineId = firstOption.getAsJsonObject("lineIdentifier")?.get("id")?.asString ?: specificLineId
                }

                val transportMode = when (modeId.lowercase()) {
                    "bus" -> "BUS"
                    "walking" -> "TUBE/WALK"
                    "tube", "dlr", "overground", "tram", "elizabeth-line", "cable-car" -> "TUBE"
                    else -> "TRAIN"
                }

                val operatorName = when (transportMode) {
                    "BUS" -> "Bus ${specificLineId.uppercase(Locale.getDefault())}"
                    "TUBE/WALK" -> "Walking"
                    "TUBE" -> "${formatOperatorSlug(specificLineId)} Line"
                    else -> {
                        val tocCode = TOCBrandHelper.getTocCodeForTflId(specificLineId)
                        TOCBrandHelper.getTocByName(tocCode)?.name
                            ?: TOCBrandHelper.getTocByName(specificLineId)?.name
                            ?: formatOperatorSlug(specificLineId)
                    }
                }

                val intermediateStops = mutableListOf<JourneyLeg.Stop>()
                legObj.getAsJsonObject("path")?.getAsJsonArray("stopPoints")?.forEach { stopElem ->
                    val stopName = stopElem.asJsonObject.get("name")?.asString?.removeSuffix(" Underground Station")?.removeSuffix(" Rail Station") ?: "Unknown"
                    if (!stopName.equals(fromName, ignoreCase = true) && !stopName.equals(toName, ignoreCase = true)) {
                        intermediateStops.add(JourneyLeg.Stop(stopName, "--:--"))
                    }
                }

                JourneyLeg(
                    depPoint.get("naptanId")?.asString ?: "", fromName,
                    arrPoint.get("naptanId")?.asString ?: "", toName, dest,
                    extractTime(legObj.get("departureTime")?.asString),
                    extractTime(legObj.get("arrivalTime")?.asString),
                    operatorName, null, transportMode, intermediateStops,
                    null, extractTime(legObj.get("departureTime")?.asString),
                    depPlatform, arrPlatform
                )
            }

            if (parsedLegs.isNotEmpty()) {
                options.add(JourneyOption(parsedLegs).apply { departureDate = journeyStartDate })
            }
        }
        return options
    }

    private fun formatOperatorSlug(slug: String?): String {
        if (slug.isNullOrEmpty()) return "National Rail"
        return slug.replace("-", " ").split("\\s+".toRegex())
            .joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() } }
    }

    private fun extractTime(isoDate: String?): String = isoDate?.takeIf { it.length >= 16 }?.substring(11, 16) ?: "--:--"

    fun shutdown() = executor.shutdown()
}