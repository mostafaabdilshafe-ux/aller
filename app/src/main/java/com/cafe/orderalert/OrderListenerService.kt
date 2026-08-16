package com.cafe.orderalert

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

// ==========================================================
// الخدمة دي بتفضل شغالة في الخلفية (Foreground Service) وبتفتح
// اتصال SSE مع /events/admin بنفس طريقة صفحة الويب بالظبط.
// لما حدث new-order يوصل، بتعمل تنبيه شاشة كاملة (full-screen
// notification) بيصحّي الموبايل ويفتح شاشة المدير حتى لو مقفول.
// ==========================================================
class OrderListenerService : Service() {

    private val CHANNEL_ID_PERSISTENT = "order_alert_persistent"
    private val CHANNEL_ID_ALERT = "order_alert_urgent"
    private val NOTIF_ID_PERSISTENT = 1
    private val NOTIF_ID_ALERT = 2

    private var running = false
    private var thread: Thread? = null
    private var connection: HttpURLConnection? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID_PERSISTENT, buildPersistentNotification("بيتم الاتصال بالسيرفر..."))
        startListening()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        try { connection?.disconnect() } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        val persistent = NotificationChannel(
            CHANNEL_ID_PERSISTENT, "حالة الاتصال", NotificationManager.IMPORTANCE_LOW
        )
        val alert = NotificationChannel(
            CHANNEL_ID_ALERT, "تنبيه طلب جديد", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            setSound(
                RingtoneManager.getActualDefaultRingtoneUri(this@OrderListenerService, RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(persistent)
        nm.createNotificationChannel(alert)
    }

    private fun buildPersistentNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID_PERSISTENT)
            .setContentTitle("تنبيه الطلبات شغال")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateStatus(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_PERSISTENT, buildPersistentNotification(text))
    }

    // عند وصول طلب جديد: تنبيه شاشة كاملة (زي منبه/مكالمة) بيصحّي الموبايل
    // ويفتح شاشة المدير تلقائيًا حتى لو الموبايل مقفول.
    private fun fireOrderAlert() {
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setContentTitle("🔔 طلب جديد!")
            .setContentText("دوس هنا لعرض الطلب")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 400, 200, 400, 200, 400))
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_ALERT, notification)

        // نفتح الشاشة مباشرة كمان (مش بس بننتظر لمس الإشعار) - فايدة إضافية
        // لو الموبايل شغال ومش مقفول، النظام هيتجاهل الـ full-screen intent
        // ويعرضه كإشعار عادي بس، فبنستخدم wake lock قصير هنا كضمان إضافي.
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "OrderAlert:wake"
            )
            wl.acquire(10_000)
        } catch (e: Exception) {}
    }

    private fun startListening() {
        if (running) return
        running = true
        thread = Thread {
            while (running) {
                try {
                    connectAndListen()
                } catch (e: Exception) {
                    updateStatus("انقطع الاتصال، بيحاول يرجع...")
                }
                if (running) Thread.sleep(3000) // إعادة محاولة الاتصال
            }
        }
        thread?.isDaemon = true
        thread?.start()
    }

    private fun connectAndListen() {
        val serverUrl = Prefs.serverUrl(this)
        val pin = Prefs.pin(this)
        if (serverUrl.isBlank() || pin.isBlank()) {
            updateStatus("محتاج تظبط الإعدادات الأول")
            Thread.sleep(5000)
            return
        }
        val url = URL("$serverUrl/events/admin?pin=${Uri.encode(pin)}")
        val conn = url.openConnection() as HttpURLConnection
        connection = conn
        conn.setRequestProperty("Accept", "text/event-stream")
        conn.connectTimeout = 10000
        conn.readTimeout = 60000
        conn.doInput = true

        if (conn.responseCode != 200) {
            updateStatus("فشل الاتصال (كود ${conn.responseCode}) - تأكد من الإعدادات")
            return
        }
        updateStatus("🟢 متصل — مستني الطلبات")

        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        var currentEvent = ""
        while (running) {
            val line = reader.readLine() ?: break // السيرفر قفل الاتصال
            if (line.startsWith("event:")) {
                currentEvent = line.removePrefix("event:").trim()
            } else if (line.startsWith("data:")) {
                if (currentEvent == "new-order") {
                    fireOrderAlert()
                }
                currentEvent = ""
            }
        }
        updateStatus("انقطع الاتصال، بيحاول يرجع...")
    }
}
