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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var activeCall: Call<JsonObject?>? = null
    @Volatile private var generation = 0
    @Volatile private var closed = false

    fun cancel() {
        generation++
        activeCall?.cancel()
        activeCall = null
    }

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
        cancel()
        if (closed) return
        val version = generation
        val call = tflApi.getJourneyResults(fromNaptan, toNaptan, nationalSearch, date, time, timeIs, journeyPreference, modes, accessibilityPreference)
        activeCall = call
        call.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    if (closed || version != generation) return
                    if (response.isSuccessful && response.body() != null) {
                        executor.execute {
                            try {
                                val options = parseTflResponse(response.body()!!)

                                mainHandler.post {
                                    if (closed || version != generation) return@post
                                    if (options.isEmpty()) callback.onError("No TfL routes found.")
                                    else callback.onJourneysFound(ArrayList<JourneyOption?>(options))
                                }
                            } catch (e: Exception) {
                                mainHandler.post { if (!closed && version == generation) callback.onError("Error processing journey data.") }
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
                        mainHandler.post { if (!closed && version == generation) callback.onError(detail) }
                    }
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    mainHandler.post { if (!closed && version == generation) callback.onError("Network error contacting TfL.") }
                }
            })
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
                ).apply {
                    fromLatitude = depPoint.get("lat")?.takeUnless { it.isJsonNull }?.asDouble
                    fromLongitude = depPoint.get("lon")?.takeUnless { it.isJsonNull }?.asDouble
                    toLatitude = arrPoint.get("lat")?.takeUnless { it.isJsonNull }?.asDouble
                    toLongitude = arrPoint.get("lon")?.takeUnless { it.isJsonNull }?.asDouble
                }
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

    fun shutdown() {
        closed = true
        cancel()
        executor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }
}