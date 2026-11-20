package com.deskpad.gamerbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SwitchStateMachineTest {

    // Valores medidos no aparelho (20/11/2026).
    private val off = mapOf(
        "zte_keypad_slide_on_or_off" to "0",
        "cc_nubia_game_key" to "0",
        "gcs_need_kill_game_launcher" to "1",
    )
    private val on = mapOf(
        "zte_keypad_slide_on_or_off" to "1",
        "cc_nubia_game_key" to "1",
        "gcs_need_kill_game_launcher" to "0",
    )

    @Test
    fun valoresMedidosNoAparelho() {
        val m = SwitchStateMachine()
        assertEquals(SwitchState.OFF, m.read(off).state)
        assertEquals(SwitchState.ON, m.read(on).state)
    }

    @Test
    fun leituraInicialNaoGeraTransicao() {
        val m = SwitchStateMachine()
        m.baseline(on)
        assertEquals(SwitchState.ON, m.confirmed)
        assertNull(m.update(on))
    }

    @Test
    fun cicloCompletoLigarDesligarLigar() {
        val m = SwitchStateMachine()
        m.baseline(off)
        val e1 = m.update(on)
        assertNotNull(e1)
        assertEquals(SwitchState.OFF, e1!!.from)
        assertEquals(SwitchState.ON, e1.to)
        val e2 = m.update(off)
        assertEquals(SwitchState.ON, e2!!.from)
        assertEquals(SwitchState.OFF, e2.to)
        assertEquals(SwitchState.ON, m.update(on)!!.to)
    }

    @Test
    fun eventosRepetidosNaoGeramNovaTransicao() {
        val m = SwitchStateMachine()
        m.baseline(off)
        assertNotNull(m.update(on))
        assertNull(m.update(on))
        assertNull(m.update(on))
    }

    @Test
    fun umaChaveSozinhaNaoBasta() {
        val m = SwitchStateMachine()
        m.baseline(off)
        // só a primeira chave mudou: 1 voto ligado, 2 desligado -> continua desligado
        assertNull(m.update(off + ("zte_keypad_slide_on_or_off" to "1")))
        assertEquals(SwitchState.OFF, m.confirmed)
    }

    @Test
    fun duasDeTresBastam() {
        val m = SwitchStateMachine()
        m.baseline(off)
        // as chaves mudam uma de cada vez; a transição acontece quando a segunda muda
        assertNull(m.update(off + ("zte_keypad_slide_on_or_off" to "1")))
        val e = m.update(off + ("zte_keypad_slide_on_or_off" to "1") + ("cc_nubia_game_key" to "1"))
        assertEquals(SwitchState.ON, e!!.to)
        // a terceira chave mudar depois não gera outra transição
        assertNull(m.update(on))
    }

    @Test
    fun chavesAusentesOuValoresEstranhosSaoDesconhecidos() {
        val m = SwitchStateMachine()
        assertEquals(SwitchState.UNKNOWN, m.baseline(emptyMap()).state)
        assertNull(m.update(mapOf("zte_keypad_slide_on_or_off" to "7", "cc_nubia_game_key" to null)))
        assertEquals(SwitchState.UNKNOWN, m.confirmed)
    }

    @Test
    fun deDesconhecidoParaLigadoGeraTransicao() {
        val m = SwitchStateMachine()
        m.baseline(emptyMap())
        val e = m.update(on)
        assertEquals(SwitchState.UNKNOWN, e!!.from)
        assertEquals(SwitchState.ON, e.to)
    }

    @Test
    fun leituraInconclusivaMantemOEstadoConfirmado() {
        val m = SwitchStateMachine()
        m.baseline(on)
        assertNull(m.update(mapOf("zte_keypad_slide_on_or_off" to "0")))
        assertEquals(SwitchState.ON, m.confirmed)
    }

    @Test
    fun espacosNoValorSaoIgnorados() {
        assertEquals(SwitchState.ON, SignalKey("k", "1", "0").classify(" 1 "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun quorumQueNaoEMaioriaERecusado() {
        SwitchStateMachine(GamerSignals.SIGNAL_KEYS, quorum = 1)
    }
}
