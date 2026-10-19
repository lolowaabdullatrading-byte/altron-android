package qa.altron.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.concurrent.Executors

/** Guided permissions: Location (fine) -> Location "All the time" -> Notifications -> Battery unrestricted -> brand autostart hint. */
class SetupActivity : AppCompatActivity() {
    private lateinit var stLoc: TextView; private lateinit var stBg: TextView; private lateinit var stNotif: TextView; private lateinit var stBat: TextView
    private lateinit var bLoc: Button; private lateinit var bBg: Button; private lateinit var bNotif: Button; private lateinit var bBat: Button; private lateinit var bDone: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        stLoc = findViewById(R.id.stLoc); stBg = findViewById(R.id.stBg); stNotif = findViewById(R.id.stNotif); stBat = findViewById(R.id.stBat)
        bLoc = findViewById(R.id.bLoc); bBg = findViewById(R.id.bBg); bNotif = findViewById(R.id.bNotif); bBat = findViewById(R.id.bBat); bDone = findViewById(R.id.bDone)
        findViewById<TextView>(R.id.brandHint).text = brandHint()
        bLoc.setOnClickListener { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1) }
        bBg.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 30) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            else if (Build.VERSION.SDK_INT >= 29) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
        }
        bNotif.setOnClickListener { if (Build.VERSION.SDK_INT >= 33) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3) }
        bBat.setOnClickListener {
            try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
            catch (e: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
        findViewById<Button>(R.id.bAutostart).setOnClickListener { openAutostart() }
        bDone.setOnClickListener {
            Prefs(this).setupDone = true
            report()
            startActivity(Intent(this, MainActivity::class.java).apply { intent.extras?.let { putExtras(it) } }); finish()
        }
    }
    override fun onResume() { super.onResume(); refresh() }
    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) { super.onRequestPermissionsResult(rc, p, g); refresh() }

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun batteryOk(): Boolean = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
    private fun refresh() {
        val loc = has(Manifest.permission.ACCESS_FINE_LOCATION)
        val bg = Build.VERSION.SDK_INT < 29 || has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        val notif = Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)
        val bat = batteryOk()
        fun mark(tv: TextView, ok: Boolean, b: Button) { tv.text = if (ok) "✅ Done" else "⚠️ Needed"; b.isEnabled = !ok }
        mark(stLoc, loc, bLoc); mark(stBg, bg, bBg); mark(stNotif, notif, bNotif); mark(stBat, bat, bBat)
        bBg.isEnabled = loc && !bg
        bDone.text = if (loc && bg && bat) "Continue" else "Continue anyway (tracking may not work)"
    }
    private fun report() {
        val j = JSONObject().put("perm_location", if (has(Manifest.permission.ACCESS_FINE_LOCATION)) "fine" else "denied")
            .put("perm_bg", if (Build.VERSION.SDK_INT < 29 || has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) "always" else if (has(Manifest.permission.ACCESS_FINE_LOCATION)) "while_in_use" else "denied")
            .put("battery_ok", batteryOk())
        Executors.newSingleThreadExecutor().execute { Api.state(this, j) }
    }
    private fun brandHint(): String {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> "Xiaomi / Redmi / POCO: Settings → Apps → Manage apps → Altron → enable Autostart, and Battery saver → No restrictions."
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> "OPPO / Realme / OnePlus: Settings → Battery → App battery management → Altron → Allow background activity; Startup manager → Altron ON."
            m.contains("vivo") || m.contains("iqoo") -> "vivo / iQOO: i Manager → App manager → Autostart → Altron ON; Battery → Background power consumption → allow Altron."
            m.contains("huawei") || m.contains("honor") -> "Huawei / Honor: Settings → Battery → App launch → Altron → Manage manually → enable all three."
            m.contains("samsung") -> "Samsung: Settings → Battery → Background usage limits → make sure Altron is NOT in 'Sleeping apps' or 'Deep sleeping apps'."
            else -> "Some phones stop apps in the background to save battery. If tracking stops while your phone is locked, open Settings → Apps → Altron → Battery and choose 'Unrestricted' / 'Allow background activity'."
        }
    }
    private fun openAutostart() {
        val intents = listOf(
            Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            Intent().setClassName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
            Intent().setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Intent().setClassName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
        for (i in intents) { try { startActivity(i); return } catch (e: Exception) {} }
    }
}
