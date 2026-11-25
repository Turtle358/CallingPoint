package io.github.turtle358.callingpoint.utils

import android.content.Context
import io.github.turtle358.callingpoint.R
import org.json.JSONObject

object StationHelper{
    private var stationsJson : JSONObject? = null;
    private var cachedStationNames : MutableList<String>? = null
    @JvmStatic
    fun init(context: Context) {
        if (stationsJson != null) return

        reload(context)
    }

    @JvmStatic
    @Synchronized
    fun reload(context: Context) {
        stationsJson = try {
            val jsonStr = context.openFileInput("stations_remote.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            JSONObject(jsonStr)

        } catch (e: Exception) {
            try {
                val jsonStr = context.assets.open("stations.json")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

                JSONObject(jsonStr)

            } catch (e: Exception) {
                e.printStackTrace()
                stationsJson ?: JSONObject()
            }
        }
        cachedStationNames = null
    }
    @JvmStatic
    fun getStationNames() : List<String> {
        cachedStationNames?.let {return it}
        val json = stationsJson ?: return emptyList()
        val names = json.keys().asSequence().toList().sorted()
        cachedStationNames = names as MutableList<String>?
        return names
    }

    @JvmStatic
    fun getNameFromCode(code: String) : String {
        if(code.isNullOrEmpty()) return ""
        val json = stationsJson ?: return ""
        if (stationsJson?.has(code) ==  true) {
            return code
        }

        for (stationName in json.keys()) {
            val stationData = json.optJSONObject(stationName) ?: continue
            val crs = stationData.optString("crs", "")
            val naptan = stationData.optString("naptan", "")
            if(code.equals(crs, ignoreCase = true) || code.equals(naptan, ignoreCase = true)) {
                return stationName
            }
        }
        return ""
    }

    @JvmStatic
    fun getCrsFromName(stationName: String) : String {
        if (stationName.isNullOrEmpty()) return ""
        val json = stationsJson ?: return ""
        val data = json.optJSONObject(stationName) ?: return ""
        return data.optString("crs", "")
    }

    @JvmStatic
    fun getNaptanFromName(stationName: String) : String {
        if (stationName.isNullOrEmpty()) return ""
        val json = stationsJson ?: return ""
        val data = json.optJSONObject(stationName) ?: return ""
        return data.optString("naptan", "")
    }

    @JvmStatic
    fun getStationType(stationName: String) : String {
        if(stationName.isNullOrEmpty()) return "national"
        val json = stationsJson ?: return "national"
        val data = json.optJSONObject(stationName) ?: return "national"
        return data.optString("type","national")
    }

    @JvmStatic
    fun getIconForStation(name: String) : Int {
        return when (getStationType(name).lowercase()) {
            "tfl", "tfl-national" -> R.drawable.ic_tfl
            "gbr" -> R.drawable.ic_gbr
            "national" -> R.drawable.ic_national_rail
            "scot" -> R.drawable.ic_scotrail_small
            "wales" -> R.drawable.ic_tfw
            "merseytravel" -> R.drawable.ic_merseyrail
            else -> R.drawable.ic_train
        }
    }

    @JvmStatic
    fun normalise(name: String?): String {
        if (name.isNullOrEmpty()) return ""

        return name.lowercase()
            .replace("'", "")
            .replace("’", "")
            .replace("(intl)", "international")
            .replace(" intl", " international")
            .replace(" underground station", "")
            .replace(" rail station", "")
            .replace(" station", "")
            .replace(" & ", " and ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    @JvmStatic
    fun areStationNamesEqual(name1: String?, name2: String?): Boolean {
        if (name1 == null || name2 == null) return false
        return normalise(name1) == normalise(name2)
    }


}