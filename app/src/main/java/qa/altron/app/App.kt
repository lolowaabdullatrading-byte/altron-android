package qa.altron.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_TRACK, "Location sharing", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while your location is shared with Altron (between check-in and check-out)."
                setShowBadge(false)
            })
            nm.createNotificationChannel(NotificationChannel(CH_ALERTS, "Altron alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Check-out reminders, approvals and other notices."
            })
        }
        StateWorker.schedule(this)
    }
    companion object {
        const val CH_TRACK = "altron_tracking"
        const val CH_ALERTS = "altron_alerts"
    }
}
