package qa.altron.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After reboot or app update: re-check the server state and resume tracking if the person is still checked in. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        StateWorker.schedule(ctx)
        StateWorker.runNow(ctx)
    }
}
