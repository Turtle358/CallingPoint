package io.github.turtle358.callingpoint.utils.JourneyPlanner

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.turtle358.callingpoint.models.SavedRoute

class SavedRoutesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("SavedRoutesPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val keySavedRoutes = "saved_routes_list"

    fun getSavedRoutes(): List<SavedRoute> {
        val json = prefs.getString(keySavedRoutes, null) ?: return emptyList()
        val type = object : TypeToken<List<SavedRoute>>() {}.type
        return try { gson.fromJson<List<SavedRoute>>(json, type)?.filterNotNull() ?: emptyList() }
        catch (_: RuntimeException) { emptyList() }
    }

    fun saveRoute(route: SavedRoute) {
        val currentRoutes = getSavedRoutes().toMutableList()
        if (currentRoutes.none { it.routeKey() == route.routeKey() }) {
            currentRoutes.add(0, route)
            saveAll(currentRoutes)
        }
    }

    fun removeRoute(route: SavedRoute) {
        val currentRoutes = getSavedRoutes().toMutableList()
        currentRoutes.removeAll { it.routeKey() == route.routeKey() }
        saveAll(currentRoutes)
    }

    fun isRouteSaved(route: SavedRoute): Boolean =
        getSavedRoutes().any { it.routeKey() == route.routeKey() }

    fun removeRoute(fromName: String, toName: String) {
        val currentRoutes = getSavedRoutes().toMutableList()
        currentRoutes.removeAll { it.fromName == fromName && it.toName == toName }
        saveAll(currentRoutes)
    }

    fun isRouteSaved(fromName: String, toName: String): Boolean =
        getSavedRoutes().any { it.fromName == fromName && it.toName == toName }

    fun saveAll(routes: List<SavedRoute>) {
        prefs.edit(commit = true) { putString(keySavedRoutes, gson.toJson(routes)) }
    }
}
