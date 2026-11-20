package com.deskpad.gamerbridge.core

/**
 * Contrato de controle de sessão entre o Bridge e o DeskPad (docs/CONTRATO-DESKPAD.md).
 *
 * O DeskPad AINDA NÃO implementa este contrato. O Bridge só o usa depois de verificar, no aparelho, que o DeskPad
 * instalado declara o receptor e que o Android concedeu ao Bridge a permissão de assinatura.
 */
object DeskPadContract {
    const val DESKPAD_PACKAGE = "com.deskpad.app"

    /** Permissão `signature` declarada pelo DeskPad: só apps assinados com a mesma chave a recebem. */
    const val PERMISSION = "com.deskpad.permission.CONTROL_SESSION"

    /** Pergunta se o desktop está aberto. Resposta no resultCode: [RESULT_SESSION_OPEN] ou [RESULT_SESSION_CLOSED]. */
    const val ACTION_QUERY_SESSION = "com.deskpad.app.action.QUERY_SESSION"

    /** Pede o encerramento normal da sessão. Resposta: [RESULT_SESSION_ENDED] ou [RESULT_NO_SESSION]. */
    const val ACTION_END_SESSION = "com.deskpad.app.action.END_SESSION"

    const val EXTRA_REASON = "com.deskpad.app.extra.REASON"
    const val REASON_GAMER_SWITCH_OFF = "gamer_switch_off"
    const val REASON_MANUAL_TEST = "bridge_test"

    /** Código inicial do broadcast ordenado: continua assim se ninguém responder. */
    const val RESULT_NO_ANSWER = 0
    const val RESULT_SESSION_OPEN = 1
    const val RESULT_SESSION_CLOSED = 2
    const val RESULT_SESSION_ENDED = 1
    const val RESULT_NO_SESSION = 2

    fun describeQuery(code: Int): String = when (code) {
        RESULT_SESSION_OPEN -> "o DeskPad informou: desktop aberto"
        RESULT_SESSION_CLOSED -> "o DeskPad informou: desktop fechado"
        RESULT_NO_ANSWER -> "o DeskPad não respondeu à consulta"
        else -> "resposta desconhecida do DeskPad à consulta (código $code)"
    }

    fun describeEnd(code: Int): String = when (code) {
        RESULT_SESSION_ENDED -> "o DeskPad confirmou: sessão desktop encerrada"
        RESULT_NO_SESSION -> "o DeskPad informou: não havia sessão aberta"
        RESULT_NO_ANSWER -> "o DeskPad não respondeu ao pedido de encerramento"
        else -> "resposta desconhecida do DeskPad ao encerramento (código $code)"
    }
}
