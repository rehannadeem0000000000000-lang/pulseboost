package dev.pulseboost.pro

import android.Manifest
import android.app.AlertDialog
import android.app.GameManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import java.util.Locale
import java.util.concurrent.Executors

/**
 * PulseBoost Pro main UI: five tabs —
 *   BOOST     Shizuku status + whole-device and per-app boost exec buttons
 *   STATS     live RAM / per-core CPU / battery telemetry (1 s refresh)
 *   APPS      installed launcher apps, selection + per-app exec actions +
 *             auto-boost profile switch per app
 *   OVERLAY   start/stop the Choreographer FPS overlay service
 *   SETTINGS  battery saver, DND, refresh rate, Game Mode, profiles, widget
 *
 * All privileged work goes through ShizukuManager (rikka.shizuku SDK).
 */
class MainActivity : Activity() {

    private enum class Section(val label: String) {
        BOOST("BOOST"), STATS("STATS"), APPS("APPS"), OVERLAY("OVERLAY"), SETTINGS("SETTINGS")
    }

    private data class AppEntry(val pkg: String, val label: String, val icon: Drawable)

    private object Ui {
        val BG: Int = Color.rgb(7, 9, 14)
        val PANEL: Int = Color.rgb(13, 16, 21)
        val STROKE: Int = Color.rgb(38, 46, 66)
        val TEXT: Int = Color.rgb(232, 240, 255)
        val DIM: Int = Color.rgb(120, 132, 158)
        val ACCENT: Int = Color.rgb(200, 255, 46) // lime
        val CYAN: Int = Color.rgb(62, 231, 255)
        val RED: Int = Color.rgb(255, 84, 110)
    }

    private lateinit var store: ProfileStore
    private val handler = Handler(Looper.getMainLooper())
    private val pool = Executors.newCachedThreadPool()
    private var currentSection = Section.BOOST
    private val panels = HashMap<Section, View>()
    private val tabViews = HashMap<Section, TextView>()

    private var shizukuPill: TextView? = null
    private var ramPill: TextView? = null
    private var contentFrame: FrameLayout? = null
    private var appListContainer: LinearLayout? = null
    private var selectedCountView: TextView? = null
    private var lastAutoBoostView: TextView? = null
    private val boostLog = StringBuilder()
    private var boostLogView: TextView? = null
    private var boostDeltaView: TextView? = null
    private var statsRefs: StatsRefs? = null

    private class StatsRefs {
        var ramBarUsed: View? = null
        var ramBarFree: View? = null
        var ramText: TextView? = null
        var ramDetail: TextView? = null
        var ramWarn: TextView? = null
        var cpuOverall: TextView? = null
        var cpuOverallBar: View? = null
        var coresContainer: LinearLayout? = null
        var batteryText: TextView? = null
        var batteryTemp: TextView? = null
        var batteryState: TextView? = null
    }

    private val shizukuBinderReceived = rikka.shizuku.Shizuku.OnBinderReceivedListener {
        runOnUiThread { refreshShizukuUi() }
    }
    private val shizukuBinderDead = rikka.shizuku.Shizuku.OnBinderDeadListener {
        runOnUiThread { refreshShizukuUi() }
    }
    private val shizukuPermResult = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { code, _ ->
        if (code == ShizukuManager.REQUEST_CODE) runOnUiThread { refreshShizukuUi() }
    }

    private val statsTicker = object : Runnable {
        override fun run() {
            tickStats()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ProfileStore(this)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        setContentView(R.layout.activity_main)

        shizukuPill = findViewById(R.id.shizuku_pill)
        ramPill = findViewById(R.id.ram_pill)
        contentFrame = findViewById(R.id.content_frame)

        buildTabs()
        attachShizukuListeners()
        selectSection(Section.BOOST)
        refreshShizukuUi()

        // Spec A: request Shizuku permission right on launch (real dialog
        // served by the Shizuku app).
        handler.postDelayed({
            if (ShizukuManager.binderAlive() && !ShizukuManager.hasPermission()) {
                ShizukuManager.requestPermission()
            }
        }, 700L)

        handler.postDelayed(statsTicker, 1000L)
    }

    override fun onResume() {
        super.onResume()
        refreshShizukuUi()
    }

    override fun onDestroy() {
        handler.removeCallbacks(statsTicker)
        try {
            rikka.shizuku.Shizuku.removeBinderReceivedListener(shizukuBinderReceived)
            rikka.shizuku.Shizuku.removeBinderDeadListener(shizukuBinderDead)
            rikka.shizuku.Shizuku.removeRequestPermissionResultListener(shizukuPermResult)
        } catch (t: Throwable) {
            // listeners were never attached (binder never arrived)
        }
        pool.shutdownNow()
        super.onDestroy()
    }

    private fun attachShizukuListeners() {
        try {
            rikka.shizuku.Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceived)
            rikka.shizuku.Shizuku.addBinderDeadListener(shizukuBinderDead)
            rikka.shizuku.Shizuku.addRequestPermissionResultListener(shizukuPermResult)
        } catch (t: Throwable) {
            // Shizuku client not initialised yet; binder-received listener
            // will fire once the provider brings the client up.
        }
    }

