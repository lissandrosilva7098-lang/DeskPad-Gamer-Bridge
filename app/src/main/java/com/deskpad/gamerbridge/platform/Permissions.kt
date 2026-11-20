package com.deskpad.gamerbridge.platform

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Estado das permissões e os atalhos oficiais para cada tela de configuração do Android. */
object Permissions {

    fun notificationsGranted(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun overlayGranted(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun batteryUnrestricted(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun overlaySettings(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))

    fun notificationSettings(ctx: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)

    /** Lista oficial de otimização de bateria (não exige a permissão REQUEST_IGNORE_BATTERY_OPTIMIZATIONS). */
    fun batterySettings(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** Detalhes do app: é aqui que fica "⋮ → Permitir configurações restritas" para apps instalados por APK. */
    fun appDetails(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
}
