package dev.pulseboost.pro

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.InputStream

/**
 * Thin wrapper around the Shizuku API (dev.rikka.shizuku:api:13.1.5).
 *
 * Every boost action in this app funnels through [exec], which spawns a
 * process inside Shizuku's privileged server via [Shizuku.newProcess].
 * The server runs with shell (ADB, uid 2000) or root privileges, which is
 * what makes `am force-stop`, `pm clear`, `pm trim-caches`, `am kill-all`
 * and secure `settings put` calls work for arbitrary packages.
 */
object ShizukuManager {

    private const val TAG = "ShizukuManager"
    const val REQUEST_CODE: Int = 0x50B7

    data class ExecResult(val exitCode: Int, val output: String) {
        val ok: Boolean get() = exitCode == 0
        fun describe(command: List<String>): String =
            "$ ${command.joinToString(" ")}\n" +
                (if (output.isBlank()) "(no output)" else output) +
                "\nexit=$exitCode"
    }

    /** True when the Shizuku server binder is reachable. */
    fun binderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        Log.w(TAG, "pingBinder failed", t)
        false
    }

    fun serverVersion(): Int = try {
        if (binderAlive()) Shizuku.getVersion() else -1
    } catch (t: Throwable) {
        -1
    }

    /** UID the Shizuku server runs as (2000 = shell started via ADB, 0 = root). */
    fun serverUid(): Int = try {
        if (hasPermission()) Shizuku.getUid() else -1
    } catch (t: Throwable) {
        -1
    }

    fun isPreV11(): Boolean = try {
        Shizuku.isPreV11()
    } catch (t: Throwable) {
        true
    }

    fun hasPermission(): Boolean = try {
        binderAlive() && !isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun shouldShowRationale(): Boolean = try {
        Shizuku.shouldShowRequestPermissionRationale()
    } catch (t: Throwable) {
        false
    }

    /** Shows the real Shizuku permission dialog (from the Shizuku app). */
    fun requestPermission() {
        if (!binderAlive() || isPreV11() || hasPermission()) return
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (t: Throwable) {
            Log.w(TAG, "requestPermission failed", t)
        }
    }

    /**
     * Shizuku.newProcess is private in the published client jar; the SDK's
     * documented access pattern is reflective. ProGuard keeps every member
     * of rikka.shizuku.*, so the lookup stays stable after minification.
     */
    private val newProcessMethod: java.lang.reflect.Method? by lazy {
        try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }
        } catch (t: Throwable) {
            Log.w(TAG, "Shizuku.newProcess lookup failed", t)
            null
        }
    }

    /**
     * Runs a command inside the Shizuku server process and captures its
     * output. Blocks until the command exits, so callers in MainActivity and
     * the widget dispatch onto background threads before invoking this.
     */
    fun exec(cmd: List<String>): ExecResult {
        if (!binderAlive()) return ExecResult(-1, "Shizuku server is not running")
        if (!hasPermission()) return ExecResult(-1, "Shizuku permission not granted to PulseBoost Pro")
        val method = newProcessMethod
            ?: return ExecResult(-1, "Shizuku.newProcess unavailable in this client build")
        return try {
            val process = method.invoke(null, cmd.toTypedArray(), null, null) as Process
            val out = readFully(process.inputStream)
            val err = readFully(process.errorStream)
            val code = process.waitFor()
            ExecResult(code, (out + if (err.isNotBlank()) "\n$err" else "").trim())
        } catch (t: Throwable) {
            Log.w(TAG, "exec failed for ${cmd.joinToString(" ")}", t)
            ExecResult(-1, "exec failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    // ---- Targeted app actions (Feature A) -----------------------------------

    /** Force-stops [pkg]: am force-stop. */
    fun forceStop(pkg: String): ExecResult = exec(listOf("am", "force-stop", pkg))

    /** Clears all data & cache held by [pkg]: pm clear. */
    fun clearAppData(pkg: String): ExecResult = exec(listOf("pm", "clear", pkg))

    /**
     * Asks [pkg] to release memory: am send-trim-memory <pkg> 80
     * (80 = ComponentCallbacks2.TRIM_MEMORY_COMPLETE).
     */
    fun trimMemory(pkg: String): ExecResult =
        exec(listOf("am", "send-trim-memory", pkg, "80"))

    /** Kills background processes of [pkg] without touching its state: am kill. */
    fun killBackground(pkg: String): ExecResult = exec(listOf("am", "kill", pkg))

    // ---- Whole-device actions (Feature B) ------------------------------------

    /** Kills every background process on the device: am kill-all. */
    fun killAllBackground(): ExecResult = exec(listOf("am", "kill-all"))

    /** Reclaims cached app data system-wide: pm trim-caches 999G. */
    fun trimCaches(): ExecResult = exec(listOf("pm", "trim-caches", "999G"))

    /** Toggles Battery Saver through the secure settings table. */
    fun setBatterySaver(on: Boolean): ExecResult =
        exec(listOf("settings", "put", "global", "low_power_mode", if (on) "1" else "0"))

    /** Reads the current battery saver flag back from the settings table. */
    fun isBatterySaverOn(): Boolean? {
        val r = exec(listOf("settings", "get", "global", "low_power_mode"))
        return when (r.output.trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    private fun readFully(stream: InputStream): String = try {
        stream.bufferedReader(Charsets.UTF_8).readText()
    } catch (t: Throwable) {
        ""
    }
}
