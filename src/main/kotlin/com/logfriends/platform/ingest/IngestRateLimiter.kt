package com.logfriends.platform.ingest

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight instance-local in-memory rate limiter.
 * Limits requests per key (workerId or IP) over a rolling 1-minute window.
 */
@Component
class IngestRateLimiter(
    private val maxRequestsPerMinute: Int = 120
) {
    private val windows = ConcurrentHashMap<String, Window>()

    data class Window(
        val windowStartMinute: Long,
        val counter: AtomicInteger
    )

    fun tryAcquire(key: String): Boolean {
        val currentMinute = System.currentTimeMillis() / 60_000

        // Periodic cleanup of stale windows if size is large
        if (windows.size > 5000) {
            cleanup(currentMinute)
        }

        val window = windows.compute(key) { _, existing ->
            if (existing == null || existing.windowStartMinute < currentMinute) {
                Window(currentMinute, AtomicInteger(1))
            } else {
                existing.counter.incrementAndGet()
                existing
            }
        }!!

        return window.counter.get() <= maxRequestsPerMinute
    }

    private fun cleanup(currentMinute: Long) {
        val staleKeys = windows.filter { it.value.windowStartMinute < currentMinute }.keys
        staleKeys.forEach { windows.remove(it) }
    }

    fun reset() {
        windows.clear()
    }
}
