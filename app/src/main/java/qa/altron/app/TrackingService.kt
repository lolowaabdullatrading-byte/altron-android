package qa.altron.app

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import java.util.concurrent.Executors

/**
 * Foreground service = the only way Android lets an app keep GPS running with the screen off.
 * Runs ONLY while the server says tracking is on (person checked in + policy applies); every upload reply
 * carries on:false -> the service stops itself. Points are buffered in SQLite and uploaded in batches.
 */
class TrackingService : Service() {
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var queue: LocationQueue
    private lateinit var prefs: Prefs
    private val io = Executors.newSingleThreadExecutor()
    private var lastUpload = 0L
    private var lastState = 0L
    @Volatile private var uploading = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) {
            val l = r.lastLocation ?: return
            if (l.accuracy > 500f) return
            queue.add(l.latitude, l.longitude, l.accuracy.toInt(), if (l.hasSpeed()) l.speed else 0f, if (l.time > 0) l.time else System.currentTimeMillis())
            maybeUpload(false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this); queue = LocationQueue(this)
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !prefs.isLoggedIn) { stopSelf(); return START_NOT_STICKY }
        if (!hasLocationPermission()) { stopSelf(); return START_NOT_STICKY }
        startForegroundCompat(buildNotification("Sharing location with Altron while you are checked in"))
        requestUpdates()
        running = true
        return START_STICKY
    }

    private fun requestUpdates() {
        val iv = prefs.intervalSec * 1000L
        currentIv = iv
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, iv)
            .setMinUpdateIntervalMillis(iv / 2)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()
        try { fused.removeLocationUpdates(callback); fused.requestLocationUpdates(req, callback, Looper.getMainLooper()) } catch (e: SecurityException) { stopSelf() }
    }

    private fun maybeUpload(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUpload < prefs.intervalSec * 1000L - 2000) return
        if (uploading) return
        uploading = true
        io.execute {
            try {
                val (pts, maxId) = queue.batch()
                val r = if (pts.length() > 0) Api.ping(this, pts) else Api.state(this)
                if (r.code == 401) { stopSelf(); return@execute }
                if (r.json != null) {
                    lastUpload = now
                    if (pts.length() > 0 && r.ok) queue.deleteUpTo(maxId)
                    val on = Api.applyTrack(this, r.json)
                    val newIv = prefs.intervalSec * 1000L
                    if (!on) { queue.clear(); stopSelf(); return@execute }
                    if (newIv != currentIv) { currentIv = newIv; requestUpdates() }
                    val n = queue.count()
                    updateNotification(if (n > 0) "Sharing location · $n points waiting for signal" else "Sharing location with Altron while you are checked in")
                } else if (now - lastState > 5 * 60 * 1000L) {
                    lastState = now
                }
            } finally { uploading = false }
        }
    }
    private var currentIv = 0L

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(NOTIF_ID, n)
    }
    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, App.CH_TRACK)
            .setSmallIcon(R.drawable.ic_notif).setContentTitle("Altron · Location sharing on").setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build()
    }
    private fun updateNotification(text: String) {
        try { androidx.core.app.NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification(text)) } catch (e: SecurityException) {}
    }
    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        running = false
        try { fused.removeLocationUpdates(callback) } catch (e: Exception) {}
        io.shutdown()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "qa.altron.app.STOP"
        @Volatile var running = false
        fun start(ctx: Context) {
            val i = Intent(ctx, TrackingService::class.java)
            try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i) } catch (e: Exception) {}
        }
        fun stop(ctx: Context) { try { ctx.startService(Intent(ctx, TrackingService::class.java).setAction(ACTION_STOP)) } catch (e: Exception) { ctx.stopService(Intent(ctx, TrackingService::class.java)) } }
        /** Start or stop according to the last known server state. */
        fun sync(ctx: Context) { val p = Prefs(ctx); if (p.isLoggedIn && p.trackOn) { if (!running) start(ctx) } else if (running) stop(ctx) }
    }
}
