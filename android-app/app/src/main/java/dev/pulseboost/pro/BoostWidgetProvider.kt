package dev.pulseboost.pro

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews

/**
 * Feature F: home screen widget showing live free RAM (read from
 * ActivityManager.MemoryInfo on every refresh) plus a one-tap Boost button
 * that runs the Shizuku kill-all + cache-trim sequence.
 *
 * A self-rescheduling 30-second AlarmManager chain keeps the reading fresh;
 * note that Android Doze can stretch that cadence when the device is idle,
 * which is by design of the platform and documented in the README.
 */
class BoostWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refreshAll(context)
        scheduleNext(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelSchedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                refreshAll(context)
                scheduleNext(context)
            }
            ACTION_BOOST -> {
                val pending = goAsync()
                Thread({
                    try {
                        if (ShizukuManager.hasPermission()) {
                            ShizukuManager.killAllBackground()
                            ShizukuManager.trimCaches()
                            try {
                                Thread.sleep(600)
                            } catch (_: InterruptedException) {
                            }
                        } else {
                            Log.w(TAG, "widget boost tapped without Shizuku permission")
                        }
                    } finally {
                        refreshAll(context)
                        scheduleNext(context)
                        pending.finish()
                    }
                }, "pulseboost-widget-boost").start()
            }
        }
    }

    private fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, BoostWidgetProvider::class.java),
        )
        if (ids.isEmpty()) return
        val ram = StatsReader.readRam(context)
        val views = RemoteViews(context.packageName, R.layout.widget_boost)
        views.setTextViewText(
            R.id.widget_ram,
            "FREE ${StatsReader.formatMb(ram.availableBytes)}  ·  USED ${ram.usedPercent}%",
        )
        views.setTextViewText(
            R.id.widget_hint,
            if (ram.lowMemory) "low memory — tap BOOST" else "updated just now",
        )
        views.setOnClickPendingIntent(
            R.id.widget_boost_button, boostPendingIntent(context),
        )
        views.setOnClickPendingIntent(
            R.id.widget_ram, refreshPendingIntent(context),
        )
        val openApp = PendingIntent.getActivity(
            context, 7, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_title, openApp)
        manager.updateAppWidget(ids, views)
    }

    private fun scheduleNext(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + REFRESH_MS,
            refreshPendingIntent(context),
        )
    }

    private fun cancelSchedule(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(refreshPendingIntent(context))
    }

    private fun refreshPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 11,
            Intent(context, BoostWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun boostPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 12,
            Intent(context, BoostWidgetProvider::class.java).setAction(ACTION_BOOST),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val TAG = "BoostWidgetProvider"
        private const val REFRESH_MS = 30_000L
        const val ACTION_REFRESH = "dev.pulseboost.pro.widget.REFRESH"
        const val ACTION_BOOST = "dev.pulseboost.pro.widget.BOOST"
    }
}
