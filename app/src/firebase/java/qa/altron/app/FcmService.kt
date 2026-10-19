package qa.altron.app

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.Executors

/** Only compiled when app/google-services.json exists. Shows Altron notices and refreshes the tracking state on "state" pushes. */
class FcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Prefs(this).fcmToken = token
        Executors.newSingleThreadExecutor().execute { if (Prefs(this).isLoggedIn) Api.fcm(this, token) }
    }
    override fun onMessageReceived(msg: RemoteMessage) {
        val d = msg.data
        if (d["type"] == "state") { StateWorker.runNow(this); return }
        val title = msg.notification?.title ?: d["title"]
        val body = msg.notification?.body ?: d["body"]
        AltronFcmService.show(this, title, body, d["url"])
    }
}
object FcmHelper {
    fun register(ctx: android.content.Context) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { t ->
            Prefs(ctx).fcmToken = t
            Executors.newSingleThreadExecutor().execute { Api.fcm(ctx, t) }
        }
    }
}
