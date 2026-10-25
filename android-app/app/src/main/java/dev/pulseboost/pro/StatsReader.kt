package dev.pulseboost.pro

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import java.io.RandomAccessFile

/**
 * Reads genuine device statistics from Android system services and /proc.
 * No value here is invented: RAM comes from ActivityManager.MemoryInfo,
 * CPU percentages are computed from /proc/stat deltas, battery data comes
 * from the ACTION_BATTERY_CHANGED sticky broadcast / BatteryManager.
 */
object StatsReader {

    data class RamSnapshot(
        val totalBytes: Long,
        val availableBytes: Long,
        val thresholdBytes: Long,
        val lowMemory: Boolean,
    ) {
        val usedBytes: Long get() = totalBytes - availableBytes
        val usedPercent: Int get() =
            if (totalBytes <= 0) 0 else ((usedBytes * 100L) / totalBytes).toInt()
    }

    fun readRam(context: Context): RamSnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return RamSnapshot(
            totalBytes = info.totalMem,
            availableBytes = info.availMem,
            thresholdBytes = info.threshold,
            lowMemory = info.lowMemory,
        )
    }

    // ---- CPU from /proc/stat --------------------------------------------------

    private data class CpuTimes(val busy: Long, val total: Long)

    private var lastTimes: List<CpuTimes>? = null

    data class CpuSnapshot(val overall: Int, val perCore: List<Int>)

    /**
     * Computes busy percentages by diffing jiffies between two consecutive
     * calls (call about once per second for meaningful numbers).
     */
    @Synchronized
    fun sampleCpu(): CpuSnapshot {
        val current = readProcStat()
        val previous = lastTimes
        lastTimes = current
        if (previous == null || previous.size != current.size) {
            return CpuSnapshot(0, List(maxOf(0, current.size - 1)) { 0 })
        }
        val deltas = current.zip(previous).map { (cur, prev) ->
            val dt = cur.total - prev.total
            val db = cur.busy - prev.busy
            if (dt <= 0) 0 else ((db * 100L) / dt).toInt().coerceIn(0, 100)
        }
        return CpuSnapshot(
            overall = deltas.firstOrNull() ?: 0,
            perCore = if (deltas.size > 1) deltas.drop(1) else emptyList(),
        )
    }

    private fun readProcStat(): List<CpuTimes> {
        val times = ArrayList<CpuTimes>(12)
        try {
            RandomAccessFile("/proc/stat", "r").use { file ->
                var line: String? = file.readLine()
                while (line != null && line.startsWith("cpu")) {
                    val parts = line.split(Regex("\\s+"))
                    // cpuN user nice system idle iowait irq softirq steal ...
                    if (parts.size >= 8) {
                        val nums = parts.drop(1).mapNotNull { it.toLongOrNull() }
                        if (nums.size >= 7) {
                            val idle = nums[3] + nums[4] // idle + iowait
                            val busy = nums[0] + nums[1] + nums[2] + nums[5] + nums[6]
                            times.add(CpuTimes(busy = busy, total = busy + idle))
                        }
                    }
                    line = file.readLine()
                }
            }
        } catch (t: Throwable) {
            // /proc/stat unreadable (rare SELinux policy) — report nothing.
        }
        return times
    }

    // ---- Battery ---------------------------------------------------------------

    data class BatterySnapshot(
        val levelPercent: Int,
        val temperatureC: Float,
        val charging: Boolean,
        val pluggedLabel: String,
        val healthLabel: String,
        val voltageMv: Int,
    )

    fun readBattery(context: Context): BatterySnapshot {
        val intent: Intent? = context.registerReceiver(
            null as BroadcastReceiver?,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        if (intent == null) {
            return BatterySnapshot(-1, 0f, false, "unknown", "unknown", -1)
        }
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val tempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "unplugged"
        }
        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over-voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "cold"
            else -> "unknown"
        }
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        return BatterySnapshot(pct, tempC, charging, plugged, health, voltage)
    }

    fun formatMb(bytes: Long): String {
        if (bytes < 0) return "--"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format("%.1f GB", mb / 1024.0)
        else String.format("%.0f MB", mb)
    }

    fun sdkAtLeast31(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
