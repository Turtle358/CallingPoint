package io.github.turtle358.callingpoint.models

data class SavedRoute(
    val fromName: String,
    val toName: String,
    val fromCrs: String?,
    val toCrs: String?,
    val fromNaptan: String?,
    val toNaptan: String?
)