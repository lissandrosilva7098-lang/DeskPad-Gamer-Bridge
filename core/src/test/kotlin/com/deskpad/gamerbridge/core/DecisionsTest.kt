package com.deskpad.gamerbridge.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionsTest {

    private val all = BridgeConfig(gamerEnabled = true, autoOpen = true, autoClose = true, openOnMonitorConnect = true)
    private val emptyReading = SwitchReading(emptyMap(), emptyMap(), SwitchState.UNKNOWN)
    private fun edge(from: SwitchState, to: SwitchState) = SwitchEdge(from, to, emptyReading)

    @Test
    fun ligarComMonitorAbre() {
        val a = BridgeDecisions.onSwitchEdge(edge(SwitchState.OFF, SwitchState.ON), all, hasMonitor = true, closeAvailable = true)
        assertTrue(a is BridgeAction.Open)
    }

    @Test
    fun ligarSemMonitorPedeMonitorENaoAbre() {
        val a = BridgeDecisions.onSwitchEdge(edge(SwitchState.OFF, SwitchState.ON), all, hasMonitor = false, closeAvailable = true)
        assertTrue(a is BridgeAction.AskForMonitor)
    }

    @Test
    fun ligarComAberturaAutomaticaDesativadaIgnora() {
        val a = BridgeDecisions.onSwitchEdge(
            edge(SwitchState.OFF, SwitchState.ON), all.copy(autoOpen = false), hasMonitor = true, closeAvailable = true,
        )
        assertTrue(a is BridgeAction.Ignore)
    }

    @Test
    fun integracaoDesativadaIgnoraTudo() {
        val cfg = all.copy(gamerEnabled = false)
        assertTrue(BridgeDecisions.onSwitchEdge(edge(SwitchState.OFF, SwitchState.ON), cfg, true, true) is BridgeAction.Ignore)
        assertTrue(BridgeDecisions.onSwitchEdge(edge(SwitchState.ON, SwitchState.OFF), cfg, true, true) is BridgeAction.Ignore)
    }

    @Test
    fun desligarDepoisDeLigadoEncerra() {
        val a = BridgeDecisions.onSwitchEdge(edge(SwitchState.ON, SwitchState.OFF), all, hasMonitor = true, closeAvailable = true)
        assertTrue(a is BridgeAction.Close)
    }

    @Test
    fun desligarEncerraMesmoSemMonitor() {
        // o monitor pode ter saído antes; o pedido de encerramento continua valendo (o DeskPad responde se não havia sessão)
        val a = BridgeDecisions.onSwitchEdge(edge(SwitchState.ON, SwitchState.OFF), all, hasMonitor = false, closeAvailable = true)
        assertTrue(a is BridgeAction.Close)
    }

    @Test
    fun desligarSemTerVistoLigadoNaoEncerra() {
        val a = BridgeDecisions.onSwitchEdge(edge(SwitchState.UNKNOWN, SwitchState.OFF), all, hasMonitor = true, closeAvailable = true)
        assertTrue(a is BridgeAction.Ignore)
    }

    @Test
    fun desligarSemEncerramentoDisponivelNaoEncerra() {
        val a = BridgeDecisions.onSwitchEdge(edge(SwitchState.ON, SwitchState.OFF), all, hasMonitor = true, closeAvailable = false)
        assertTrue(a is BridgeAction.Ignore)
    }

    @Test
    fun desligarComEncerramentoAutomaticoDesativadoNaoEncerra() {
        val a = BridgeDecisions.onSwitchEdge(
            edge(SwitchState.ON, SwitchState.OFF), all.copy(autoClose = false), hasMonitor = true, closeAvailable = true,
        )
        assertTrue(a is BridgeAction.Ignore)
    }

    @Test
    fun monitorConectadoComBotaoLigadoAbreSoSeAOpcaoEstiverLigada() {
        assertTrue(BridgeDecisions.onMonitorConnected(SwitchState.ON, all) is BridgeAction.Open)
        assertTrue(BridgeDecisions.onMonitorConnected(SwitchState.ON, all.copy(openOnMonitorConnect = false)) is BridgeAction.Ignore)
        assertTrue(BridgeDecisions.onMonitorConnected(SwitchState.OFF, all) is BridgeAction.Ignore)
        assertTrue(BridgeDecisions.onMonitorConnected(SwitchState.UNKNOWN, all) is BridgeAction.Ignore)
    }

    @Test
    fun protecaoContraAberturaRepetida() {
        val g = LaunchGuard(cooldownMs = 4000)
        assertTrue(g.tryBegin(1_000))
        assertFalse("outra abertura em andamento", g.tryBegin(1_100))
        g.end()
        assertFalse("dentro do intervalo mínimo", g.tryBegin(3_000))
        assertTrue("depois do intervalo", g.tryBegin(5_000))
    }

    @Test
    fun descricoesDoContrato() {
        assertTrue(DeskPadContract.describeEnd(DeskPadContract.RESULT_NO_ANSWER).contains("não respondeu"))
        assertTrue(DeskPadContract.describeQuery(DeskPadContract.RESULT_SESSION_OPEN).contains("aberto"))
    }
}
