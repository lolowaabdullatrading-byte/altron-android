package qa.altron.app

import android.webkit.JavascriptInterface
import org.json.JSONObject

/** window.AltronNative - called by the web app (inc/track.php emits setTrackState on every staff page). */
class WebBridge(private val act: MainActivity) {
    @JavascriptInterface fun setTrackState(json: String) {
        try {
            val j = JSONObject(json)
            val t = JSONObject().put("on", j.optBoolean("on", false))
            if (j.has("iv")) t.put("interval", j.optInt("iv", 60))
            if (j.has("mv")) t.put("min_move", j.optInt("mv", 25))
            Api.applyTrack(act, t)
            act.runOnUiThread { TrackingService.sync(act) }
        } catch (e: Exception) {}
    }
    @JavascriptInterface fun getAppVersion(): String = BuildConfig.VERSION_NAME
    @JavascriptInterface fun isTracking(): Boolean = TrackingService.running
    @JavascriptInterface fun openSetup() { act.runOnUiThread { act.startActivity(android.content.Intent(act, SetupActivity::class.java)) } }
    @JavascriptInterface fun logout() { act.runOnUiThread { act.doLogout() } }
}
