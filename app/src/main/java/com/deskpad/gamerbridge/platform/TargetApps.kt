package com.deskpad.gamerbridge.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.deskpad.gamerbridge.core.DeskPadContract

data class LaunchableApp(val packageName: String, val label: String)

/** Apps que podem ser escolhidos como destino (os que têm ícone na tela inicial). */
object TargetApps {
    const val GAME_SPACE_REPLACER = "com.abeja.gamecenterreplacer"

    fun list(ctx: Context): List<LaunchableApp> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return found
            .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != ctx.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(pkg, 0)
        }
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun label(ctx: Context, pkg: String): String? = try {
        val pm = ctx.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(pkg, 0)
        }
        pm.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun isDeskPad(pkg: String?): Boolean = pkg == DeskPadContract.DESKPAD_PACKAGE

    /** Na primeira vez, sugere o DeskPad se ele estiver instalado. */
    fun defaultTarget(ctx: Context): String? =
        DeskPadContract.DESKPAD_PACKAGE.takeIf { isInstalled(ctx, it) }
}
