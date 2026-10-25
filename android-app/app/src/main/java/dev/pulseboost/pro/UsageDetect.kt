package dev.pulseboost.pro

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Foreground-app detection backed by UsageStatsManager. Requires the user
 * to grant "Usage access" in system settings (PACKAGE_USAGE_STATS).
 */
object UsageDetect {

    fun hasUsageAccess(context: Context): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (t: Throwable) {
        false
    }

    /**
     * Returns the package most recently moved to the foreground within the
     * last [windowMs] milliseconds, or null when usage access is missing or
     * no event was recorded.
     */
    fun foregroundPackage(context: Context, windowMs: Long = 15_000L): String? {
        if (!hasUsageAccess(context)) return null
        return try {
            val usm =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - windowMs, end)
            val event = UsageEvents.Event()
            val resumeType = if (Build.VERSION.SDK_INT >= 29) {
                UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            var latestPkg: String? = null
            var latestTime = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == resumeType && event.timeStamp >= latestTime) {
                    latestTime = event.timeStamp
                    latestPkg = event.packageName
                }
            }
            latestPkg
        } catch (t: Throwable) {
            null
        }
    }
}
