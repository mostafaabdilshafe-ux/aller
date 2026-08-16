package com.cafe.orderalert

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val serverInput = findViewById<EditText>(R.id.serverUrlInput)
        val pinInput = findViewById<EditText>(R.id.pinInput)

        serverInput.setText(Prefs.serverUrl(this))
        pinInput.setText(Prefs.pin(this))

        findViewById<Button>(R.id.saveBtn).setOnClickListener {
            val url = serverInput.text.toString().trim()
            val pin = pinInput.text.toString().trim()
            if (url.isBlank() || pin.isBlank()) {
                Toast.makeText(this, "اكتب رابط السيرفر والرقم السري", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Toast.makeText(this, "الرابط لازم يبدأ بـ http:// أو https://", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.save(this, url, pin)
            startService(Intent(this, OrderListenerService::class.java))
            Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.batteryBtn).setOnClickListener {
            requestBatteryOptimizationExemption()
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        } else {
            Toast.makeText(this, "التطبيق مستثنى بالفعل", Toast.LENGTH_SHORT).show()
        }
    }
}
