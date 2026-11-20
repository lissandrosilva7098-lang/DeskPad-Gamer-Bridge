package com.deskpad.gamerbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayFilterTest {

    // Valores reais do Diagnóstico do DeskPad no REDMAGIC 11 Pro Plus (20/11/2026).
    private val phone = DisplayInfo(0, "Tela integrada", 0x4083, 2, 2688, 1216)
    private val hdmi = DisplayInfo(211, "Tela HDMI", 0x8b, 2, 1920, 1080)
    private val deskpadWindow = DisplayInfo(212, "deskpad-ZArchiver", 0x5f80, 2, 1190, 771)

    @Test
    fun hdmiEMonitorExterno() = assertTrue(DisplayFilter.isExternalMonitor(hdmi))

    @Test
    fun telaDoCelularNaoE() = assertFalse(DisplayFilter.isExternalMonitor(phone))

    @Test
    fun janelaVirtualDoDeskPadNaoE() = assertFalse(DisplayFilter.isExternalMonitor(deskpadWindow))

    @Test
    fun displayPrivadoNaoE() =
        assertFalse(DisplayFilter.isExternalMonitor(hdmi.copy(flags = hdmi.flags or DisplayFilter.FLAG_PRIVATE)))

    @Test
    fun virtualSemPrefixoMasSemApresentacaoNaoE() =
        assertFalse(DisplayFilter.isExternalMonitor(deskpadWindow.copy(name = "outra-janela")))

    @Test
    fun escolheOMonitorEntreTodos() =
        assertEquals(hdmi, DisplayFilter.pickExternal(listOf(phone, deskpadWindow, hdmi)))

    @Test
    fun semMonitorDevolveNull() = assertNull(DisplayFilter.pickExternal(listOf(phone, deskpadWindow)))

    @Test
    fun preferePorUmMonitorLigado() {
        val desligado = hdmi.copy(id = 209, state = 1)
        assertEquals(hdmi, DisplayFilter.pickExternal(listOf(phone, desligado, hdmi)))
    }

    @Test
    fun semNenhumLigadoUsaOPrimeiro() {
        val desligado = hdmi.copy(id = 209, state = 1)
        assertEquals(desligado, DisplayFilter.pickExternal(listOf(phone, desligado)))
    }
}
