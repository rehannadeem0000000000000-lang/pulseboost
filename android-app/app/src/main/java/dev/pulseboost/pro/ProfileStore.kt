package dev.pulseboost.pro

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences-backed storage for Feature D (per-app boost profiles)
 * and the user's boost selections.
 */
class ProfileStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pulseboost_profiles", Context.MODE_PRIVATE)

    /** Apps the user checked in the Apps tab (targets of manual boosts). */
    var selectedApps: MutableSet<String>
        get() = prefs.getStringSet(KEY_SELECTED, emptySet())?.toMutableSet() ?: mutableSetOf()
        set(value) {
            prefs.edit().putStringSet(KEY_SELECTED, value.toSet()).apply()
        }

    /** Apps with "auto-boost on launch" enabled. */
    var boostProfiles: MutableSet<String>
        get() = prefs.getStringSet(KEY_PROFILES, emptySet())?.toMutableSet() ?: mutableSetOf()
        set(value) {
            prefs.edit().putStringSet(KEY_PROFILES, value.toSet()).apply()
        }

    fun setProfileEnabled(pkg: String, enabled: Boolean) {
        val set = boostProfiles
        if (enabled) set.add(pkg) else set.remove(pkg)
        boostProfiles = set
    }

    fun isProfileEnabled(pkg: String): Boolean = boostProfiles.contains(pkg)

    fun setSelected(pkg: String, selected: Boolean) {
        val set = selectedApps
        if (selected) set.add(pkg) else set.remove(pkg)
        selectedApps = set
    }

    /** Master switch for BoostMonitorService. */
    var monitorEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITOR, false)
        set(value) {
            prefs.edit().putBoolean(KEY_MONITOR, value).apply()
        }

    /** Whether the FPS overlay shows the foreground package name. */
    var overlayShowPackage: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_PKG, true)
        set(value) {
            prefs.edit().putBoolean(KEY_OVERLAY_PKG, value).apply()
        }

    /** Timestamp + summary of the latest automatic boost, for the UI log. */
    var lastAutoBoost: String
        get() = prefs.getString(KEY_LAST_AUTO, "no automatic boost recorded yet") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LAST_AUTO, value).apply()
        }

    companion object {
        private const val KEY_SELECTED = "selected_apps"
        private const val KEY_PROFILES = "boost_profiles"
        private const val KEY_MONITOR = "monitor_enabled"
        private const val KEY_OVERLAY_PKG = "overlay_show_pkg"
        private const val KEY_LAST_AUTO = "last_auto_boost"
    }
}
