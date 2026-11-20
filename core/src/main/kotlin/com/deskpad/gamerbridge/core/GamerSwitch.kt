package com.deskpad.gamerbridge.core

/** Estado do botão físico Gamer. */
enum class SwitchState { ON, OFF, UNKNOWN }

/**
 * Uma configuração de `Settings.Global` que acompanha o botão Gamer, com os valores medidos no aparelho.
 * Qualquer valor diferente de [onValue] e [offValue] (inclusive a chave não existir) conta como desconhecido.
 */
data class SignalKey(val name: String, val onValue: String, val offValue: String) {
    init {
        require(onValue != offValue) { "onValue e offValue precisam ser diferentes" }
    }

    fun classify(raw: String?): SwitchState = when (raw?.trim()) {
        onValue -> SwitchState.ON
        offValue -> SwitchState.OFF
        else -> SwitchState.UNKNOWN
    }
}

object GamerSignals {
    /**
     * Medidas no REDMAGIC 11 Pro Plus (Android 16, NX809J) em 20/11/2026: as três mudam ao ligar o botão e voltam
     * ao desligar. Ver docs/ETAPA-1-ANALISE.md, seção 5.1.
     */
    val SIGNAL_KEYS: List<SignalKey> = listOf(
        SignalKey("zte_keypad_slide_on_or_off", onValue = "1", offValue = "0"),
        SignalKey("cc_nubia_game_key", onValue = "1", offValue = "0"),
        SignalKey("gcs_need_kill_game_launcher", onValue = "0", offValue = "1"),
    )

    /** Também mudou com o botão, mas o nome indica "cena de jogo": só aparece no diagnóstico, nunca decide nada. */
    val DIAGNOSTIC_KEYS: List<String> = listOf("game_subagent_scene")

    /** Quantas chaves precisam concordar para o estado valer (2 de 3). */
    const val QUORUM = 2
}

/** Uma leitura das chaves: os valores crus, o voto de cada chave e o estado resultante. */
data class SwitchReading(
    val values: Map<String, String?>,
    val votes: Map<String, SwitchState>,
    val state: SwitchState,
)

/** Uma transição confirmada do botão. */
data class SwitchEdge(val from: SwitchState, val to: SwitchState, val reading: SwitchReading)

/**
 * Transforma leituras das configurações em transições confiáveis do botão:
 * - o estado só vale quando pelo menos [quorum] chaves concordam;
 * - leituras inconclusivas (desconhecido) não mudam nada;
 * - a mesma leitura repetida não gera uma nova transição.
 */
class SwitchStateMachine(
    private val keys: List<SignalKey> = GamerSignals.SIGNAL_KEYS,
    private val quorum: Int = GamerSignals.QUORUM,
) {
    init {
        require(keys.isNotEmpty()) { "é preciso pelo menos uma chave" }
        require(quorum in 1..keys.size) { "quorum fora do intervalo" }
        require(quorum * 2 > keys.size) { "o quorum precisa ser maioria, senão ligado e desligado podem empatar" }
    }

    /** Último estado confirmado (ligado ou desligado), ou desconhecido antes da primeira leitura conclusiva. */
    var confirmed: SwitchState = SwitchState.UNKNOWN
        private set

    fun read(values: Map<String, String?>): SwitchReading {
        val votes = keys.associate { it.name to it.classify(values[it.name]) }
        val on = votes.values.count { it == SwitchState.ON }
        val off = votes.values.count { it == SwitchState.OFF }
        val state = when {
            on >= quorum -> SwitchState.ON
            off >= quorum -> SwitchState.OFF
            else -> SwitchState.UNKNOWN
        }
        return SwitchReading(values, votes, state)
    }

    /** Primeira leitura (ao iniciar o monitoramento): define o estado atual sem gerar transição. */
    fun baseline(values: Map<String, String?>): SwitchReading {
        val r = read(values)
        confirmed = r.state
        return r
    }

    /** Nova leitura: devolve a transição se o estado confirmado mudou de verdade; senão, null. */
    fun update(values: Map<String, String?>): SwitchEdge? {
        val r = read(values)
        if (r.state == SwitchState.UNKNOWN || r.state == confirmed) return null
        val edge = SwitchEdge(confirmed, r.state, r)
        confirmed = r.state
        return edge
    }
}
