package io.github.turtle358.callingpoint.utils

import io.github.turtle358.callingpoint.models.ServiceDetailsResponse

object ServiceDetailsCache {
    private const val STALE_TIME_MS: Long = 60000
    private val cache: MutableMap<String?, CachedService?> = HashMap<String?, CachedService?>()

    @JvmField
    val currentlyFetching: MutableSet<String?> = HashSet<String?>()

    @JvmStatic
    fun get(serviceId: String?): ServiceDetailsResponse? {
        val cached = cache.get(serviceId)
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < STALE_TIME_MS) {
            return cached.response
        }
        return null
    }

    @JvmStatic
    fun put(serviceId: String?, response: ServiceDetailsResponse?) {
        cache.put(serviceId, CachedService(response, System.currentTimeMillis()))
        currentlyFetching.remove(serviceId)
    }

    class CachedService(var response: ServiceDetailsResponse?, var timestamp: Long)
}