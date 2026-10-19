package qa.altron.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Routes to the right screen: login -> policy -> permissions setup -> app. */
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = Prefs(this)
        val next = when {
            !p.isLoggedIn -> LoginActivity::class.java
            !p.consentOk -> PolicyActivity::class.java
            !p.setupDone -> SetupActivity::class.java
            else -> MainActivity::class.java
        }
        startActivity(Intent(this, next).apply { intent.extras?.let { putExtras(it) } })
        finish()
    }
}
