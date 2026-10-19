package qa.altron.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity() {
    private var mode = "staff"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        val mobile = findViewById<EditText>(R.id.mobile)
        val password = findViewById<EditText>(R.id.password)
        val btn = findViewById<Button>(R.id.btnLogin)
        val err = findViewById<TextView>(R.id.err)
        val prog = findViewById<ProgressBar>(R.id.prog)
        val tabs = findViewById<RadioGroup>(R.id.modeGroup)
        tabs.setOnCheckedChangeListener { _, id -> mode = when (id) { R.id.modeWorker -> "worker"; R.id.modeAdmin -> "admin"; else -> "staff" } }
        findViewById<TextView>(R.id.help).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Api.BASE + "/signup.php"))) }
        btn.setOnClickListener {
            val m = mobile.text.toString().trim(); val pw = password.text.toString()
            if (m.isEmpty() || pw.isEmpty()) { err.text = "Enter your mobile number and password."; err.visibility = View.VISIBLE; return@setOnClickListener }
            err.visibility = View.GONE; prog.visibility = View.VISIBLE; btn.isEnabled = false
            Executors.newSingleThreadExecutor().execute {
                val r = Api.login(this, m, pw, mode)
                runOnUiThread {
                    prog.visibility = View.GONE; btn.isEnabled = true
                    if (r.ok && r.json != null) {
                        val p = Prefs(this)
                        p.token = r.json.optString("token")
                        val u = r.json.optJSONObject("user")
                        p.userName = u?.optString("name") ?: ""; p.userRole = u?.optString("role") ?: "staff"; p.userPortal = u?.optString("portal") ?: "staff"
                        p.consentOk = r.json.optBoolean("consent_ok", false)
                        Api.applyTrack(this, r.json)
                        StateWorker.schedule(this)
                        val i = Intent(this, MainActivity::class.java).putExtra("web_code", r.json.optString("web_code"))
                        // route: policy -> setup -> main (extras carried through)
                        val next = if (!p.consentOk) Intent(this, PolicyActivity::class.java).putExtras(i) else if (!p.setupDone) Intent(this, SetupActivity::class.java).putExtras(i) else i
                        startActivity(next); finish()
                    } else {
                        err.text = r.json?.optString("err")?.takeIf { it.isNotEmpty() } ?: if (r.code == 0) "No internet connection. Please try again." else "Login failed (${r.code})."
                        err.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}
