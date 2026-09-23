package io.github.turtle358.callingpoint.ui.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.turtle358.callingpoint.R
import io.github.turtle358.callingpoint.utils.StationHelper.getIconForStation
import io.github.turtle358.callingpoint.utils.StationHelper.getNameFromCode
import java.util.Collections

class PinnedStationAdapter(
    private val context: Context?,
    private val pinnedKeys: MutableList<String>,
    private val listener: OnPinClickListener
) : RecyclerView.Adapter<PinnedStationAdapter.ViewHolder?>() {
    interface OnPinClickListener {
        fun onPinClick(fromCrs: String?, toCrs: String?)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(context).inflate(R.layout.item_pinned_station, parent, false)
        return ViewHolder(v)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val key = pinnedKeys[position]

        val fromCode =
            if (key.contains(":")) key.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[0] else key
        val toCode = if (key.contains(":")) key.split(":".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()[1] else ""

        var fromName = getNameFromCode(fromCode)
        if (fromName.isEmpty()) fromName = fromCode

        var toName = ""
        if (!toCode.isEmpty()) {
            toName = getNameFromCode(toCode)
            if (toName.isEmpty()) toName = toCode
        }

        holder.tvStationName.text = fromName

        holder.ivLogo.setImageResource(getIconForStation(fromName))

        if (!toCode.isEmpty()) {
            holder.tvDestinationName.text = "to $toName"
            holder.tvDestinationName.visibility = View.VISIBLE
        } else {
            holder.tvDestinationName.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { _: View? ->
            listener.onPinClick(
                fromCode,
                toCode
            )
        }
    }

    override fun getItemCount(): Int {
        return pinnedKeys.size
    }

    fun swapItems(fromPosition: Int, toPosition: Int) {
        Collections.swap(pinnedKeys, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        var tvStationName: TextView = v.findViewById(R.id.tvStationName)
        var tvDestinationName: TextView = v.findViewById(R.id.tvDestinationName)
        var ivLogo: ImageView = v.findViewById(R.id.stationBranding)
    }
}