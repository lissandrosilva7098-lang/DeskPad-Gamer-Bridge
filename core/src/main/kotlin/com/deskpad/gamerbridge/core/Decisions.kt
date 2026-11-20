package com.deskpad.gamerbridge.core

/** Opções do usuário que influenciam as decisões. */
data class BridgeConfig(
    val gamerEnabled: Boolean,
    val autoOpen: Boolean,
    /** Já considera a calibração: só é true se o usuário ligou a opção e o Bridge viu as duas transições. */
    val autoClose: Boolean,
    val openOnMonitorConnect: Boolean,
)

/** O que o serviço deve fazer. Cada caso traz o motivo, que vai para o Diagnóstico. */
sealed interface BridgeAction {
    val reason: String

    data class Open(override val reason: String) : BridgeAction
    data class AskForMonitor(override val reason: String) : BridgeAction
    data class Close(override val reason: String) : BridgeAction
    data class Ignore(override val reason: String) : BridgeAction
}

object BridgeDecisions {

    fun onSwitchEdge(edge: SwitchEdge, cfg: BridgeConfig, hasMonitor: Boolean, closeAvailable: Boolean): BridgeAction {
        if (!cfg.gamerEnabled) return BridgeAction.Ignore("a integração com o botão Gamer está desativada")
        return when (edge.to) {
            SwitchState.ON -> when {
                !cfg.autoOpen -> BridgeAction.Ignore("botão ligado, mas a abertura automática está desativada")
                !hasMonitor -> BridgeAction.AskForMonitor("botão ligado sem monitor externo: não abro sessão desktop")
                else -> BridgeAction.Open("botão Gamer ligado com monitor conectado")
            }

            SwitchState.OFF -> when {
                edge.from != SwitchState.ON ->
                    BridgeAction.Ignore("botão desligado, mas o estado anterior não era \"ligado\": não encerro nada")
                !cfg.autoClose -> BridgeAction.Ignore("botão desligado; o encerramento automático está desativado")
                !closeAvailable ->
                    BridgeAction.Ignore("botão desligado, mas o DeskPad instalado não oferece encerramento externo")
                else -> BridgeAction.Close("botão Gamer desligado")
            }

            SwitchState.UNKNOWN -> BridgeAction.Ignore("leitura inconclusiva do botão")
        }
    }

    fun onMonitorConnected(switchState: SwitchState, cfg: BridgeConfig): BridgeAction = when {
        !cfg.openOnMonitorConnect -> BridgeAction.Ignore("monitor conectado; abrir ao conectar está desativado")
        !cfg.gamerEnabled -> BridgeAction.Ignore("monitor conectado; a integração com o botão Gamer está desativada")
        !cfg.autoOpen -> BridgeAction.Ignore("monitor conectado; a abertura automática está desativada")
        switchState != SwitchState.ON -> BridgeAction.Ignore("monitor conectado com o botão Gamer desligado")
        else -> BridgeAction.Open("monitor conectado com o botão Gamer ligado")
    }
}

/**
 * Proteção contra aberturas repetidas: uma abertura por vez e um intervalo mínimo entre duas aberturas.
 * O tempo vem de fora (relógio monotônico do Android) para a regra poder ser testada.
 */
class LaunchGuard(private val cooldownMs: Long) {
    private var inFlight = false
    private var lastStart: Long? = null

    @Synchronized
    fun tryBegin(nowMs: Long): Boolean {
        if (inFlight) return false
        val last = lastStart
        if (last != null && nowMs - last < cooldownMs) return false
        inFlight = true
        lastStart = nowMs
        return true
    }

    @Synchronized
    fun end() {
        inFlight = false
    }
}
