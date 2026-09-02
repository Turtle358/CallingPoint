package io.github.turtle358.callingpoint.utils.JourneyPlanner

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.turtle358.callingpoint.models.SavedRoute
import androidx.core.content.edit

class SavedRoutesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("SavedRoutesPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val KEY_SAVED_ROUTES = "saved_routes_list"

    fun getSavedRoutes(): List<SavedRoute> {
        val json = prefs.getString(KEY_SAVED_ROUTES, null) ?: return emptyList()
        val type = object : TypeToken<List<SavedRoute>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveRoute(route: SavedRoute) {
        val currentRoutes = getSavedRoutes().toMutableList()

        val exists = currentRoutes.any {
            it.fromName == route.fromName && it.toName == route.toName
        }

        if (!exists) {
            currentRoutes.add(0, route)
            saveAll(currentRoutes)
        }
    }

    fun removeRoute(fromName: String, toName: String) {
        val currentRoutes = getSavedRoutes().toMutableList()
        currentRoutes.removeAll {
            it.fromName == fromName && it.toName == toName
        }
        saveAll(currentRoutes)
    }

    fun isRouteSaved(fromName: String, toName: String): Boolean {
        return getSavedRoutes().any {
            it.fromName == fromName && it.toName == toName
        }
    }

    fun saveAll(routes: List<SavedRoute>) {
        prefs.edit(commit = true) { putString(KEY_SAVED_ROUTES, gson.toJson(routes)) }
    }
}