package qa.altron.app

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTPS client for the Altron mobile API (no third-party libraries). All calls are blocking - use off the main thread. */
object Api {
    const val BASE = BuildConfig.BASE_URL
    const val UA_TAG = "AltronApp/" + BuildConfig.VERSION_NAME

    class Result(val code: Int, val json: JSONObject?) { val ok get() = json?.optBoolean("ok", false) == true }

    private fun open(ctx: Context, path: String, method: String, token: String?): HttpURLConnection {
        val c = (URL(BASE + path).openConnection() as HttpURLConnection)
        c.requestMethod = method; c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) $UA_TAG")
        c.setRequestProperty("X-App-Version", BuildConfig.VERSION_NAME)
        if (!token.isNullOrEmpty()) { c.setRequestProperty("Authorization", "Bearer $token"); c.setRequestProperty("X-Altron-Token", token) }
        return c
    }
    private fun read(c: HttpURLConnection): Result {
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
        val text = try { stream.bufferedReader().use(BufferedReader::readText) } catch (e: Exception) { "" }
        val j = try { JSONObject(text) } catch (e: Exception) { null }
        return Result(code, j)
    }
    fun get(ctx: Context, path: String, token: String?): Result = try { read(open(ctx, path, "GET", token)) } catch (e: Exception) { Result(0, null) }
    fun post(ctx: Context, path: String, token: String?, body: JSONObject): Result = try {
        val c = open(ctx, path, "POST", token); c.doOutput = true; c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        read(c)
    } catch (e: Exception) { Result(0, null) }

    fun login(ctx: Context, mobile: String, password: String, mode: String): Result {
        val p = Prefs(ctx)
        val b = JSONObject().put("mobile", mobile).put("password", password).put("mode", mode)
            .put("device_id", p.deviceId).put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            .put("platform", "android").put("app_version", BuildConfig.VERSION_NAME).put("os_version", Build.VERSION.RELEASE)
        return post(ctx, "/api/mobile/login.php", null, b)
    }
    fun state(ctx: Context, extra: JSONObject? = null): Result {
        val p = Prefs(ctx); val t = p.token ?: return Result(401, null)
        val b = extra ?: JSONObject()
        b.put("os_version", Build.VERSION.RELEASE)
        return post(ctx, "/api/mobile/state.php", t, b)
    }
    fun ping(ctx: Context, points: JSONArray): Result {
        val t = Prefs(ctx).token ?: return Result(401, null)
        return post(ctx, "/track_ping.php", t, JSONObject().put("points", points))
    }
    fun webCode(ctx: Context): String? {
        val t = Prefs(ctx).token ?: return null
        val r = post(ctx, "/api/mobile/web_code.php", t, JSONObject())
        return if (r.ok) r.json?.optString("web_code") else null
    }
    fun consent(ctx: Context): Boolean = post(ctx, "/api/mobile/consent.php", Prefs(ctx).token, JSONObject()).ok
    fun fcm(ctx: Context, fcmToken: String): Boolean = post(ctx, "/api/mobile/fcm.php", Prefs(ctx).token, JSONObject().put("fcm_token", fcmToken)).ok
    fun logout(ctx: Context) { post(ctx, "/api/mobile/logout.php", Prefs(ctx).token, JSONObject()) }

    /** Applies the "track" object of any reply to prefs; returns whether tracking should be on. */
    fun applyTrack(ctx: Context, j: JSONObject?): Boolean {
        val p = Prefs(ctx); if (j == null) return p.trackOn
        val t = j.optJSONObject("track") ?: j
        if (t.has("interval")) p.intervalSec = t.optInt("interval", 60)
        if (t.has("min_move")) p.minMoveM = t.optInt("min_move", 25)
        if (t.has("on")) p.trackOn = t.optBoolean("on", false)
        t.optJSONObject("app")?.let { a -> p.latestVersionName = a.optString("version", ""); p.latestVersionCode = a.optInt("build", 0) }
        p.lastStateCheck = System.currentTimeMillis()
        return p.trackOn
    }
}
