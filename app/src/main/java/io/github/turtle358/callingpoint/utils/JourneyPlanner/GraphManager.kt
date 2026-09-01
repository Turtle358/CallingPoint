package io.github.turtle358.callingpoint.utils.JourneyPlanner

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class GraphManager private constructor(context: Context) {
    private val prefs: SharedPreferences
    private val gson: Gson
    private var networkGraph: MutableMap<String?, MutableSet<String?>?>? = null

    init {
        prefs =
            context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        gson = Gson()
        loadGraph()
    }

    private fun loadGraph() {
        val json = prefs.getString(KEY_GRAPH, null)
        if (json != null) {
            val type = object : TypeToken<MutableMap<String?, MutableSet<String?>?>?>() {}.getType()
            networkGraph = gson.fromJson<MutableMap<String?, MutableSet<String?>?>>(json, type)
        } else {
            networkGraph = HashMap<String?, MutableSet<String?>?>()
        }
    }

    private fun saveGraph() {
        val json = gson.toJson(networkGraph)
        prefs.edit().putString(KEY_GRAPH, json).apply()
    }


    fun addConnection(originCrs: String?, destinationCrs: String?) {
        if (originCrs == null || destinationCrs == null || originCrs == destinationCrs) return

        if (!networkGraph!!.containsKey(originCrs)) {
            networkGraph!!.put(originCrs, HashSet<String?>())
        }

        val isNewConnection = networkGraph!!.get(originCrs)!!.add(destinationCrs)

        if (isNewConnection) {
            saveGraph()
        }
    }

    fun getConnections(crs: String?): MutableSet<String?>? {
        return networkGraph!!.getOrDefault(crs, HashSet<String?>())
    }

    val knownStationCount: Int
        get() = networkGraph!!.size

    fun clearMemory() {
        networkGraph!!.clear()
        prefs.edit().remove(KEY_GRAPH).apply()
    }

    companion object {
        private const val PREF_NAME = "NetworkGraphPrefs"
        private const val KEY_GRAPH = "graph_data"

        private var instance: GraphManager? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): GraphManager {
            if (instance == null) {
                instance = GraphManager(context)
            }
            return instance!!
        }
    }
}