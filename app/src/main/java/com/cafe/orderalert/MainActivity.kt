package com.cafe.orderalert

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // إظهار الشاشة فوق القفل وتنويرها تلقائيًا (زي المنبه بالظبط)
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

        setupWebView()

        if (Prefs.isConfigured(this)) {
            startService(Intent(this, OrderListenerService::class.java))
        } else {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // لما يوصل تنبيه طلب جديد ونضغط عليه، نعيد تحميل صفحة المدير
        loadAdminPage()
    }

    override fun onResume() {
        super.onResume()
        loadAdminPage()
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
        val pin = Prefs.pin(this)
        val statusText = findViewById<TextView>(R.id.statusText)
        statusText.text = "متصل بـ $serverUrl"
        // تمرير الرقم السري عن طريق localStorage قبل تحميل الصفحة صعب من هنا،
        // فبنعتمد على إن صفحة /admin بتقبل ?pin= أو المستخدم يدخله مرة واحدة
        // وهيتحفظ جوه الـ WebView تلقائيًا (نفس سلوك المتصفح العادي).
        webView.loadUrl("$serverUrl/admin")
    }
}
