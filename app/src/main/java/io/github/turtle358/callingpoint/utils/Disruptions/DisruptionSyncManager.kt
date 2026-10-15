package io.github.turtle358.callingpoint.utils.Disruptions

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.google.gson.Gson
import io.github.turtle358.callingpoint.api.APIClient
import io.github.turtle358.callingpoint.api.NationalRailAPI
import io.github.turtle358.callingpoint.api.TflAPI
import io.github.turtle358.callingpoint.models.DisruptionResponce.ServiceIndicator
import io.github.turtle358.callingpoint.models.IncidentResponse
import io.github.turtle358.callingpoint.models.IncidentResponse.AffectedOperator
import io.github.turtle358.callingpoint.models.IncidentSearchRequest
import io.github.turtle358.callingpoint.utils.Disruptions.DisruptionSeverityManager.Severity.Companion.fromString
import io.github.turtle358.callingpoint.utils.Disruptions.DisruptionSeverityManager.classify
import io.github.turtle358.callingpoint.utils.Disruptions.DisruptionSeverityManager.isTflPlanned
import io.github.turtle358.callingpoint.utils.TOCBrandHelper
import io.github.turtle358.callingpoint.widget.AppWidgetProvider
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DisruptionSyncManager {
    @JvmStatic
    fun syncAsync(context: Context, callback: SyncCallback?) {
        Thread(Runnable {
            val success = syncSync(context, callback)
            if (!success && callback != null) {
                Handler(Looper.getMainLooper()).post(Runnable { callback.onFailure("Sync failed") })
            }
        }).start()
    }

    @JvmStatic
    fun syncSync(context: Context, callback: SyncCallback?): Boolean {
        val prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        TOCBrandHelper.init(context)

        val selectedTocs: String = prefs.getString("WIDGET_TRACKED_TOCS", "")!!
        val trackedSet: MutableSet<String?> = HashSet<String?>()
        if (!selectedTocs.trim { it <= ' ' }.isEmpty()) {
            for (s in selectedTocs.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()) {
                if (!s.trim { it <= ' ' }.isEmpty()) trackedSet.add(s.trim { it <= ' ' })
            }
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()

        cal.add(Calendar.DAY_OF_YEAR, 1)
        val today = sdf.format(cal.getTime())
        cal.add(Calendar.DAY_OF_YEAR, 2)
        val tomorrow = sdf.format(cal.getTime())
        val request = IncidentSearchRequest(today, tomorrow, ArrayList<String?>())
        val api = APIClient.getClient().create<NationalRailAPI>(NationalRailAPI::class.java)

        try {
            val response = api.searchIncidents(request).execute()

            if (response.isSuccessful() && response.body() != null) {
                val trackedMap: MutableMap<String?, ServiceIndicator> =
                    LinkedHashMap<String?, ServiceIndicator>()
                val othersMap: MutableMap<String?, ServiceIndicator> =
                    LinkedHashMap<String?, ServiceIndicator>()


                for (incident in response.body()!!) {
                    if (incident.affectedOperators == null || "Cleared".equals(
                            incident.status,
                            ignoreCase = true
                        )
                    ) continue

                    for (op in incident.affectedOperators) {
                        if ("LO".equals(op.tocCode, ignoreCase = true) || "XR".equals(
                                op.tocCode,
                                ignoreCase = true
                            )
                        ) {
                            continue
                        }

                        val severity = classify(
                            incident.summary,
                            incident.description
                        )

                        val targetMap: MutableMap<String?, ServiceIndicator>?
                        if (trackedSet.isEmpty() || trackedSet.contains(op.tocCode)) {
                            targetMap = trackedMap
                        } else {
                            targetMap = othersMap
                        }

                        if (!targetMap.containsKey(op.tocCode)) {
                            val si = ServiceIndicator()
                            si.tocCode = op.tocCode
                            si.tocName = TOCBrandHelper.getNameForCode(op.tocCode)
                            si.status = severity.statusString

                            if (severity == DisruptionSeverityManager.Severity.ENGINEERING_WORKS) {
                                si.plannedIncidents.add(incident)
                            } else {
                                si.activeIncidents.add(incident)
                            }
                            targetMap.put(op.tocCode, si)
                        } else {
                            val existing = targetMap.get(op.tocCode)
                            val existingSeverity = fromString(existing!!.status)

                            if (severity.priority > existingSeverity.priority) {
                                existing.status = severity.statusString
                            }

                            if (severity == DisruptionSeverityManager.Severity.ENGINEERING_WORKS) {
                                if (!existing.plannedIncidents.contains(incident)) {
                                    existing.plannedIncidents.add(incident)
                                }
                            } else {
                                if (!existing.activeIncidents.contains(incident)) {
                                    existing.activeIncidents.add(incident)
                                }
                            }
                        }
                    }
                }

                val tflApi = APIClient.getClient().create<TflAPI>(TflAPI::class.java)
                try {
                    val tflResponse =
                        tflApi.getLineStatus("tube,overground,dlr,elizabeth-line,tram").execute()

                    if (tflResponse.isSuccessful() && tflResponse.body() != null) {
                        for (line in tflResponse.body()!!) {
                            if (line.lineStatuses == null || line.lineStatuses.isEmpty()) continue

                            val status = line.lineStatuses.get(0)

                            if (status.statusSeverity == 10) continue

                            val opCode = TOCBrandHelper.getTocCodeForTflId(line.id)
                            val opName = TOCBrandHelper.getNameForCode(opCode)

                            val targetMap: MutableMap<String?, ServiceIndicator?> =
                                (if (trackedSet.isEmpty() || trackedSet.contains(opCode)) trackedMap else othersMap) as MutableMap<String?, ServiceIndicator?>

                            var si = targetMap.get(opCode)
                            if (si == null) {
                                si = ServiceIndicator()
                                si.tocCode = opCode
                                si.tocName = if (opName.isEmpty()) line.name else opName
                                si.status = status.statusSeverityDescription
                                targetMap.put(opCode, si)
                            } else {
                                si.status = status.statusSeverityDescription
                            }

                            val incident = IncidentResponse()
                            incident.summary = status.statusSeverityDescription
                            incident.description =
                                if (status.reason != null) status.reason else "No further details available."
                            incident.status = "Active"
                            incident.affectedOperators = ArrayList<AffectedOperator?>()

                            val op = AffectedOperator()
                            op.tocCode = opCode
                            incident.affectedOperators.add(op)

                            val isPlanned = isTflPlanned(status.statusSeverity)

                            if (isPlanned) {
                                si.plannedIncidents.add(incident)
                            } else {
                                si.activeIncidents.add(incident)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                var trackedLive: MutableList<ServiceIndicator> = ArrayList<ServiceIndicator>()
                var othersLive: MutableList<ServiceIndicator> = ArrayList<ServiceIndicator>()
                var plannedWorks: MutableList<ServiceIndicator> = ArrayList<ServiceIndicator>()

                for (si in trackedMap.values) {
                    if (si.activeIncidents.isEmpty() && !si.plannedIncidents.isEmpty()) {
                        plannedWorks.add(si)
                    } else {
                        trackedLive.add(si)
                    }
                }

                for (si in othersMap.values) {
                    if (si.activeIncidents.isEmpty() && !si.plannedIncidents.isEmpty()) {
                        plannedWorks.add(si)
                    } else {
                        othersLive.add(si)
                    }
                }

                trackedLive = groupAndFormatList(trackedLive)
                othersLive = groupAndFormatList(othersLive)
                plannedWorks = groupAndFormatList(plannedWorks)

                val cacheContainer = CachedDisruptionData(trackedLive, othersLive, plannedWorks)
                val appJson = Gson().toJson(cacheContainer)

                val widgetTracked: MutableList<ServiceIndicator?> = ArrayList<ServiceIndicator?>()
                for (si in trackedLive) {
                    if ("SEPARATOR" != si.tocCode) {
                        widgetTracked.add(si)
                    }
                }
                val widgetJson = Gson().toJson(widgetTracked)

                prefs.edit()
                    .putString("CACHED_TOC_DATA", widgetJson)
                    .putString("CACHED_TOC_DATA_ALL", appJson)
                    .putLong("LAST_SYNC_TIME", System.currentTimeMillis())
                    .apply()

                triggerWidgetUpdate(context)

                if (callback != null) {
                    val finalTrackedLive = trackedLive
                    val finalOthersLive = othersLive
                    val finalPlannedWorks = plannedWorks
                    Handler(Looper.getMainLooper()).post(Runnable {
                        callback.onSuccess(
                            finalTrackedLive,
                            finalOthersLive,
                            finalPlannedWorks
                        )
                    }
                    )
                }
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun isTflService(si: ServiceIndicator?): Boolean {
        if (si == null || si.tocName == null) return false
        val manager = TOCBrandHelper.getManagerForToc(si.tocName)
        return "tfl".equals(manager, ignoreCase = true) || "tfl-national".equals(
            manager,
            ignoreCase = true
        ) || "london buses".equals(manager, ignoreCase = true)
    }

    private fun groupAndFormatList(inputList: MutableList<ServiceIndicator>?): MutableList<ServiceIndicator> {
        if (inputList == null || inputList.isEmpty()) return ArrayList<ServiceIndicator>()

        val nationalRail: MutableList<ServiceIndicator?> = ArrayList<ServiceIndicator?>()
        val tflServices: MutableList<ServiceIndicator?> = ArrayList<ServiceIndicator?>()

        for (si in inputList) {
            if ("SEPARATOR" == si.tocCode) continue
            if (isTflService(si)) {
                tflServices.add(si)
            } else {
                nationalRail.add(si)
            }
        }

        nationalRail.sortWith(Comparator { a: ServiceIndicator?, b: ServiceIndicator? ->
                    a!!.tocName.compareTo(
                        b!!.tocName,
                        ignoreCase = true
                    )
                })
        tflServices.sortWith(Comparator { a: ServiceIndicator?, b: ServiceIndicator? ->
            a!!.tocName.compareTo(
                b!!.tocName,
                ignoreCase = true
            )
        })

        val result: MutableList<ServiceIndicator> = ArrayList<ServiceIndicator>()
        result.addAll(nationalRail as Collection<ServiceIndicator>)

        if (!nationalRail.isEmpty() && !tflServices.isEmpty()) {
            val sep = ServiceIndicator()
            sep.tocCode = "SEPARATOR"
            sep.tocName = "Transport for London"
            result.add(sep)
        }

        result.addAll(tflServices as Collection<ServiceIndicator>)
        return result
    }

    private fun triggerWidgetUpdate(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, AppWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

        val updateIntent = Intent(context, AppWidgetProvider::class.java)
        updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        context.sendBroadcast(updateIntent)
    }

    class CachedDisruptionData(
        @JvmField var tracked: MutableList<ServiceIndicator>?,
        @JvmField var others: MutableList<ServiceIndicator>?,
        @JvmField var engineering: MutableList<ServiceIndicator>?
    )

    interface SyncCallback {
        fun onSuccess(
            tracked: MutableList<ServiceIndicator>?,
            others: MutableList<ServiceIndicator>?,
            engineering: MutableList<ServiceIndicator>?
        )

        fun onFailure(error: String?)
    }
}