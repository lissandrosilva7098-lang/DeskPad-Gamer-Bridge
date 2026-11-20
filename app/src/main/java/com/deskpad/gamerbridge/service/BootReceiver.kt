package com.deskpad.gamerbridge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deskpad.gamerbridge.data.Prefs
import com.deskpad.gamerbridge.log.EventLog

/**
 * Depois de reiniciar o aparelho (se a opção estiver ligada) ou depois de atualizar o Bridge, volta a monitorar.
 * O Android 15+ permite iniciar daqui um serviço do tipo specialUse (a restrição vale para outros tipos).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val p = Prefs(context)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                if (p.monitoringEnabled && p.startOnBoot) {
                    EventLog.log("serviço", "Aparelho reiniciado: iniciando o monitoramento")
                    BridgeService.start(context)
                } else if (p.monitoringEnabled) {
                    EventLog.log("serviço", "Aparelho reiniciado: \"iniciar após reiniciar\" está desligado, o monitoramento não foi iniciado")
                }
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (p.monitoringEnabled) {
                    EventLog.log("serviço", "Bridge atualizado: retomando o monitoramento")
                    BridgeService.start(context)
                }
            }
        }
    }
}
