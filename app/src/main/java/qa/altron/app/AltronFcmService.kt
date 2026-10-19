package qa.altron.app

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Native push (Firebase). Compiled only when google-services.json is present (HAS_FIREBASE); otherwise this class is inert. */
class AltronFcmService : android.app.Service() {
    override fun onBind(intent: Intent?): android.os.IBinder? = null
    companion object {
        fun show(ctx: android.content.Context, title: String?, body: String?, url: String?) {
            val i = Intent(ctx, MainActivity::class.java).apply { if (!url.isNullOrEmpty()) putExtra("url", url); flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP }
            val pi = PendingIntent.getActivity(ctx, (System.currentTimeMillis() % 10000).toInt(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, App.CH_ALERTS).setSmallIcon(R.drawable.ic_notif).setContentTitle(title ?: "Altron").setContentText(body ?: "")
                .setStyle(NotificationCompat.BigTextStyle().bigText(body ?: "")).setAutoCancel(true).setContentIntent(pi).setPriority(NotificationCompat.PRIORITY_HIGH).build()
            try { NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 100000).toInt(), n) } catch (e: SecurityException) {}
        }
    }
}
