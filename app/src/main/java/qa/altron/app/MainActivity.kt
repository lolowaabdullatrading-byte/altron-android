package qa.altron.app
import androidx.activity.addCallback

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executors

/** The Altron web app inside a WebView, with camera/GPS grants, file/camera uploads, session recovery and the native bridge. */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var prog: ProgressBar
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null
    private var recovering = false

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val cb = fileCb ?: return@registerForActivityResult
        val data = res.data
        val uris: Array<Uri>? = when {
            res.resultCode != RESULT_OK -> null
            data?.data != null -> arrayOf(data.data!!)
            data?.clipData != null -> Array(data.clipData!!.itemCount) { data.clipData!!.getItemAt(it).uri }
            cameraUri != null -> arrayOf(cameraUri!!)
            else -> null
        }
        cb.onReceiveValue(uris); fileCb = null; cameraUri = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        web = findViewById(R.id.web); prog = findViewById(R.id.prog)
        val s = web.settings
        s.javaScriptEnabled = true; s.domStorageEnabled = true; s.databaseEnabled = true
        s.mediaPlaybackRequiresUserGesture = false; s.allowFileAccess = false; s.setGeolocationEnabled(true)
        s.cacheMode = WebSettings.LOAD_DEFAULT; s.useWideViewPort = true; s.loadWithOverviewMode = true
        s.userAgentString = s.userAgentString + " " + Api.UA_TAG
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.addJavascriptInterface(WebBridge(this), "AltronNative")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val u = req.url; val host = u.host ?: ""
                if (u.scheme == "tel" || u.scheme == "mailto" || u.scheme == "whatsapp" || host.contains("wa.me") || host.contains("whatsapp.com") || u.toString().contains("google.com/maps") || host.contains("maps.app")) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (e: Exception) {}; return true
                }
                if (host != Uri.parse(Api.BASE).host) { try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (e: Exception) {}; return true }
                // session expired -> the site shows login.php: re-open the session with the device token instead of asking for the password again
                if (u.path?.endsWith("/login.php") == true && Prefs(this@MainActivity).isLoggedIn && !recovering) { recoverSession(u.getQueryParameter("next")); return true }
                return false
            }
            override fun onPageStarted(v: WebView, url: String, favicon: android.graphics.Bitmap?) { prog.visibility = View.VISIBLE }
            override fun onPageFinished(v: WebView, url: String) { prog.visibility = View.GONE; if (url.contains("/login.php") && Prefs(this@MainActivity).isLoggedIn && !recovering) recoverSession(null) }
            override fun onReceivedError(v: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) v.loadData(offlineHtml(), "text/html", "utf-8")
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) { prog.progress = p }
            override fun onGeolocationPermissionsShowPrompt(origin: String, cb: GeolocationPermissions.Callback) { cb.invoke(origin, true, true) }
            override fun onPermissionRequest(req: PermissionRequest) {
                val wanted = req.resources.filter { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                if (wanted.isNotEmpty() && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) req.grant(wanted.toTypedArray())
                else { pendingPerm = req; requestPermissions(arrayOf(Manifest.permission.CAMERA), 7) }
            }
            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCb?.onReceiveValue(null); fileCb = cb
                val pick = params.createIntent()
                val cam = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                val f = File.createTempFile("cam_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: cacheDir)
                cameraUri = FileProvider.getUriForFile(this@MainActivity, "qa.altron.app.files", f)
                cam.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraUri)
                val chooser = Intent.createChooser(pick, "Choose").putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cam))
                try { fileChooser.launch(chooser) } catch (e: Exception) { fileCb = null; return false }
                return true
            }
        }
        web.setDownloadListener { url, _, _, _, _ -> try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) {} }

        onBackPressedDispatcher.addCallback(this) { if (web.canGoBack()) web.goBack() else moveTaskToBack(true) }

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 8)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.CAMERA), 7)

        val code = intent.getStringExtra("web_code"); val url = intent.getStringExtra("url") ?: intent.dataString
        when {
            !code.isNullOrEmpty() -> web.loadUrl(Api.BASE + "/api/mobile/web_session.php?c=" + code + (if (!url.isNullOrEmpty()) "&next=" + Uri.encode(Uri.parse(url).path) else ""))
            !url.isNullOrEmpty() -> web.loadUrl(url)
            else -> web.loadUrl(Api.BASE + "/index.php?src=app")
        }
        if (BuildConfig.HAS_FIREBASE) { try { Class.forName("qa.altron.app.FcmHelper").getMethod("register", android.content.Context::class.java).invoke(null, this) } catch (e: Exception) {} }
        TrackingService.sync(this)
        checkUpdate()
    }
    private var pendingPerm: PermissionRequest? = null
    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        if (rc == 7) { val req = pendingPerm; pendingPerm = null; if (req != null) { if (g.isNotEmpty() && g[0] == PackageManager.PERMISSION_GRANTED) req.grant(req.resources) else req.deny() } }
    }
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); (i.getStringExtra("url") ?: i.dataString)?.let { web.loadUrl(it) } }
    override fun onResume() { super.onResume(); StateWorker.runNow(this) }

    private fun recoverSession(next: String?) {
        recovering = true
        Executors.newSingleThreadExecutor().execute {
            val code = Api.webCode(this)
            runOnUiThread {
                if (code != null) web.loadUrl(Api.BASE + "/api/mobile/web_session.php?c=" + code + (if (!next.isNullOrEmpty()) "&next=" + Uri.encode(next) else ""))
                else { doLogout() }
                web.postDelayed({ recovering = false }, 4000)
            }
        }
    }
    fun doLogout() {
        Executors.newSingleThreadExecutor().execute { Api.logout(this) }
        TrackingService.stop(this); Prefs(this).clear()
        CookieManager.getInstance().removeAllCookies(null); WebStorage.getInstance().deleteAllData()
        startActivity(Intent(this, LoginActivity::class.java)); finish()
    }
    private fun checkUpdate() {
        val p = Prefs(this)
        if (p.latestVersionCode > BuildConfig.VERSION_CODE && p.latestVersionName.isNotEmpty()) {
            AlertDialog.Builder(this).setTitle("Update available").setMessage("Altron app ${p.latestVersionName} is available. Update now?")
                .setPositiveButton("Update") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Api.BASE + "/get-app.php"))) }
                .setNegativeButton("Later", null).show()
        }
    }
    private fun offlineHtml() = """<html><body style="font-family:sans-serif;text-align:center;padding:60px 24px;color:#0f172a"><div style="font-size:48px">📡</div><h2>No connection</h2><p>Altron needs internet. Check Wi-Fi / mobile data and tap below.</p><a href="${Api.BASE}/index.php?src=app" style="display:inline-block;background:#1A2BD0;color:#fff;padding:12px 22px;border-radius:12px;text-decoration:none;font-weight:700">Try again</a></body></html>"""
}
