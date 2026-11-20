package com.deskpad.gamerbridge.platform

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

enum class ShizukuState(val description: String) {
    NOT_INSTALLED("não instalado"),
    NOT_RUNNING("instalado, mas não está em execução"),
    RUNNING_NO_PERMISSION("em execução; o Bridge não pediu autorização"),
    AUTHORIZED("em execução e autorizado"),
}

/** Só leitura do estado. O Bridge não pede autorização ao Shizuku: nenhuma função atual precisa dele. */
object ShizukuStatus {
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun state(ctx: Context): ShizukuState {
        val alive = try {
            Shizuku.pingBinder()
        } catch (t: Throwable) {
            false
        }
        if (!alive) {
            return if (TargetApps.isInstalled(ctx, SHIZUKU_PACKAGE)) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
        }
        val granted = try {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (t: Throwable) {
            false
        }
        return if (granted) ShizukuState.AUTHORIZED else ShizukuState.RUNNING_NO_PERMISSION
    }
}
