package dev.pulseboost.pro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Feature E: a draggable, dismissible FPS overlay drawn above other apps.
 *
 * FPS is measured with [Choreographer.FrameCallback] — the same frame
 * pulses UI rendering uses — so the number reflects actual delivered
 * frames of the overlay's frame source, updated twice per second. The
 * current foreground package is resolved through UsageStatsManager.
 */
class BoostOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var fpsText: TextView? = null
    private var pkgText: TextView? = null
    private var frameCount = 0
    private var windowStartNanos = 0L
    private var lastFrameNanos = 0L
    private var lastMeasuredFps = 0

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: ProfileStore

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (windowStartNanos == 0L) windowStartNanos = frameTimeNanos
            if (lastFrameNanos > 0) {
                frameCount++
                val elapsed = frameTimeNanos - windowStartNanos
                if (elapsed >= 500_000_000L) { // refresh readout every ~0.5 s
                    lastMeasuredFps = (frameCount * 1_000_000_000.0 / elapsed).roundToInt()
                    frameCount = 0
                    windowStartNanos = frameTimeNanos
                }
            }
            lastFrameNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            fpsText?.text = "$lastMeasuredFps fps"
            if (store.overlayShowPackage) {
                val fg = UsageDetect.foregroundPackage(this@BoostOverlayService, 20_000L)
                pkgText?.visibility = View.VISIBLE
                pkgText?.text = fg ?: if (UsageDetect.hasUsageAccess(this@BoostOverlayService))
                    "…" else "usage access off"
            } else {
                pkgText?.visibility = View.GONE
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = ProfileStore(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startInForeground()
        attachOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (t: Throwable) {
                // view already detached
            }
        }
        overlayView = null
        super.onDestroy()
    }

    private fun startInForeground() {
        val channelId = "pulseboost_overlay"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId, "FPS overlay", NotificationManager.IMPORTANCE_MIN,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopSelf = PendingIntent.getService(
            this, 1,
            Intent(this, BoostOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("PulseBoost Pro overlay active")
            .setContentText("Measuring real frame delivery")
            .setContentIntent(openApp)
            .addAction(
                Notification.Action.Builder(null, "Dismiss", stopSelf).build(),
            )
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun attachOverlay() {
        val layoutType = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 220
        }

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_fps, null)
        fpsText = view.findViewById(R.id.fps_value)
        pkgText = view.findViewById(R.id.fps_pkg)
        view.findViewById<ImageButton>(R.id.overlay_close).setOnClickListener {
            stopSelf()
        }
        attachDrag(view, params)

        windowManager.addView(view, params)
        overlayView = view
        Choreographer.getInstance().postFrameCallback(frameCallback)
        handler.post(ticker)
    }

    private fun attachDrag(view: View, params: WindowManager.LayoutParams) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        view.findViewById<View>(R.id.overlay_root).setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (!dragging && (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12)) {
                        dragging = true
                    }
                    if (dragging) {
                        params.x = (startX + dx).coerceAtLeast(0)
                        params.y = (startY + dy).coerceAtLeast(0)
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (t: Throwable) {
                            // detached mid-drag; ignore
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 0x50B1
        const val ACTION_STOP = "dev.pulseboost.pro.overlay.STOP"

        fun startIntent(context: Context): Intent =
            Intent(context, BoostOverlayService::class.java)
    }
}
