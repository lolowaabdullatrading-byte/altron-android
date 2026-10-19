package qa.altron.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

/** First launch: the location-sharing policy (PDPPL transparency). Acceptance is recorded on the server. */
class PolicyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_policy)
        val wv = findViewById<WebView>(R.id.policyWeb)
        wv.settings.javaScriptEnabled = false
        wv.loadUrl(Api.BASE + "/api/mobile/policy.php")
        val btn = findViewById<Button>(R.id.btnAccept); val prog = findViewById<ProgressBar>(R.id.prog)
        btn.setOnClickListener {
            btn.isEnabled = false; prog.visibility = View.VISIBLE
            Executors.newSingleThreadExecutor().execute {
                val ok = Api.consent(this)
                runOnUiThread {
                    prog.visibility = View.GONE
                    if (ok) { Prefs(this).consentOk = true; startActivity(Intent(this, if (Prefs(this).setupDone) MainActivity::class.java else SetupActivity::class.java).apply { intent.extras?.let { putExtras(it) } }); finish() }
                    else { btn.isEnabled = true; android.widget.Toast.makeText(this, "Could not save. Check your connection and try again.", android.widget.Toast.LENGTH_LONG).show() }
                }
            }
        }
    }
}
