package com.cafe.orderalert

import android.content.Context

object Prefs {
    private const val FILE = "order_alert_prefs"

    fun serverUrl(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("serverUrl", "") ?: ""

    fun pin(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("pin", "") ?: ""

    fun save(ctx: Context, serverUrl: String, pin: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("serverUrl", serverUrl.trim().trimEnd('/'))
            .putString("pin", pin.trim())
            .apply()
    }

    fun isConfigured(ctx: Context): Boolean =
        serverUrl(ctx).isNotBlank() && pin(ctx).isNotBlank()
}
