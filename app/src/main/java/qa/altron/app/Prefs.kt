package qa.altron.app

import android.content.Context
import android.content.SharedPreferences

/** Small key/value store for the device token and tracking settings (the token never leaves the app). */
class Prefs(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("altron", Context.MODE_PRIVATE)
    var token: String? get() = p.getString("token", null); set(v) { p.edit().putString("token", v).apply() }
    var userName: String get() = p.getString("user_name", "") ?: ""; set(v) { p.edit().putString("user_name", v).apply() }
    var userRole: String get() = p.getString("user_role", "staff") ?: "staff"; set(v) { p.edit().putString("user_role", v).apply() }
    var userPortal: String get() = p.getString("user_portal", "staff") ?: "staff"; set(v) { p.edit().putString("user_portal", v).apply() }
    var consentOk: Boolean get() = p.getBoolean("consent_ok", false); set(v) { p.edit().putBoolean("consent_ok", v).apply() }
    var setupDone: Boolean get() = p.getBoolean("setup_done", false); set(v) { p.edit().putBoolean("setup_done", v).apply() }
    var trackOn: Boolean get() = p.getBoolean("track_on", false); set(v) { p.edit().putBoolean("track_on", v).apply() }
    var intervalSec: Int get() = p.getInt("interval", 60); set(v) { p.edit().putInt("interval", v.coerceIn(20, 600)).apply() }
    var minMoveM: Int get() = p.getInt("min_move", 25); set(v) { p.edit().putInt("min_move", v.coerceIn(0, 500)).apply() }
    var lastStateCheck: Long get() = p.getLong("last_state", 0); set(v) { p.edit().putLong("last_state", v).apply() }
    var latestVersionName: String get() = p.getString("latest_ver", "") ?: ""; set(v) { p.edit().putString("latest_ver", v).apply() }
    var latestVersionCode: Int get() = p.getInt("latest_code", 0); set(v) { p.edit().putInt("latest_code", v).apply() }
    var fcmToken: String? get() = p.getString("fcm", null); set(v) { p.edit().putString("fcm", v).apply() }
    val deviceId: String get() {
        var id = p.getString("device_id", null)
        if (id == null) { id = java.util.UUID.randomUUID().toString().replace("-", "").take(24); p.edit().putString("device_id", id).apply() }
        return id
    }
    fun clear() { val d = deviceId; p.edit().clear().putString("device_id", d).apply() }
    val isLoggedIn get() = !token.isNullOrEmpty()
}
