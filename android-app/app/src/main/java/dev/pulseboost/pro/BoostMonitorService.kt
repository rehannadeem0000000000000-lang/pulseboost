package dev.pulseboost.pro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Feature D engine: watches UsageStatsManager for foreground changes while
 * enabled, and when an app with an "auto-boost on launch" profile takes the
 * foreground it immediately runs the user's Shizuku boost sequence:
 * kills background processes of the marked heavyweight apps, then reclaims
 * cached memory system-wide. Results are written to ProfileStore so the
 * Settings tab can display the most recent run.
 */
class BoostMonitorService : Service() {

    private lateinit var store: ProfileStore
    @Volatile private var running = false
    private var worker: Thread? = null
    private var lastForeground: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = ProfileStore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (!running) {
            running = true
            worker = Thread({ watchLoop() }, "pulseboost-monitor").also { it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        worker = null
        super.onDestroy()
    }

    private fun watchLoop() {
        while (running) {
            try {
                tick()
                Thread.sleep(POLL_MS)
            } catch (ie: InterruptedException) {
                return
            } catch (t: Throwable) {
                Log.w(TAG, "monitor tick failed", t)
                try {
                    Thread.sleep(POLL_MS)
                } catch (ie: InterruptedException) {
                    return
                }
            }
        }
    }

    private fun tick() {
        if (!store.monitorEnabled) return
        if (store.boostProfiles.isEmpty()) return
        val foreground = UsageDetect.foregroundPackage(this, POLL_MS * 3) ?: return
        if (foreground == lastForeground) return
        val previous = lastForeground
        lastForeground = foreground
        if (previous == null) return // first detection after service start
        if (foreground != packageName && store.boostProfiles.contains(foreground) &&
            ShizukuManager.hasPermission()
        ) {
            runAutoBoost(foreground)
        }
    }

    private fun runAutoBoost(gamePkg: String) {
        val before = StatsReader.readRam(this)
        val hogs = store.selectedApps.filter { it != gamePkg && it != packageName }
        hogs.forEach { hog ->
            ShizukuManager.killBackground(hog)
        }
        ShizukuManager.trimCaches()
        try {
            Thread.sleep(500)
        } catch (ie: InterruptedException) {
            return
        }
        val after = StatsReader.readRam(this)
        val deltaMb = (after.availableBytes - before.availableBytes) / (1024 * 1024)
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val summary = "$stamp — auto-boosted for $gamePkg: " +
            "killed ${hogs.size} background app(s), trimmed caches, " +
            (if (deltaMb >= 0) "+$deltaMb MB free" else "$deltaMb MB free")
        store.lastAutoBoost = summary
        Log.i(TAG, summary)
        updateNotification(summary)
    }

    private fun startInForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Game launch monitor", NotificationManager.IMPORTANCE_MIN,
                ),
            )
        }
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = buildNotification("Watching for profile-enabled games", pending)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        lastNotification = notification
    }

    private var lastNotification: Notification? = null

    private fun buildNotification(text: String, pending: PendingIntent): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("PulseBoost Pro profiles active")
            .setContentText(text.take(64))
            .setContentIntent(pending)
            .build()
    }

    private fun updateNotification(text: String) {
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text, pending))
    }

    companion object {
        private const val TAG = "BoostMonitorService"
        private const val CHANNEL_ID = "pulseboost_monitor"
        private const val NOTIFICATION_ID = 0x50B2
        private const val POLL_MS = 2500L
        const val ACTION_STOP = "dev.pulseboost.pro.monitor.STOP"

        fun startIntent(context: Context): Intent =
            Intent(context, BoostMonitorService::class.java)
    }
}
