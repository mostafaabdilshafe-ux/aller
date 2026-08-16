package com.cafe.orderalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (Prefs.isConfigured(context)) {
                context.startService(Intent(context, OrderListenerService::class.java))
            }
        }
    }
}
