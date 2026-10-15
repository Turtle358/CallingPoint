package io.github.turtle358.callingpoint.ui.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import io.github.turtle358.callingpoint.R
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse
import io.github.turtle358.callingpoint.models.ServiceDetailsResponse.TimelinePoint
import io.github.turtle358.callingpoint.ui.activities.ServiceDetailsActivity
import io.github.turtle358.callingpoint.utils.TOCBrandHelper
import java.util.Collections
import java.util.Locale
import androidx.core.graphics.toColorInt

class PinnedServiceAdapter(
    private val context: Context,
    private val liveServices: MutableList<ServiceDetailsResponse>
) : RecyclerView.Adapter<PinnedServiceAdapter.ViewHolder?>() {
    private var lastPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(context).inflate(R.layout.item_pinned_service, parent, false)
        return ViewHolder(v)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val service = liveServices[position]

        val brandColour = TOCBrandHelper.getColourForToc(service.operator)
        val secondaryColour = TOCBrandHelper.getSecondaryColourForToc(service.operator)
        if (TOCBrandHelper.getManagerForToc(service.operator) == "gbr" || TOCBrandHelper.getManagerForToc(
                service.operator
            ) == "national"
        ) {
            holder.ivModeIcon.setImageResource(R.drawable.ic_national_rail)
        } else {
            holder.ivModeIcon.setImageResource(TOCBrandHelper.getIconForToc(service.operator))
        }

        val pillBg = GradientDrawable()
        pillBg.setColor(brandColour)
        pillBg.setCornerRadius(
            holder.itemView.context.resources.displayMetrics.density * 8
        )
        holder.tvOperatorName.background = pillBg
        holder.tvOperatorName.setTextColor(secondaryColour)

        if (service.operator != null && !service.operator.isEmpty()) {
            val op = service.operator
            holder.tvOperatorName.text =
                op.substring(0, 1).uppercase(Locale.getDefault()) + op.substring(1).lowercase(
                    Locale.getDefault()
                )
        }

        val dotBg = GradientDrawable()
        dotBg.setShape(GradientDrawable.OVAL)
        dotBg.setColor(brandColour)
        holder.dotTop.background = dotBg
        holder.dotBottom.background = dotBg
        holder.timelineLine.setBackgroundColor(ColorUtils.setAlphaComponent(brandColour, 102))

        val schTime = service.scheduledDepartureTime ?: "--:--"
        val currentStationName =
            service.locationName ?: "Unknown"

        var trueOriginName: String? = currentStationName
        var trueOriginTime: String? = schTime
        if (service.previousCallingPoints != null && !service.previousCallingPoints.isEmpty() && service.previousCallingPoints[0].callingPoint != null
        ) {
            trueOriginName = service.previousCallingPoints[0].callingPoint[0].locationName
            trueOriginTime = service.previousCallingPoints[0].callingPoint[0].scheduledTime
        }

        var trueDestName: String? = currentStationName
        var trueDestTime: String? = schTime
        if (service.subsequentCallingPoints != null && !service.subsequentCallingPoints.isEmpty() && service.subsequentCallingPoints.get(
                0
            ).callingPoint != null
        ) {
            val subsequent = service.subsequentCallingPoints[0].callingPoint
            trueDestName = subsequent[subsequent.size - 1]!!.locationName
            trueDestTime = subsequent[subsequent.size - 1]!!.scheduledTime
        }

        holder.tvOriginStation.text = trueOriginName
        holder.tvDepTime.text = trueOriginTime
        holder.tvDestStation.text = trueDestName
        holder.tvArrTime.text = trueDestTime

        holder.tvCurrentStationTime.text = schTime

        val entireJourney: MutableList<TimelinePoint> = ArrayList<TimelinePoint>()
        if (service.previousCallingPoints != null && !service.previousCallingPoints.isEmpty() && service.previousCallingPoints.get(
                0
            ).callingPoint != null
        ) {
            entireJourney.addAll(service.previousCallingPoints.get(0).callingPoint)
        }

        val anchorStation = TimelinePoint()
        anchorStation.locationName = service.locationName
        anchorStation.scheduledTime = service.scheduledDepartureTime ?: ""
        anchorStation.estimatedTime = service.estimatedDepartureTime
        anchorStation.actualTime = service.actualDepartureTime ?: service.actualArrivalTime
        entireJourney.add(anchorStation)

        if (service.subsequentCallingPoints != null && !service.subsequentCallingPoints.isEmpty() && service.subsequentCallingPoints.get(
                0
            ).callingPoint != null
        ) {
            entireJourney.addAll(service.subsequentCallingPoints.get(0).callingPoint)
        }

        var trueNextStop: TimelinePoint? = null
        var trueNextStopIndex = -1

        for (i in entireJourney.indices) {
            val point = entireJourney[i]
            var hasPassed = point.actualTime != null && !point.actualTime.isEmpty()

            if (point.estimatedTime != null && (point.estimatedTime.equals(
                    "Arrived",
                    ignoreCase = true
                ) || point.estimatedTime.equals("Departed", ignoreCase = true))
            ) {
                hasPassed = true
            }

            if (!hasPassed) {
                trueNextStop = point
                trueNextStopIndex = i
                break
            }
        }

        holder.tvCurrentStationTime.paintFlags = holder.tvCurrentStationTime.paintFlags and (Paint.STRIKE_THRU_TEXT_FLAG.inv())
        holder.tvCurrentStationTime.setTextColor(holder.defaultSchColour)
        holder.tvLiveStatus.visibility = View.VISIBLE

        if (trueNextStop != null) {
            val estTime =
                if (trueNextStop.estimatedTime != null && !trueNextStop.estimatedTime.isEmpty()) trueNextStop.estimatedTime else "On time"

            if ("On time".equals(estTime, ignoreCase = true)) {
                holder.tvCurrentStationTime.setTextColor(holder.defaultSchColour)
                holder.tvLiveStatus.setText("On time")
                holder.tvLiveStatus.setTextColor("#00875A".toColorInt())
            } else if ("Cancelled".equals(estTime, ignoreCase = true)) {
                holder.tvCurrentStationTime.paintFlags = holder.tvCurrentStationTime.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                holder.tvLiveStatus.text = "Cancelled"
                holder.tvLiveStatus.setTextColor("#DE350B".toColorInt())
            } else {
                holder.tvCurrentStationTime.paintFlags = holder.tvCurrentStationTime.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                holder.tvLiveStatus.text = "Exp $estTime"
                holder.tvLiveStatus.setTextColor("#FF8C00".toColorInt())
            }

            val stopsLeft = entireJourney.size - trueNextStopIndex - 1
            var durationStr = ""

            if (stopsLeft > 0) {
                val finalDest = entireJourney.get(entireJourney.size - 1)
                durationStr = calculateDuration(trueNextStop.scheduledTime, finalDest.scheduledTime)
            }
            val callingText = "Next station: " + trueNextStop.locationName

            var detailsText = ""
            if (!durationStr.isEmpty()) detailsText += "$durationStr • "

            if (stopsLeft > 0) {
                detailsText += stopsLeft.toString() + (if (stopsLeft == 1) " stop • " else " stops • ")
            }
            detailsText += callingText

            holder.tvServiceDetails.text = detailsText
        } else {
            holder.tvLiveStatus.text = "Terminated"
            holder.tvLiveStatus.setTextColor("#666666".toColorInt())
            holder.tvServiceDetails.text = "Terminated at $currentStationName"
        }

        setAnimation(holder.itemView, position)

        val finalDestName = trueDestName

        holder.itemView.setOnClickListener { _: View? ->
            val intent = Intent(context, ServiceDetailsActivity::class.java)
            intent.putExtra("SERVICE_ID", service.customServiceID)
            intent.putExtra("USER_ORIGIN", service.locationName)
            intent.putExtra("USER_DESTINATION", finalDestName)
            context.startActivity(intent)
        }
    }

    private fun setAnimation(viewToAnimate: View, position: Int) {
        if (position > lastPosition) {
            viewToAnimate.setAlpha(0f)
            viewToAnimate.translationY = 80f
            viewToAnimate.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .start()
            lastPosition = position
        } else {
            viewToAnimate.setAlpha(1f)
            viewToAnimate.translationY = 0f
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

    override fun getItemCount(): Int {
        return liveServices.size
    }

    fun swapItems(fromPosition: Int, toPosition: Int) {
        Collections.swap(liveServices, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        var ivModeIcon: ImageView
        var tvOperatorName: TextView
        var tvCurrentStationTime: TextView
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
}