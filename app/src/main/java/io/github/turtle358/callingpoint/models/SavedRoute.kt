package io.github.turtle358.callingpoint.models

import java.util.Locale
data class SavedRoute @JvmOverloads constructor(
    val fromName: String,
    val toName: String,
    val fromCrs: String?,
    val toCrs: String?,
    val fromNaptan: String?,
    val toNaptan: String?,
    val fromLocation: JourneyLocation? = null,
    val toLocation: JourneyLocation? = null,
    val fromSource: String? = null,
    val toSource: String? = null
) {
    fun routeKey(): String = endpointKey(fromName, fromLocation, fromSource) +
            " -> " + endpointKey(toName, toLocation, toSource)

    private fun endpointKey(name: String, location: JourneyLocation?, source: String?): String {
        when (source?.uppercase(Locale.ROOT)) {
            "CURRENT" -> return "current"
            "HOME" -> return "home"
            "WORK" -> return "work"
        }
        if (location?.type == JourneyLocation.Type.CURRENT_LOCATION) return "current"
        if (location != null && location.type != JourneyLocation.Type.STATION) {
            return location.stableKey()
        }
        return "station:" + name.trim().lowercase(Locale.UK)
    }
}
