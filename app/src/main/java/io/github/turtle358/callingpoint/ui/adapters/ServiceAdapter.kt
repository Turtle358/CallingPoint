package io.github.turtle358.callingpoint.ui.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import io.github.turtle358.callingpoint.R
import io.github.turtle358.callingpoint.api.APIClient
import io.github.turtle358.callingpoint.api.NationalRailAPI
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse
import io.github.turtle358.callingpoint.models.StationDepartureResponse.TrainService
import io.github.turtle358.callingpoint.ui.activities.ServiceDetailsActivity
import io.github.turtle358.callingpoint.utils.ServiceDetailsCache
import io.github.turtle358.callingpoint.utils.ServiceDetailsCache.get
import io.github.turtle358.callingpoint.utils.ServiceDetailsCache.put
import io.github.turtle358.callingpoint.utils.StationHelper.areStationNamesEqual
import io.github.turtle358.callingpoint.utils.StationHelper.getNameFromCode
import io.github.turtle358.callingpoint.utils.TOCBrandHelper
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import androidx.core.graphics.toColorInt

class ServiceAdapter(private val context: Context, services: MutableList<TrainService>?) :
    RecyclerView.Adapter<RecyclerView.ViewHolder?>() {
    private val upcomingServices: MutableList<TrainService> = ArrayList<TrainService>()
    private val earlierServices: MutableList<TrainService> = ArrayList<TrainService>()
    private var isEarlierExpanded = false
    private var isArrivals = false

    private var userOriginStation = ""
    private var userArrivingStation: String? = ""
    private var lastPosition = -1

    init {
        processServices(services)
    }

    fun updateData(
        newServices: MutableList<TrainService>?,
        isArrivals: Boolean,
        departingCrs: String,
        arrivingCrs: String
    ) {
        this.isArrivals = isArrivals
        this.userOriginStation = getNameFromCode(departingCrs)
        this.userArrivingStation = getNameFromCode(arrivingCrs)
        this.lastPosition = -1
        processServices(newServices)
        notifyDataSetChanged()
    }

    private fun processServices(allServices: MutableList<TrainService>?) {
        upcomingServices.clear()
        earlierServices.clear()
        if (allServices == null) return

        for (service in allServices) {
            if (isServicePassed(service)) {
                earlierServices.add(service)
            } else {
                upcomingServices.add(service)
            }
        }
    }

    private fun isServicePassed(service: TrainService): Boolean {
        if ((service.actualDepartureTime != null && !service.actualDepartureTime.isEmpty()) || (service.actualArrivalTime != null && !service.actualArrivalTime.isEmpty())) {
            return true
        }

        val schTime = if (service.scheduledDepartureTime != null) service.scheduledDepartureTime else service.scheduledArrivalTime
        val estTime = if (service.estimatedDepartureTime != null) service.estimatedDepartureTime else service.estimatedArrivalTime

        if ("Cancelled".equals(estTime, ignoreCase = true) || "Delayed".equals(
                estTime,
                ignoreCase = true
            )
        ) {
            return false
        }

        val timeToUse = if (estTime != null && estTime.contains(":")) estTime else schTime
        if (timeToUse == null || !timeToUse.contains(":")) return false

        try {
            val parts: Array<String?> =
                timeToUse.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val targetMins =
                parts[0]!!.trim { it <= ' ' }.toInt() * 60 + parts[1]!!.trim { it <= ' ' }.toInt()

            val now = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"))
            val currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            var diffMins = targetMins - currentMins
            if (diffMins > 720) diffMins -= 1440
            if (diffMins < -720) diffMins += 1440

            return diffMins < -1
        } catch (_: Exception) {
            return false
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (!earlierServices.isEmpty() && position == 0) {
            return VIEW_TYPE_HEADER
        }
        return VIEW_TYPE_ITEM
    }

    override fun getItemCount(): Int {
        var count = upcomingServices.size
        if (!earlierServices.isEmpty()) {
            count += 1
            if (isEarlierExpanded) {
                count += earlierServices.size
            }
        }
        return count
    }

    private fun getServiceForPosition(position: Int): TrainService {
        if (earlierServices.isEmpty()) {
            return upcomingServices.get(position)
        }
        val itemIndex = position - 1
        if (isEarlierExpanded) {
            if (itemIndex < earlierServices.size) {
                return earlierServices.get(itemIndex)
            } else {
                return upcomingServices.get(itemIndex - earlierServices.size)
            }
        } else {
            return upcomingServices.get(itemIndex)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == VIEW_TYPE_HEADER) {
            val v =
                LayoutInflater.from(context).inflate(R.layout.item_earlier_header, parent, false)
            return HeaderViewHolder(v)
        } else {
            val v = LayoutInflater.from(context).inflate(R.layout.item_service, parent, false)
            return ItemViewHolder(v)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            val hHolder = holder

            if (isEarlierExpanded) {
                hHolder.tvEarlierTitle.setText("Hide previous departures")
                hHolder.ivExpandEarlier.setRotation(180f)
            } else {
                hHolder.tvEarlierTitle.setText("Show previous departures (" + earlierServices.size + ")")
                hHolder.ivExpandEarlier.setRotation(0f)
            }

            hHolder.itemView.setOnClickListener { _: View? ->
                isEarlierExpanded = !isEarlierExpanded
                hHolder.ivExpandEarlier.animate().rotation(if (isEarlierExpanded) 180f else 0f)
                    .setDuration(200).start()
                if (isEarlierExpanded) {
                    hHolder.tvEarlierTitle.setText("Hide previous departures")
                    notifyItemRangeInserted(1, earlierServices.size)
                } else {
                    hHolder.tvEarlierTitle.setText("Show previous departures (" + earlierServices.size + ")")
                    notifyItemRangeRemoved(1, earlierServices.size)
                }
            }
            return
        }

        val itemHolder = holder as ItemViewHolder
        val service = getServiceForPosition(position)

        val isEarlier = earlierServices.contains(service)

        val brandColour = TOCBrandHelper.getColourForToc(service.operatorName)
        val secondaryColour = TOCBrandHelper.getSecondaryColourForToc(service.operatorName)
        if (TOCBrandHelper.getManagerForToc(service.operatorName) == "gbr" || TOCBrandHelper.getManagerForToc(
                service.operatorName
            ) == "national"
        ) {
            itemHolder.ivModeIcon.setImageResource(R.drawable.ic_national_rail)
        } else {
            itemHolder.ivModeIcon.setImageResource(TOCBrandHelper.getIconForToc(service.operatorName))
        }

        val pillBg = GradientDrawable()
        pillBg.setColor(brandColour)
        pillBg.setCornerRadius(
            itemHolder.itemView.getContext().getResources().getDisplayMetrics().density * 8
        )
        itemHolder.tvOperatorName.setBackground(pillBg)
        itemHolder.tvOperatorName.setTextColor(secondaryColour)
        itemHolder.tvOperatorName.setText(service.operatorName)

        val dotBg = GradientDrawable()
        dotBg.setShape(GradientDrawable.OVAL)
        dotBg.setColor(brandColour)
        itemHolder.dotTop.setBackground(dotBg)
        itemHolder.dotBottom.setBackground(dotBg)
        itemHolder.timelineLine.setBackgroundColor(ColorUtils.setAlphaComponent(brandColour, 102))

        val schTime =
            if (service.scheduledDepartureTime != null) service.scheduledDepartureTime else (if (service.scheduledArrivalTime != null) service.scheduledArrivalTime else "--:--")
        val estTime =
            if (service.estimatedDepartureTime != null) service.estimatedDepartureTime else (if (service.estimatedArrivalTime != null) service.estimatedArrivalTime else "Unknown")
        val countdownText = calculateCountdown(schTime, estTime)

        val trueOriginName =
            if (service.origin != null && !service.origin.isEmpty()) service.origin.get(0).locationName else "Unknown"
        var trueOriginTime: String? = "--:--"
        if (service.previousCallingPoints != null && !service.previousCallingPoints.isEmpty()) {
            trueOriginTime = service.previousCallingPoints.get(0).callingPoint.get(0).scheduledTime
        } else if (areStationNamesEqual(trueOriginName, userOriginStation)) {
            trueOriginTime = schTime
        } else {
            val cachedDetails = get(service.serviceID)
            if (cachedDetails != null && cachedDetails.previousCallingPoints != null && !cachedDetails.previousCallingPoints.isEmpty()) {
                trueOriginTime =
                    cachedDetails.previousCallingPoints.get(0).callingPoint.get(0).scheduledTime
            } else {
                fetchOriginTimeAsync(service.serviceID, position)
            }
        }

        itemHolder.tvDepTime.setText(trueOriginTime)

        val rawDest =
            if (service.destination != null && !service.destination.isEmpty()) service.destination.get(
                0
            ).locationName else "Unknown"
        var trueDestName =
            rawDest.replace(" \\(.*?\\)".toRegex(), "").replace(" Underground Station", "")
                .replace(" Station", "").trim { it <= ' ' }
        if (trueDestName.contains(" via ")) trueDestName =
            trueDestName.substring(0, trueDestName.indexOf(" via "))

        var trueDestTime: String?
        var stopsCount = 0
        var durationStr = ""
        var filterDestTime: String? = null

        val hasFilter =
            (userArrivingStation != "Unknown") && userArrivingStation != null && !userArrivingStation!!.isEmpty()

        if (service.subsequentCallingPoints != null && !service.subsequentCallingPoints.isEmpty()) {
            val pts = service.subsequentCallingPoints.get(0).callingPoint
            trueDestTime = pts.get(pts.size - 1)!!.scheduledTime

            if (hasFilter && !isArrivals) {
                var destIndex = -1
                for (i in pts.indices) {
                    if (pts.get(i)!!.locationName.equals(userArrivingStation, ignoreCase = true)) {
                        destIndex = i
                        break
                    }
                }
                if (destIndex != -1) {
                    stopsCount = destIndex + 1
                    filterDestTime = pts.get(destIndex)!!.scheduledTime
                } else {
                    stopsCount = pts.size
                }
            } else {
                stopsCount = pts.size
            }

            durationStr = calculateDuration(schTime, filterDestTime ?: trueDestTime)
        } else {
            trueDestTime = schTime
        }

        if (isArrivals && service.previousCallingPoints != null && !service.previousCallingPoints.isEmpty()) {
            val pts = service.previousCallingPoints.get(0).callingPoint
            if (hasFilter) {
                var originIndex = -1
                for (i in pts.indices) {
                    if (pts.get(i)!!.locationName.equals(userArrivingStation, ignoreCase = true)) {
                        originIndex = i
                        break
                    }
                }
                if (originIndex != -1) {
                    stopsCount = pts.size - originIndex
                } else {
                    stopsCount = pts.size
                }
            } else {
                stopsCount = pts.size
            }
        }

        itemHolder.tvOriginStation.setText(trueOriginName)
        itemHolder.tvDepTime.setText(trueOriginTime)
        itemHolder.tvDestStation.setText(trueDestName)
        itemHolder.tvArrTime.setText(trueDestTime)

        itemHolder.tvCurrentStationTime.text = schTime
        itemHolder.tvPlatform.text = if (service.platform != null) "Plat\n${service.platform}" else "Plat\n-"

        itemHolder.tvCurrentStationTime.setPaintFlags(itemHolder.tvCurrentStationTime.getPaintFlags() and (Paint.STRIKE_THRU_TEXT_FLAG.inv()))
        itemHolder.tvCurrentStationTime.setTextColor(itemHolder.defaultSchColour)
        itemHolder.tvLiveStatus.setVisibility(View.VISIBLE)

        if (isEarlier) {
            itemHolder.tvCurrentStationTime.setText(countdownText)
            itemHolder.tvCurrentStationTime.setTextColor("#888888".toColorInt())
            itemHolder.tvLiveStatus.setVisibility(View.GONE)
            itemHolder.itemView.setAlpha(0.5f)
        } else {
            itemHolder.itemView.setAlpha(1.0f)

            if ("Cancelled".equals(estTime, ignoreCase = true)) {
                itemHolder.tvCurrentStationTime.setPaintFlags(itemHolder.tvCurrentStationTime.getPaintFlags() or Paint.STRIKE_THRU_TEXT_FLAG)
                itemHolder.tvLiveStatus.setText("Cancelled")
                itemHolder.tvLiveStatus.setTextColor("#DE350B".toColorInt())
            } else if ("Delayed".equals(estTime, ignoreCase = true)) {
                itemHolder.tvCurrentStationTime.setPaintFlags(itemHolder.tvCurrentStationTime.getPaintFlags() or Paint.STRIKE_THRU_TEXT_FLAG)
                itemHolder.tvLiveStatus.setText("Delayed")
                itemHolder.tvLiveStatus.setTextColor("#FF8C00".toColorInt())
            } else if ("On time".equals(estTime, ignoreCase = true)) {
                itemHolder.tvLiveStatus.setText("On time")
                itemHolder.tvLiveStatus.setTextColor("#00875A".toColorInt())
            } else {
                itemHolder.tvCurrentStationTime.setPaintFlags(itemHolder.tvCurrentStationTime.getPaintFlags() or Paint.STRIKE_THRU_TEXT_FLAG)
                itemHolder.tvLiveStatus.setText("Exp " + estTime)
                itemHolder.tvLiveStatus.setTextColor("#FF8C00".toColorInt())
            }
        }

        var callingText: String
        if (!isArrivals) {
            callingText =
                if (hasFilter) "Calling at " + userOriginStation + " and " + userArrivingStation else "Calling at " + userOriginStation
        } else {
            callingText =
                if (hasFilter) "Arriving at " + userOriginStation + " from " + userArrivingStation else "Arriving at " + userOriginStation
        }

        var detailsText = ""
        if (!durationStr.isEmpty()) detailsText += durationStr + " • "
        if (stopsCount > 0) {
            detailsText += stopsCount.toString() + (if (stopsCount == 1) " stop • " else " stops • ")
        }
        detailsText += callingText

        itemHolder.tvServiceDetails.setText(detailsText)

        setAnimation(itemHolder.itemView, position)

        val finalTrueDestName = trueDestName
        itemHolder.itemView.setOnClickListener { _: View? ->
            val intent = Intent(context, ServiceDetailsActivity::class.java)
            intent.putExtra("SERVICE_ID", service.serviceID)

            val uOrigin: String?
            val uDest: String?

            if (isArrivals) {
                uDest = userOriginStation
                uOrigin =
                    if (userArrivingStation != null && !userArrivingStation!!.isEmpty() && (userArrivingStation != "Unknown")) userArrivingStation else trueOriginName
            } else {
                uOrigin = userOriginStation
                uDest =
                    if (userArrivingStation != null && !userArrivingStation!!.isEmpty() && (userArrivingStation != "Unknown")) userArrivingStation else finalTrueDestName
            }

            intent.putExtra("USER_ORIGIN", uOrigin)
            intent.putExtra("USER_DESTINATION", uDest)
            context.startActivity(intent)
        }
    }

    private fun calculateCountdown(scheduledTime: String, estimatedTime: String?): String {
        if ("Cancelled".equals(estimatedTime, ignoreCase = true)) return "Cancelled"
        if ("Delayed".equals(estimatedTime, ignoreCase = true)) return "Delayed"

        var timeToUse: String? = scheduledTime
        if (estimatedTime != null && estimatedTime.contains(":") && !estimatedTime.equals(
                "On time",
                ignoreCase = true
            )
        ) {
            timeToUse = estimatedTime
        }

        if (timeToUse == null || !timeToUse.contains(":")) {
            return if (estimatedTime != null) estimatedTime else "--"
        }

        try {
            val sdf = SimpleDateFormat("HH:mm", Locale.UK)
            sdf.setTimeZone(TimeZone.getTimeZone("Europe/London"))

            val now = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"))
            val currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            val targetDate = sdf.parse(timeToUse)
            if (targetDate == null) return if (estimatedTime != null) estimatedTime else scheduledTime

            val target = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"))
            target.setTime(targetDate)
            var targetMins = target.get(Calendar.HOUR_OF_DAY) * 60 + target.get(Calendar.MINUTE)

            if (targetMins < currentMins && (currentMins - targetMins) > 720) {
                targetMins += 1440
            }

            val diffMins = targetMins - currentMins

            if (diffMins < -1) {
                return abs(diffMins).toString() + "m ago"
            } else if (diffMins <= 0) {
                return "Due"
            } else if (diffMins < 60) {
                return diffMins.toString() + " min"
            } else {
                val h = diffMins / 60
                val m = diffMins % 60
                return if (m > 0) h.toString() + "h " + m + "m" else h.toString() + "h"
            }
        } catch (_: Exception) {
            return if (estimatedTime != null) estimatedTime else scheduledTime
        }
    }

    private fun setAnimation(viewToAnimate: View, position: Int) {
        if (position > lastPosition) {
            viewToAnimate.setAlpha(0f)
            viewToAnimate.setTranslationY(80f)
            viewToAnimate.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .start()
            lastPosition = position
        } else {
            viewToAnimate.setAlpha(1f)
            viewToAnimate.setTranslationY(0f)
        }
    }

    private fun calculateDuration(start: String?, end: String?): String {
        if (start == null || end == null || !start.contains(":") || !end.contains(":")) return ""
        try {
            val sParts: Array<String?> =
                start.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val eParts: Array<String?> =
                end.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val sMins = sParts[0]!!.toInt() * 60 + sParts[1]!!.toInt()
            val eMins = eParts[0]!!.toInt() * 60 + eParts[1]!!.toInt()

            var diff = eMins - sMins
            if (diff < 0) diff += 1440

            val h = diff / 60
            val m = diff % 60

            if (h > 0) return h.toString() + "h " + m + "m"
            return m.toString() + "m"
        } catch (_: Exception) {
            return ""
        }
    }

    fun appendData(newServices: MutableList<TrainService>?): Int {
        if (newServices == null || newServices.isEmpty()) return 0
        var addedCount = 0
        for (newService in newServices) {
            var exists = false
            for (existing in upcomingServices) {
                if (existing.serviceID == newService.serviceID) {
                    exists = true
                    break
                }
            }
            if (!exists) {
                for (existing in earlierServices) {
                    if (existing.serviceID == newService.serviceID) {
                        exists = true
                        break
                    }
                }
            }
            if (!exists) {
                if (isServicePassed(newService)) {
                    earlierServices.add(newService)
                } else {
                    upcomingServices.add(newService)
                }
                addedCount++
            }
        }
        if (addedCount > 0) {
            notifyDataSetChanged()
        }
        return addedCount
    }

    private fun fetchOriginTimeAsync(serviceId: String?, position: Int) {
        if (ServiceDetailsCache.currentlyFetching.contains(serviceId)) {
            return
        }

        ServiceDetailsCache.currentlyFetching.add(serviceId)
        val api =
            APIClient.getClient().create(NationalRailAPI::class.java)

        api.getServiceDetails(serviceId).enqueue(object : Callback<ServiceDetailsResponse?> {
            override fun onResponse(
                call: Call<ServiceDetailsResponse?>,
                response: Response<ServiceDetailsResponse?>
            ) {
                if (response.isSuccessful() && response.body() != null) {
                    put(serviceId, response.body())

                    Handler(Looper.getMainLooper()).post(Runnable {
                        notifyItemChanged(position)
                    })
                } else {
                    ServiceDetailsCache.currentlyFetching.remove(serviceId)
                }
            }

            override fun onFailure(call: Call<ServiceDetailsResponse?>, t: Throwable) {
                ServiceDetailsCache.currentlyFetching.remove(serviceId)
            }
        })
    }

    class HeaderViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        var ivExpandEarlier: ImageView
        var tvEarlierTitle: TextView

        init {
            ivExpandEarlier = v.findViewById(R.id.ivExpandEarlier)
            tvEarlierTitle = v.findViewById(R.id.tvEarlierTitle)
        }
    }

    class ItemViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        var ivModeIcon: ImageView
        var tvOperatorName: TextView
        var tvCurrentStationTime: TextView
        var tvPlatform: TextView
        var dotTop: View
        var timelineLine: View
        var dotBottom: View
        var tvDepTime: TextView
        var tvOriginStation: TextView
        var tvArrTime: TextView
        var tvDestStation: TextView
        var tvServiceDetails: TextView
        var tvLiveStatus: TextView
        var defaultSchColour: Int

        init {
            ivModeIcon = v.findViewById(R.id.ivModeIcon)
            tvOperatorName = v.findViewById(R.id.tvOperatorName)
            tvCurrentStationTime = v.findViewById(R.id.tvCurrentStationTime)
            tvPlatform = v.findViewById(R.id.tvPlatform)
            dotTop = v.findViewById(R.id.dotTop)
            timelineLine = v.findViewById(R.id.timelineLine)
            dotBottom = v.findViewById(R.id.dotBottom)
            tvDepTime = v.findViewById(R.id.tvDepTime)
            tvOriginStation = v.findViewById(R.id.tvOriginStation)
            tvArrTime = v.findViewById(R.id.tvArrTime)
            tvDestStation = v.findViewById(R.id.tvDestStation)
            tvServiceDetails = v.findViewById(R.id.tvServiceDetails)
            tvLiveStatus = v.findViewById(R.id.tvLiveStatus)
            defaultSchColour = tvDepTime.getCurrentTextColor()
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }
}