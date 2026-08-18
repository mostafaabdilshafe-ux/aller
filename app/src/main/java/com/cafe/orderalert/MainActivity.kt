package com.cafe.orderalert

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val termuxPermission = "com.termux.permission.RUN_COMMAND"
    private val permissionRequestCode = 100
    private var pendingAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        findViewById<Button>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.startServerBtn).setOnClickListener {
            ensureTermuxPermission {
                runTermuxScript("/data/data/com.termux/files/home/start-server.sh")
                Toast.makeText(this, "بيتم تشغيل السيرفر...", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.stopServerBtn).setOnClickListener {
            ensureTermuxPermission {
                runTermuxScript("/data/data/com.termux/files/home/stop-server.sh")
                Toast.makeText(this, "بيتم إيقاف السيرفر...", Toast.LENGTH_SHORT).show()
            }
        }

        setupWebView()

        if (Prefs.isConfigured(this)) {
            startService(Intent(this, OrderListenerService::class.java))
        } else {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        loadAdminPage()
    }

    override fun onResume() {
        super.onResume()
        loadAdminPage()
    }

    private fun ensureTermuxPermission(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, termuxPermission) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingAction = action
            ActivityCompat.requestPermissions(this, arrayOf(termuxPermission), permissionRequestCode)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            pendingAction?.invoke()
        } else {
            Toast.makeText(this, "لازم توافق على صلاحية Termux عشان الزرار يشتغل", Toast.LENGTH_LONG).show()
        }
        pendingAction = null
    }

    private fun runTermuxScript(scriptPath: String) {
        try {
            val intent = Intent()
            intent.setClassName("com.termux", "com.termux.app.RunCommandService")
            intent.action = "com.termux.RUN_COMMAND"
            intent.putExtra("com.termux.RUN_COMMAND_PATH", scriptPath)
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            startService(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "تأكد إن Termux مثبت ومفعّل فيه allow-external-apps", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupWebView() {
        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
    }

    private fun loadAdminPage() {
        if (!Prefs.isConfigured(this)) return
        val webView = findViewById<WebView>(R.id.webView)
        val serverUrl = Prefs.serverUrl(this)
        val statusText = findViewById<TextView>(R.id.statusText)
        statusText.text = "متصل بـ $serverUrl"
        webView.loadUrl("$serverUrl/admin")
    }
}
