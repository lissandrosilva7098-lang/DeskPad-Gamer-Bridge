package com.deskpad.gamerbridge.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.deskpad.gamerbridge.R
import com.deskpad.gamerbridge.service.BridgeService
import com.deskpad.gamerbridge.ui.MainActivity

object Notifier {
    private const val CH_SERVICE = "monitoramento"
    private const val CH_ALERTS = "avisos"

    const val ID_SERVICE = 1
    private const val ID_NO_MONITOR = 2
    private const val ID_TAP_TO_OPEN = 3
    private const val ID_ERROR = 4

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Monitoramento do botão Gamer", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Aviso fixo exigido pelo Android enquanto o Bridge observa o botão e o monitor."
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Avisos", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Monitor ausente, abertura que precisa de um toque e erros."
            },
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 10, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun serviceNotification(ctx: Context, text: String): Notification {
        val pause = PendingIntent.getService(
            ctx, 11, Intent(ctx, BridgeService::class.java).setAction(BridgeService.ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setContentTitle("Gamer Bridge ativo")
            .setContentText(text)
            .setContentIntent(openApp(ctx))
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(Notification.Action.Builder(null, "Pausar", pause).build())
            .build()
    }

    fun notifyNoMonitor(ctx: Context) = notify(
        ctx, ID_NO_MONITOR,
        Notification.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setContentTitle("Conecte o monitor externo")
            .setContentText("O botão Gamer foi ligado, mas não há monitor: a sessão desktop não foi aberta.")
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build(),
    )

    fun cancelNoMonitor(ctx: Context) = cancel(ctx, ID_NO_MONITOR)

    fun notifyTapToOpen(ctx: Context, pkg: String, label: String, displayId: Int) {
        val launch = TargetLauncher.launchIntent(ctx, pkg) ?: return
        val pi = PendingIntent.getActivity(
            ctx, 12, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            TargetLauncher.options(displayId),
        )
        notify(
            ctx, ID_TAP_TO_OPEN,
            Notification.Builder(ctx, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_bridge)
                .setContentTitle("Toque para abrir o $label")
                .setContentText("O Android não deixou abrir sozinho. Veja Permissões no Gamer Bridge.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun notifyError(ctx: Context, text: String) = notify(
        ctx, ID_ERROR,
        Notification.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setContentTitle("Gamer Bridge")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build(),
    )

    private fun notify(ctx: Context, id: Int, n: Notification) {
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (e: SecurityException) {
            // sem permissão de notificação: o evento continua no Diagnóstico
        }
    }

    private fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
    }
}
