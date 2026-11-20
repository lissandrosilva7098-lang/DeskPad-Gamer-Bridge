package com.deskpad.gamerbridge.platform

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.deskpad.gamerbridge.core.DisplayFilter

sealed interface LaunchResult {
    val description: String

    data class Started(val displayId: Int) : LaunchResult {
        override val description = "pedido de abertura entregue ao Android (tela $displayId)"
    }

    data object NotInstalled : LaunchResult {
        override val description = "o app de destino não está instalado"
    }

    data object NoLaunchIntent : LaunchResult {
        override val description = "o app de destino não tem tela de entrada (ícone)"
    }

    data object NeedsTap : LaunchResult {
        override val description =
            "sem a permissão \"Sobrepor a outros apps\" o Android não deixa abrir em segundo plano: aviso para abrir com um toque"
    }

    data class Failed(val error: String) : LaunchResult {
        override val description = "o Android recusou a abertura: $error"
    }
}

/**
 * Abre o app de destino pelo intent de lançamento dele, como o ícone faria. Nunca cria tarefas extras: a tela de
 * entrada do DeskPad abre a TouchpadActivity, que é singleTask e reaproveita a instância existente.
 */
object TargetLauncher {

    fun launchIntent(ctx: Context, pkg: String): Intent? =
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun options(displayId: Int): Bundle = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()

    /**
     * [fromForeground]: o pedido vem da tela do Bridge (sempre permitido). Em segundo plano, o Android 10+ só deixa
     * abrir uma tela com "Sobrepor a outros apps" concedido.
     */
    fun launch(ctx: Context, pkg: String, displayId: Int = DisplayFilter.DEFAULT_DISPLAY, fromForeground: Boolean): LaunchResult {
        val intent = launchIntent(ctx, pkg)
            ?: return if (TargetApps.isInstalled(ctx, pkg)) LaunchResult.NoLaunchIntent else LaunchResult.NotInstalled
        if (!fromForeground && !Settings.canDrawOverlays(ctx)) return LaunchResult.NeedsTap
        return try {
            ctx.startActivity(intent, options(displayId))
            LaunchResult.Started(displayId)
        } catch (e: Exception) {
            LaunchResult.Failed(e.javaClass.simpleName + ": " + e.message)
        }
    }
}
