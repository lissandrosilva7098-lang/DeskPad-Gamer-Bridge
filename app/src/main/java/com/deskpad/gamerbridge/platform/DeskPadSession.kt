package com.deskpad.gamerbridge.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.deskpad.gamerbridge.core.DeskPadContract

enum class ContractStatus(val description: String) {
    TARGET_NOT_DESKPAD("o app de destino não é o DeskPad: não há encerramento de sessão"),
    NOT_INSTALLED("o DeskPad não está instalado"),
    NOT_SUPPORTED("o DeskPad instalado ainda não oferece encerramento externo (falta o contrato da docs/CONTRATO-DESKPAD.md)"),
    PERMISSION_MISSING(
        "o DeskPad oferece o encerramento, mas o Android não deu a permissão ao Bridge: os dois apps precisam da mesma " +
            "assinatura (se já tiverem, reinstale o Bridge depois do DeskPad)",
    ),
    AVAILABLE("disponível: o DeskPad aceita pedidos de encerramento do Bridge"),
}

/**
 * Cliente do contrato de sessão do DeskPad. Usa broadcasts ordenados explícitos (só para o pacote do DeskPad,
 * protegidos pela permissão de assinatura) para consultar e encerrar a sessão; a resposta vem no resultCode.
 */
object DeskPadSession {

    fun status(ctx: Context, targetPackage: String?): ContractStatus {
        if (!TargetApps.isDeskPad(targetPackage)) return ContractStatus.TARGET_NOT_DESKPAD
        if (!TargetApps.isInstalled(ctx, DeskPadContract.DESKPAD_PACKAGE)) return ContractStatus.NOT_INSTALLED
        val probe = Intent(DeskPadContract.ACTION_END_SESSION).setPackage(DeskPadContract.DESKPAD_PACKAGE)
        val receivers = if (Build.VERSION.SDK_INT >= 33) {
            ctx.packageManager.queryBroadcastReceivers(probe, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            ctx.packageManager.queryBroadcastReceivers(probe, 0)
        }
        if (receivers.isEmpty()) return ContractStatus.NOT_SUPPORTED
        if (ctx.checkSelfPermission(DeskPadContract.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            return ContractStatus.PERMISSION_MISSING
        }
        return ContractStatus.AVAILABLE
    }

    fun query(ctx: Context, onResult: (Int) -> Unit) =
        send(ctx, DeskPadContract.ACTION_QUERY_SESSION, reason = null, onResult)

    fun end(ctx: Context, reason: String, onResult: (Int) -> Unit) =
        send(ctx, DeskPadContract.ACTION_END_SESSION, reason, onResult)

    private fun send(ctx: Context, action: String, reason: String?, onResult: (Int) -> Unit) {
        val intent = Intent(action).setPackage(DeskPadContract.DESKPAD_PACKAGE)
        if (reason != null) intent.putExtra(DeskPadContract.EXTRA_REASON, reason)
        val result = object : BroadcastReceiver() {
            override fun onReceive(context: Context, i: Intent) {
                onResult(resultCode)
            }
        }
        try {
            ctx.sendOrderedBroadcast(
                intent,
                null,
                result,
                Handler(Looper.getMainLooper()),
                DeskPadContract.RESULT_NO_ANSWER,
                null,
                null,
            )
        } catch (e: Exception) {
            onResult(DeskPadContract.RESULT_NO_ANSWER)
        }
    }
}
