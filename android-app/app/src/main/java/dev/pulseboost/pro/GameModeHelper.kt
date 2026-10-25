package dev.pulseboost.pro

import android.app.GameManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Wrapper around android.app.GameManager (API 31+).
 *
 * Public surface per the SDK stubs:
 *   - getGameMode()                         → reads the mode for our package
 *   - setGameState(GameState) (API 33+)     → tells the system "we are in
 *     gameplay right now", which is the supported signal game dashboards
 *     use to switch into performance behaviour
 *
 * The OEM-facing setGameMode(String, int) is hidden from the public SDK, so
 * it is invoked through reflection and every failure (hidden-API policy,
 * SecurityException on devices that restrict MANAGE_GAME_MODE) is surfaced
 * verbatim instead of claiming the mode changed. Below API 31 all calls
 * report unavailability and MainActivity hides the section.
 */
object GameModeHelper {

    private const val TAG = "GameModeHelper"

    fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= 31

    data class GameModeResult(
        val success: Boolean,
        val mode: Int,
        val message: String,
    )

    fun modeLabel(mode: Int): String = when (mode) {
        GameManager.GAME_MODE_STANDARD -> "STANDARD"
        GameManager.GAME_MODE_PERFORMANCE -> "PERFORMANCE"
        GameManager.GAME_MODE_BATTERY -> "BATTERY"
        GameManager.GAME_MODE_CUSTOM -> "CUSTOM"
        else -> "UNSUPPORTED"
    }

    /** Reads the current Game Mode via the public API. */
    fun getMode(context: Context): GameModeResult {
        if (!isAvailable()) {
            return GameModeResult(false, -1, "GameManager needs Android 12 (API 31)+")
        }
        return try {
            val gm = context.getSystemService(GameManager::class.java)
            GameModeResult(true, gm.getGameMode(), "ok")
        } catch (t: Throwable) {
            Log.w(TAG, "getGameMode failed", t)
            GameModeResult(false, -1, "${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /**
     * Requests a Game Mode for [pkg]. Three stages, each reported honestly:
     *  1. public setGameState signal on API 33+ when PERFORMANCE is chosen
     *  2. reflective call to the hidden GameManager.setGameMode(String, int)
     *  3. public getGameMode() re-read to display what the device reports now
     */
    fun setMode(
        context: Context,
        mode: Int,
        pkg: String = context.packageName,
    ): GameModeResult {
        if (!isAvailable()) {
            return GameModeResult(false, -1, "GameManager needs Android 12 (API 31)+")
        }
        val gm = context.getSystemService(GameManager::class.java)
            ?: return GameModeResult(false, -1, "GameManager service unavailable")

        val notes = ArrayList<String>(3)

        if (Build.VERSION.SDK_INT >= 33 && mode == GameManager.GAME_MODE_PERFORMANCE) {
            try {
                val state = android.app.GameState(
                    false, android.app.GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE,
                )
                gm.setGameState(state)
                notes.add("setGameState(MODE_GAMEPLAY_UNINTERRUPTIBLE) accepted — gameplay signalled to the system")
            } catch (t: Throwable) {
                notes.add("setGameState rejected: ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        return try {
            val method = GameManager::class.java.getDeclaredMethod(
                "setGameMode", String::class.java, Int::class.javaPrimitiveType,
            )
            method.isAccessible = true
            method.invoke(gm, pkg, mode)
            val now = gm.getGameMode()
            notes.add("setGameMode(${modeLabel(mode)}) accepted by GameManager")
            notes.add("getGameMode() now reports ${modeLabel(now)}")
            GameModeResult(true, now, notes.joinToString("\n"))
        } catch (t: Throwable) {
            val root = generateSequence(t as Throwable?) { it.cause }.last()
            Log.w(TAG, "setGameMode unavailable", root)
            val now = try {
                gm.getGameMode()
            } catch (_: Throwable) {
                -1
            }
            notes.add(
                "hidden setGameMode rejected: ${root.javaClass.simpleName}: ${root.message}\n" +
                    "this device reserves game-mode control for its system game manager",
            )
            notes.add("getGameMode() reports ${modeLabel(now)}")
            GameModeResult(false, now, notes.joinToString("\n"))
        }
    }
}