    // ============================ chrome =====================================

    private fun buildTabs() {
        val bar = findViewById<LinearLayout>(R.id.tab_bar)
        Section.values().forEach { section ->
            val tab = TextView(this).apply {
                text = section.label
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(Ui.DIM)
                gravity = Gravity.CENTER
                letterSpacing = 0.12f
                val lp = LinearLayout.LayoutParams(0, dp(40), 1f)
                layoutParams = lp
                setOnClickListener { selectSection(section) }
            }
            bar.addView(tab)
            tabViews[section] = tab
        }
    }

    private fun selectSection(section: Section) {
        currentSection = section
        tabViews.forEach { (s, v) ->
            val active = s == section
            v.setTextColor(if (active) Ui.ACCENT else Ui.DIM)
            v.background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                if (active) {
                    setColor(Color.argb(28, 200, 255, 46))
                    setStroke(dp(1), Ui.ACCENT)
                } else {
                    setColor(Color.TRANSPARENT)
                }
            }
        }
        contentFrame?.removeAllViews()
        contentFrame?.addView(panel(section))
        if (section == Section.APPS && appListContainer?.childCount == 0) {
            loadAppList()
        }
        if (section == Section.SETTINGS) refreshSettingsPanel()
    }

    private fun panel(section: Section): View =
        panels.getOrPut(section) {
            when (section) {
                Section.BOOST -> buildBoostPanel()
                Section.STATS -> buildStatsPanel()
                Section.APPS -> buildAppsPanel()
                Section.OVERLAY -> buildOverlayPanel()
                Section.SETTINGS -> buildSettingsPanel()
            }
        }

    // ============================ BOOST tab ==================================

    private var boostStatusTitle: TextView? = null
    private var boostStatusBody: TextView? = null
    private var batterySaverSwitch: Switch? = null

    private fun buildBoostPanel(): View {
        val root = scrollRoot()

        // -- Shizuku status card
        val statusCard = card()
        boostStatusTitle = title("SHIZUKU ENGINE").also { statusCard.addView(it) }
        boostStatusBody = body("…").also { statusCard.addView(it) }
        val grant = actionButton("GRANT SHIZUKU ACCESS", Ui.ACCENT) {
            if (!ShizukuManager.binderAlive()) {
                launchShizukuApp()
            } else {
                ShizukuManager.requestPermission()
            }
        }
        statusCard.addView(grant)
        root.addView(statusCard)

        // -- Boost now
        val boostCard = card()
        boostCard.addView(title("MEMORY BOOST"))
        boostCard.addView(body("Kills all background processes (am kill-all), reclaims app caches (pm trim-caches), then kills background processes of every app you checked on the APPS tab. Before/after RAM is read from ActivityManager.MemoryInfo."))
        val boostBtn = actionButton("BOOST NOW", Ui.ACCENT) { runMemoryBoost() }
        boostCard.addView(boostBtn)
        boostDeltaView = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(Ui.CYAN)
            setPadding(0, dp(6), 0, 0)
            text = "idle — no boost run yet in this session"
        }
        boostCard.addView(boostDeltaView)
        root.addView(boostCard)

        // -- individual device-level actions
        val deviceCard = card()
        deviceCard.addView(title("DEVICE ACTIONS"))
        deviceCard.addView(actionButton("TRIM ALL BACKGROUND APPS", Ui.CYAN) {
            runExec("am kill-all") { ShizukuManager.killAllBackground() }
        })
        deviceCard.addView(actionButton("CLEAR ALL APP CACHES", Ui.CYAN) {
            runExec("pm trim-caches 999G") { ShizukuManager.trimCaches() }
        })
        root.addView(deviceCard)

        // -- exec log
        val logCard = card()
        logCard.addView(title("EXEC LOG"))
        val logScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150),
            )
        }
        boostLogView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Ui.DIM)
            text = ""
        }
        logScroll.addView(boostLogView)
        logCard.addView(logScroll)
        root.addView(logCard)

        refreshShizukuUi()
        return wrapScroll(root)
    }

    private fun runMemoryBoost() {
        if (!ensureShizukuReady()) return
        executor {
            appendLog("── boost sequence start ──")
            val before = StatsReader.readRam(this)
            appendLog("before: free ${StatsReader.formatMb(before.availableBytes)} / total ${StatsReader.formatMb(before.totalBytes)}")
            logResult("am kill-all", ShizukuManager.killAllBackground())
            logResult("pm trim-caches 999G", ShizukuManager.trimCaches())
            val hogs = store.selectedApps.filter { it != packageName }
            hogs.forEach { pkg ->
                logResult("am kill $pkg", ShizukuManager.killBackground(pkg))
            }
            try {
                Thread.sleep(700)
            } catch (_: InterruptedException) {
            }
            val after = StatsReader.readRam(this)
            val deltaMb = (after.availableBytes - before.availableBytes) / (1024 * 1024)
            appendLog("after:  free ${StatsReader.formatMb(after.availableBytes)}")
            appendLog(
                if (deltaMb >= 0) "RESULT +$deltaMb MB reclaimed"
                else "RESULT $deltaMb MB (system re-cached quickly)",
            )
            runOnUiThread {
                boostDeltaView?.text =
                    "Δ free RAM: ${if (deltaMb >= 0) "+" else ""}$deltaMb MB  ·  " +
                        "free now ${StatsReader.formatMb(after.availableBytes)}"
                boostDeltaView?.setTextColor(if (deltaMb >= 0) Ui.ACCENT else Ui.RED)
            }
        }
    }

    private fun runExec(label: String, block: () -> ShizukuManager.ExecResult) {
        if (!ensureShizukuReady()) return
        executor {
            logResult(label, block())
        }
    }

    // ============================ STATS tab ==================================

    private fun buildStatsPanel(): View {
        val root = scrollRoot()
        val refs = StatsRefs()
        statsRefs = refs

        val ramCard = card()
        ramCard.addView(title("RAM · ACTIVITYMANAGER.MEMORYINFO"))
        refs.ramText = mono("-- / --").apply { textSize = 18f; setTextColor(Ui.TEXT) }
        ramCard.addView(refs.ramText)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10),
            ).apply { topMargin = dp(8) }
        }
        refs.ramBarUsed = barSlice(Ui.ACCENT)
        refs.ramBarFree = barSlice(Ui.STROKE)
        bar.addView(refs.ramBarUsed)
        bar.addView(refs.ramBarFree)
        ramCard.addView(bar)
        refs.ramDetail = body("…")
        ramCard.addView(refs.ramDetail)
        refs.ramWarn = body("").apply { setTextColor(Ui.RED) }
        ramCard.addView(refs.ramWarn)
        root.addView(ramCard)

        val cpuCard = card()
        cpuCard.addView(title("CPU · /PROC/STAT (1S DELTAS)"))
        refs.cpuOverall = mono("total: --%").apply { textSize = 15f; setTextColor(Ui.CYAN) }
        cpuCard.addView(refs.cpuOverall)
        refs.coresContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        cpuCard.addView(refs.coresContainer)
        cpuCard.addView(body("percentages update every second from real jiffy deltas"))
        root.addView(cpuCard)

        val batCard = card()
        batCard.addView(title("BATTERY · BATTERYMANAGER"))
        refs.batteryText = mono("--").apply { textSize = 15f; setTextColor(Ui.TEXT) }
        refs.batteryTemp = body("…")
        refs.batteryState = body("…")
        batCard.addView(refs.batteryText)
        batCard.addView(refs.batteryTemp)
        batCard.addView(refs.batteryState)
        root.addView(batCard)

        tickStats()
        return wrapScroll(root)
    }

    private fun barSlice(color: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(5).toFloat()
        }
    }

    private fun tickStats() {
        val ram = StatsReader.readRam(this)
        ramPill?.text = "FREE ${StatsReader.formatMb(ram.availableBytes)}"
        val refs = statsRefs ?: return
        if (currentSection != Section.STATS) return

        refs.ramText?.text = "${StatsReader.formatMb(ram.usedBytes)} used / ${StatsReader.formatMb(ram.totalBytes)}"
        refs.ramDetail?.text =
            "free ${StatsReader.formatMb(ram.availableBytes)} · threshold ${StatsReader.formatMb(ram.thresholdBytes)} · used ${ram.usedPercent}%"
        refs.ramWarn?.text = if (ram.lowMemory) "system reports LOW MEMORY state" else ""
        setBarWeights(refs.ramBarUsed, refs.ramBarFree, ram.usedPercent)

        val cpu = StatsReader.sampleCpu()
        refs.cpuOverall?.text = "total: ${cpu.overall}%"
        renderCores(refs, cpu.perCore)

        val bat = StatsReader.readBattery(this)
        refs.batteryText?.text =
            "${if (bat.levelPercent >= 0) "${bat.levelPercent}%" else "--"}  ·  ${bat.pluggedLabel}"
        refs.batteryTemp?.text = String.format(
            Locale.US, "temperature %.1f °C · voltage %d mV", bat.temperatureC, bat.voltageMv,
        )
        refs.batteryTemp?.setTextColor(if (bat.temperatureC >= 42f) Ui.RED else Ui.DIM)
        refs.batteryState?.text =
            "status: ${if (bat.charging) "charging" else "discharging"} · health ${bat.healthLabel}"
    }

    private fun setBarWeights(used: View?, free: View?, usedPercent: Int) {
        val u = usedPercent.coerceIn(0, 100)
        (used?.layoutParams as? LinearLayout.LayoutParams)?.weight = u.toFloat().coerceAtLeast(0.5f)
        (free?.layoutParams as? LinearLayout.LayoutParams)?.weight =
            (100 - u).toFloat().coerceAtLeast(0.5f)
        used?.requestLayout()
        free?.requestLayout()
    }

    private fun renderCores(refs: StatsRefs, perCore: List<Int>) {
        val container = refs.coresContainer ?: return
        if (container.childCount != perCore.size) {
            container.removeAllViews()
            perCore.indices.forEach { i ->
                val row = TextView(this).apply {
                    typeface = Typeface.MONOSPACE
                    textSize = 12f
                    setTextColor(Ui.DIM)
                    tag = i
                }
                container.addView(row)
            }
        }
        perCore.forEachIndexed { i, pct ->
            (container.getChildAt(i) as? TextView)?.apply {
                text = String.format(Locale.US, "core %d  %3d%%", i, pct)
                setTextColor(if (pct > 85) Ui.RED else Ui.DIM)
            }
        }
    }

    // ============================ APPS tab ===================================

    private fun buildAppsPanel(): View {
        val root = scrollRoot()

        val actionCard = card()
        actionCard.addView(title("BOOST SELECTED"))
        selectedCountView = body("0 selected — actions below target every checked app")
        actionCard.addView(selectedCountView)
        actionCard.addView(actionButton("FORCE-STOP SELECTED", Ui.ACCENT) {
            forEachSelected("am force-stop") { pkg -> ShizukuManager.forceStop(pkg) }
        })
        actionCard.addView(actionButton("TRIM MEMORY OF SELECTED", Ui.CYAN) {
            forEachSelected("am send-trim-memory … 80") { pkg -> ShizukuManager.trimMemory(pkg) }
        })
        actionCard.addView(actionButton("KILL BACKGROUND OF SELECTED", Ui.CYAN) {
            forEachSelected("am kill") { pkg -> ShizukuManager.killBackground(pkg) }
        })
        actionCard.addView(actionButton("CLEAR DATA + CACHE OF SELECTED", Ui.RED) {
            confirmClearSelected()
        })
        root.addView(actionCard)

        val listCard = card()
        listCard.addView(title("INSTALLED LAUNCHER APPS"))
        listCard.addView(body("check = include in boost target list · the switch enables the per-app auto-boost profile (Feature D)"))
        appListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listCard.addView(appListContainer)
        root.addView(listCard)
        return wrapScroll(root)
    }

    private fun loadAppList() {
        val container = appListContainer ?: return
        val loading = body("reading PackageManager…")
        container.removeAllViews()
        container.addView(loading)
        executor {
            val entries = queryLauncherApps()
            runOnUiThread {
                container.removeAllViews()
                entries.forEach { entry -> container.addView(appRow(entry)) }
                refreshSelectedCount()
            }
        }
    }

    private fun queryLauncherApps(): List<AppEntry> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return infos.mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            try {
                AppEntry(
                    pkg = pkg,
                    label = ri.loadLabel(pm)?.toString() ?: pkg,
                    icon = ri.loadIcon(pm),
                )
            } catch (t: Throwable) {
                null
            }
        }.distinctBy { it.pkg }
            .sortedWith(
                compareByDescending<AppEntry> { store.selectedApps.contains(it.pkg) }
                    .thenBy { it.label.lowercase(Locale.US) },
            )
    }

    private fun appRow(entry: AppEntry): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val check = CheckBox(this).apply {
            isChecked = store.selectedApps.contains(entry.pkg)
        }
        check.setOnCheckedChangeListener { _, checked ->
            store.setSelected(entry.pkg, checked)
            refreshSelectedCount()
        }
        val icon = ImageView(this).apply {
            setImageDrawable(entry.icon)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginStart = dp(6)
                marginEnd = dp(10)
            }
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(this).apply {
            text = entry.label
            setTextColor(Ui.TEXT)
            textSize = 14f
            maxLines = 1
        })
        texts.addView(TextView(this).apply {
            text = entry.pkg
            setTextColor(Ui.DIM)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            maxLines = 1
        })
        val profileSwitch = Switch(this).apply {
            isChecked = store.isProfileEnabled(entry.pkg)
        }
        profileSwitch.setOnCheckedChangeListener { _, checked ->
            store.setProfileEnabled(entry.pkg, checked)
            if (checked) {
                toast("auto-boost on launch enabled for ${entry.label} — enable the monitor in SETTINGS")
            }
        }
        row.addView(check)
        row.addView(icon)
        row.addView(texts)
        row.addView(profileSwitch)
        return row
    }

    private fun refreshSelectedCount() {
        selectedCountView?.text =
            "${store.selectedApps.size} selected — actions target every checked app"
    }

    private fun forEachSelected(label: String, block: (String) -> ShizukuManager.ExecResult) {
        if (!ensureShizukuReady()) return
        val targets = store.selectedApps.filter { it != packageName }
        if (targets.isEmpty()) {
            toast("check at least one app in the list")
            return
        }
        executor {
            targets.forEach { pkg ->
                logResult("$label $pkg", block(pkg))
            }
        }
    }

    private fun confirmClearSelected() {
        val targets = store.selectedApps.filter { it != packageName }
        if (targets.isEmpty()) {
            toast("check at least one app in the list")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Clear app data?")
            .setMessage(
                "pm clear wipes ALL data (settings, accounts, files) plus cache for " +
                    "${targets.size} app(s). This is the Android-sanctioned way to clear " +
                    "another app's storage and cannot be undone. Continue?",
            )
            .setPositiveButton("CLEAR") { _, _ ->
                forEachSelected("pm clear") { pkg -> ShizukuManager.clearAppData(pkg) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // =========================== OVERLAY tab =================================

    private var overlayStatusView: TextView? = null

    private fun buildOverlayPanel(): View {
        val root = scrollRoot()
        val c = card()
        c.addView(title("FPS OVERLAY"))
        c.addView(body("A draggable TYPE_APPLICATION_OVERLAY window. FPS is measured with Choreographer.FrameCallback upstream of real frame delivery; the second line shows the current foreground package (via UsageStats)."))
        overlayStatusView = body("…")
        c.addView(overlayStatusView)
        c.addView(actionButton("START OVERLAY", Ui.ACCENT) { startOverlayFlow() })
        c.addView(actionButton("STOP OVERLAY", Ui.CYAN) {
            stopService(Intent(this, BoostOverlayService::class.java))
            refreshOverlayStatus()
        })
        val toggle = Switch(this).apply {
            text = "show foreground package name"
            setTextColor(Ui.TEXT)
            textSize = 13f
            isChecked = store.overlayShowPackage
        }
        toggle.setOnCheckedChangeListener { _, checked ->
            store.overlayShowPackage = checked
        }
        c.addView(toggle)
        root.addView(c)
        refreshOverlayStatus()
        return wrapScroll(root)
    }

    private fun startOverlayFlow() {
        if (!Settings.canDrawOverlays(this)) {
            toast("grant 'display over other apps' first")
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }
        ensureNotificationPermission()
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(BoostOverlayService.startIntent(this))
        } else {
            startService(BoostOverlayService.startIntent(this))
        }
        refreshOverlayStatus()
    }

    private fun refreshOverlayStatus() {
        overlayStatusView?.text =
            "overlay permission: " +
                (if (Settings.canDrawOverlays(this)) "granted" else "not granted") +
                " · usage access: " +
                (if (UsageDetect.hasUsageAccess(this)) "granted" else "not granted")
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 77)
        }
    }

    // =========================== SETTINGS tab ================================

    private var saverState: TextView? = null
    private var dndState: TextView? = null
    private var refreshState: TextView? = null
    private var gameState: TextView? = null
    private var usageState: TextView? = null
    private var monitorSwitch: Switch? = null

    private fun buildSettingsPanel(): View {
        val root = scrollRoot()

        // battery saver
        val saverCard = card()
        saverCard.addView(title("BATTERY SAVER"))
        saverState = body("…")
        saverCard.addView(saverState)
        batterySaverSwitch = Switch(this).apply {
            text = "battery saver enabled"
            setTextColor(Ui.TEXT)
            textSize = 13f
        }
        batterySaverSwitch?.setOnCheckedChangeListener { _, checked ->
            if (!ShizukuManager.hasPermission()) {
                toast("requires Shizuku permission")
                refreshShizukuUi()
                return@setOnCheckedChangeListener
            }
            executor {
                logResult(
                    "settings put global low_power_mode ${if (checked) 1 else 0}",
                    ShizukuManager.setBatterySaver(checked),
                )
                val actual = ShizukuManager.isBatterySaverOn()
                runOnUiThread {
                    saverState?.text = "actual state reported by device: " +
                        (actual?.let { if (it) "ON" else "OFF" } ?: "unreadable")
                }
            }
        }
        saverCard.addView(batterySaverSwitch)
        saverCard.addView(body("writes Settings.Global low_power_mode through the Shizuku shell"))
        root.addView(saverCard)

        // DND
        val dndCard = card()
        dndCard.addView(title("DO NOT DISTURB"))
        dndState = body("…")
        dndCard.addView(dndState)
        dndCard.addView(actionButton("TOGGLE DND", Ui.CYAN) { toggleDnd() })
        dndCard.addView(actionButton("GRANT POLICY ACCESS", Ui.DIM) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        })
        root.addView(dndCard)

        // refresh rate
        val refreshCard = card()
        refreshCard.addView(title("SCREEN REFRESH RATE"))
        refreshState = body("…")
        refreshCard.addView(refreshState)
        refreshCard.addView(actionButton("SET MAX REFRESH RATE", Ui.ACCENT) {
            refreshState?.text = applyMaxRefreshRate()
        })
        refreshCard.addView(body("uses Display.Mode via window attributes (API 23+); on devices that do not expose extra modes the call reports the outcome instead of hiding it"))
        root.addView(refreshCard)

        // game mode — real section, hidden entirely below API 31
        if (GameModeHelper.isAvailable()) {
            val gameCard = card()
            gameCard.addView(title("GAME MODE · GAMEMANAGER API"))
            gameState = body("…")
            gameCard.addView(gameState)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listOf(
                "STANDARD" to GameManager.GAME_MODE_STANDARD,
                "PERFORMANCE" to GameManager.GAME_MODE_PERFORMANCE,
                "BATTERY" to GameManager.GAME_MODE_BATTERY,
            ).forEach { (label, mode) ->
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                val b = actionButton(label, if (mode == GameManager.GAME_MODE_PERFORMANCE) Ui.ACCENT else Ui.CYAN) {
                    val r = GameModeHelper.setMode(this, mode)
                    gameState?.text = r.message
                    refreshGameMode()
                }
                b.layoutParams = lp.apply { marginEnd = dp(6) }
                row.addView(b)
            }
            gameCard.addView(row)
            root.addView(gameCard)
            refreshGameMode()
        }

        // profiles / monitor
        val profileCard = card()
        profileCard.addView(title("AUTO-BOOST PROFILES"))
        usageState = body("…")
        profileCard.addView(usageState)
        monitorSwitch = Switch(this).apply {
            text = "monitor foreground apps & auto-boost"
            setTextColor(Ui.TEXT)
            textSize = 13f
            isChecked = store.monitorEnabled
        }
        monitorSwitch?.setOnCheckedChangeListener { _, checked ->
            store.monitorEnabled = checked
            if (checked) {
                if (!UsageDetect.hasUsageAccess(this)) {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                ensureNotificationPermission()
                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(BoostMonitorService.startIntent(this))
                } else {
                    startService(BoostMonitorService.startIntent(this))
                }
            } else {
                stopService(Intent(this, BoostMonitorService::class.java))
            }
            refreshSettingsPanel()
        }
        profileCard.addView(monitorSwitch)
        profileCard.addView(actionButton("GRANT USAGE ACCESS", Ui.DIM) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        lastAutoBoostView = body(store.lastAutoBoost).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
        }
        profileCard.addView(lastAutoBoostView)
        root.addView(profileCard)

        // ignoring battery optimizations
        val dozeCard = card()
        dozeCard.addView(title("KEEP SERVICES ALIVE"))
        dozeCard.addView(body("request exemption from battery optimization so the monitor and overlay stay scheduled"))
        dozeCard.addView(actionButton("REQUEST EXEMPTION", Ui.DIM) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName"),
                    ),
                )
            } catch (t: Throwable) {
                toast("not accepted by this ROM: ${t.javaClass.simpleName}")
            }
        })
        root.addView(dozeCard)

        // widget + honesty
        val infoCard = card()
        infoCard.addView(title("HOME SCREEN WIDGET"))
        infoCard.addView(body("long-press the launcher → Widgets → PulseBoost Pro. The widget re-reads RAM every 30 s and its BOOST button runs kill-all + trim-caches through Shizuku."))
        infoCard.addView(title("HARD LIMITS"))
        infoCard.addView(body("cannot change the CPU governor or clock speeds without root · cannot exceed the panel's hardware refresh rate · boost actions need the Shizuku app installed and started (via ADB once, or root) · pm clear wipes app data — always confirmed first"))
        root.addView(infoCard)

        refreshSettingsPanel()
        return wrapScroll(root)
    }

    private fun refreshSettingsPanel() {
        val nm = getSystemService(NotificationManager::class.java)
        dndState?.text = if (nm?.isNotificationPolicyAccessGranted == true) {
            "policy access granted · current filter: " + dndFilterLabel(nm.currentInterruptionFilter)
        } else {
            "notification policy access not granted"
        }
        usageState?.text = "usage access: " +
            (if (UsageDetect.hasUsageAccess(this)) "granted" else "not granted (needed for profiles)")
        lastAutoBoostView?.text = store.lastAutoBoost
        val r = currentRefreshRate()
        refreshState?.text = "current mode: $r"
        refreshShizukuUi()
    }

    private fun refreshGameMode() {
        if (!GameModeHelper.isAvailable()) return
        val r = GameModeHelper.getMode(this)
        gameState?.text = if (r.success) {
            "current game mode for $packageName: ${GameModeHelper.modeLabel(r.mode)}"
        } else {
            r.message
        }
    }

    private fun dndFilterLabel(filter: Int): String = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> "ALL"
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "PRIORITY"
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "ALARMS"
        NotificationManager.INTERRUPTION_FILTER_NONE -> "NONE"
        else -> "UNKNOWN"
    }

    private fun toggleDnd() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) {
            toast("grant notification policy access first")
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        val on = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL,
        )
        refreshSettingsPanel()
    }

    @Suppress("DEPRECATION")
    private fun currentDisplay() =
        if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay

    private fun currentRefreshRate(): String {
        return try {
            val d = currentDisplay()
            if (d == null) "no display"
            else String.format(Locale.US, "%.0f Hz (mode %d)", d.mode.refreshRate, d.mode.modeId)
        } catch (t: Throwable) {
            "unreadable: ${t.javaClass.simpleName}"
        }
    }

    private fun applyMaxRefreshRate(): String {
        return try {
            val d = currentDisplay() ?: return "no display handle"
            val modes = d.supportedModes
            if (modes == null || modes.isEmpty()) return "this device exposes no display modes"
            val best = modes.maxByOrNull { it.refreshRate } ?: return "no modes"
            val current = d.mode
            if (current.modeId == best.modeId) {
                return String.format(
                    Locale.US,
                    "already at hardware max %.0f Hz — nothing to change",
                    best.refreshRate,
                )
            }
            val lp = window.attributes
            lp.preferredDisplayModeId = best.modeId
            window.attributes = lp
            String.format(
                Locale.US,
                "requested mode %d (%.0f Hz) — OEM decides whether the panel switches",
                best.modeId, best.refreshRate,
            )
        } catch (t: Throwable) {
            "unsupported on this device: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    // =========================== shared helpers ==============================

    private fun refreshShizukuUi() {
        val alive = ShizukuManager.binderAlive()
        val granted = ShizukuManager.hasPermission()
        shizukuPill?.apply {
            text = when {
                granted -> "SHIZUKU · READY"
                alive -> "SHIZUKU · NEED PERMISSION"
                else -> "SHIZUKU · OFFLINE"
            }
            setTextColor(
                when {
                    granted -> Ui.ACCENT
                    alive -> Color.rgb(255, 200, 87)
                    else -> Ui.RED
                },
            )
        }
        boostStatusTitle?.text = if (granted) "SHIZUKU ENGINE · CONNECTED" else "SHIZUKU ENGINE"
        boostStatusBody?.text = when {
            granted -> "server v${ShizukuManager.serverVersion()} · uid ${ShizukuManager.serverUid()} " +
                (if (ShizukuManager.serverUid() == 0) "(root — full power)" else "(shell via adb — full boost power)") +
                "\npermission granted — all boost actions are live"
            alive -> "Shizuku is running but has not granted PulseBoost Pro access yet.\nTap GRANT and accept the dialog from the Shizuku app."
            else -> "Shizuku server not reachable.\nInstall Shizuku (Play Store / GitHub), then start it once via root or:\nadb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"
        }
        if (granted && batterySaverSwitch != null) {
            executor {
                val state = ShizukuManager.isBatterySaverOn()
                runOnUiThread {
                    batterySaverSwitch?.setOnCheckedChangeListener(null)
                    batterySaverSwitch?.isChecked = state == true
                    // re-attach
                    batterySaverSwitch?.setOnCheckedChangeListener { _, checked ->
                        executor {
                            ShizukuManager.setBatterySaver(checked)
                            val actual = ShizukuManager.isBatterySaverOn()
                            runOnUiThread {
                                saverState?.text = "actual state: " +
                                    (actual?.let { if (it) "ON" else "OFF" } ?: "unreadable")
                            }
                        }
                    }
                    saverState?.text = "actual state: " +
                        (state?.let { if (it) "ON" else "OFF" } ?: "unreadable")
                }
            }
        } else {
            saverState?.text = "requires Shizuku permission to read/write"
        }
        refreshSelectedCount()
        refreshOverlayStatus()
    }

    private fun ensureShizukuReady(): Boolean {
        if (ShizukuManager.hasPermission()) return true
        toast(
            if (ShizukuManager.binderAlive()) {
                "grant Shizuku access first (BOOST tab)"
            } else {
                "start Shizuku first — see BOOST tab"
            },
        )
        return false
    }

    private fun launchShizukuApp() {
        val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (intent != null) {
            startActivity(intent)
        } else {
            toast("Shizuku app is not installed")
        }
    }

    private fun executor(block: () -> Unit) {
        pool.execute {
            try {
                block()
            } catch (t: Throwable) {
                runOnUiThread { toast("error: ${t.javaClass.simpleName}") }
            }
        }
    }

    private fun logResult(label: String, result: ShizukuManager.ExecResult) {
        appendLog("$ $label")
        if (result.output.isNotBlank()) appendLog(result.output)
        appendLog("exit=${result.exitCode}")
    }

    @Synchronized
    private fun appendLog(line: String) {
        boostLog.append(line).append('\n')
        if (boostLog.length > 6000) boostLog.delete(0, boostLog.length - 5000)
        runOnUiThread {
            boostLogView?.text = boostLog.toString()
        }
    }

    private fun toast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    // ---- view construction helpers -----------------------------------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(8))
        background = GradientDrawable().apply {
            setColor(Ui.PANEL)
            cornerRadius = dp(16).toFloat()
            setStroke(1, Ui.STROKE)
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(12) }
    }

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        typeface = Typeface.MONOSPACE
        setTextColor(Ui.ACCENT)
        letterSpacing = 0.14f
        setPadding(0, 0, 0, dp(6))
    }

    private fun body(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12.5f
        setTextColor(Ui.DIM)
        setPadding(0, dp(4), 0, dp(6))
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    private fun mono(text: String): TextView = TextView(this).apply {
        this.text = text
        typeface = Typeface.MONOSPACE
        setPadding(0, dp(2), 0, dp(4))
    }

    private fun actionButton(label: String, color: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(color)
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.argb(20, Color.red(color), Color.green(color), Color.blue(color)))
                setStroke(1, color)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44),
            ).apply { topMargin = dp(6) }
            setOnClickListener { onClick() }
        }

    private fun scrollRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(24))
    }

    private fun wrapScroll(content: LinearLayout): View = ScrollView(this).apply {
        addView(content)
        isFillViewport = false
    }
}
