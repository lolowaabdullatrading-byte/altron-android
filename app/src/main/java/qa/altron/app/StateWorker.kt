package qa.altron.app

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/** Every 15 min (Android minimum): ask the server whether tracking should be on and start/stop the service accordingly.
 *  This is the safety net for: phone rebooted, app killed by the OS, check-in done on another device, admin changed policy. */
class StateWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val p = Prefs(applicationContext)
        if (!p.isLoggedIn) return Result.success()
        val r = Api.state(applicationContext)
        if (r.code == 401) { p.clear(); TrackingService.stop(applicationContext); return Result.success() }
        if (r.json != null) Api.applyTrack(applicationContext, r.json)
        TrackingService.sync(applicationContext)
        return Result.success()
    }
    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<StateWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("altron_state", ExistingPeriodicWorkPolicy.KEEP, req)
        }
        fun runNow(ctx: Context) {
            WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<StateWorker>().build())
        }
    }
}
