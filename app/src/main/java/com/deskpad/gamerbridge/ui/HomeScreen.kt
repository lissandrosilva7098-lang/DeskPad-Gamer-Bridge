package com.deskpad.gamerbridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deskpad.gamerbridge.core.SwitchState
import com.deskpad.gamerbridge.platform.ContractStatus
import com.deskpad.gamerbridge.platform.ShizukuState

@Composable
fun HomeScreen(state: UiState, vm: MainViewModel, onGoToPermissions: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Serviço + Iniciar/Pausar
        StatusCard(
            title = "Monitoramento",
            value = if (state.serviceRunning) "Ativo" else "Pausado",
            level = if (state.serviceRunning) Level.OK else Level.NEUTRAL,
            detail = when {
                state.serviceRunning -> "Observando o botão Gamer e o monitor externo em segundo plano."
                state.monitoringEnabled -> "A integração está ligada, mas o serviço não está rodando (o Android pode tê-lo encerrado). Toque em Iniciar."
                else -> "Nada acontece ao mexer no botão Gamer enquanto o monitoramento estiver pausado."
            },
        ) {
            Button(
                onClick = vm::toggleMonitoring,
                modifier = Modifier.fillMaxWidth(),
                colors = if (state.serviceRunning) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) { Text(if (state.serviceRunning) "Pausar integração" else "Iniciar integração") }
        }

        if (!state.overlayOk) {
            StatusCard(
                title = "Permissão necessária",
                value = "Sobrepor a outros apps",
                level = Level.ERROR,
                detail = "Sem ela, o Android não deixa o Bridge abrir o DeskPad em segundo plano: você receberia só um aviso para tocar.",
            ) {
                FilledTonalButton(onClick = onGoToPermissions) { Text("Ver permissões") }
            }
        }

        val reading = state.reading
        StatusCard(
            title = "Botão Gamer",
            value = when (reading?.state) {
                SwitchState.ON -> "Ligado"
                SwitchState.OFF -> "Desligado"
                else -> "Estado desconhecido"
            },
            level = when {
                state.readError != null -> Level.ERROR
                reading?.state == SwitchState.ON -> Level.OK
                reading?.state == SwitchState.OFF -> Level.NEUTRAL
                else -> Level.WARN
            },
            detail = when {
                state.readError != null -> "Erro de leitura: ${state.readError}"
                !state.gamerEnabled -> "A integração com o botão está desativada nas configurações."
                reading?.state == SwitchState.UNKNOWN ->
                    "As configurações do sistema não indicam um estado claro. Veja os valores no Diagnóstico."
                else -> "Lido de 3 configurações do sistema; pelo menos 2 precisam concordar."
            },
        )

        StatusCard(
            title = "Monitor externo",
            value = state.monitor?.let { "${it.name} · ${it.width}x${it.height}" } ?: "Nenhum monitor conectado",
            level = if (state.monitor != null) Level.OK else Level.WARN,
            detail = if (state.monitor == null) "Sem monitor, o botão Gamer não abre a sessão desktop." else null,
        )

        StatusCard(
            title = "App de destino",
            value = state.targetLabel ?: state.targetPackage ?: "Nenhum escolhido",
            level = when {
                state.targetPackage == null -> Level.ERROR
                !state.targetInstalled -> Level.ERROR
                else -> Level.OK
            },
            detail = when {
                state.targetPackage == null -> "Escolha o app em Configurações."
                !state.targetInstalled -> "O app escolhido (${state.targetPackage}) não está instalado."
                state.targetIsDeskPad -> "Abre na tela do celular; o DeskPad leva o desktop para o monitor."
                else -> state.targetPackage
            },
        )

        StatusCard(
            title = "Encerramento ao desligar o botão",
            value = when {
                state.contract != ContractStatus.AVAILABLE -> "Indisponível"
                state.autoClose && state.calibrated -> "Ativado"
                else -> "Disponível, desativado"
            },
            level = when {
                state.contract != ContractStatus.AVAILABLE -> Level.WARN
                state.autoClose && state.calibrated -> Level.OK
                else -> Level.NEUTRAL
            },
            detail = state.contract.description.replaceFirstChar { it.uppercase() } + ".",
        )

        StatusCard(
            title = "Shizuku (opcional)",
            value = state.shizuku.description.replaceFirstChar { it.uppercase() },
            level = if (state.shizuku == ShizukuState.NOT_INSTALLED) Level.NEUTRAL else Level.OK,
            detail = "Nenhuma função atual do Bridge depende do Shizuku.",
        )

        if (state.gsrInstalled) {
            StatusCard(
                title = "Game Space Replacer instalado",
                value = "Pode abrir apps junto com o Bridge",
                level = Level.WARN,
                detail = "Se o serviço dele estiver ativo, ele também abre um app ao ligar o botão. Para testar o Bridge, " +
                    "pause o serviço no próprio Game Space Replacer. O Bridge nunca mexe nele.",
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = vm::testOpen, modifier = Modifier.weight(1f)) { Text("Testar abertura") }
            OutlinedButton(
                onClick = vm::testClose,
                enabled = state.contract == ContractStatus.AVAILABLE,
                modifier = Modifier.weight(1f),
            ) { Text("Testar encerramento") }
        }
        OutlinedButton(onClick = onGoToPermissions, modifier = Modifier.fillMaxWidth()) { Text("Configurar permissões") }
    }
}
