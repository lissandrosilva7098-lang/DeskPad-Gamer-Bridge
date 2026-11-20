package com.deskpad.gamerbridge.data

import kotlinx.coroutines.flow.MutableStateFlow

/** Estado do processo que a tela precisa saber e que só o serviço conhece. */
object BridgeState {
    /** O serviço de monitoramento está rodando agora (não é a opção salva: é o fato). */
    val serviceRunning = MutableStateFlow(false)
}
