package com.deskpad.gamerbridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskpad.gamerbridge.platform.ContractStatus
import com.deskpad.gamerbridge.platform.LaunchableApp

@Composable
fun SettingsScreen(state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SectionTitle("Botão Gamer")
        SettingSwitch(
            title = "Integração com o botão Gamer",
            description = "Ligar e desligar o botão físico dispara as ações abaixo.",
            checked = state.gamerEnabled,
            onChange = vm::setGamerEnabled,
        )
        SettingSwitch(
            title = "Abertura automática",
            description = "Ao ligar o botão, abre o app de destino. Só com um monitor externo conectado.",
            checked = state.autoOpen,
            onChange = vm::setAutoOpen,
            enabled = state.gamerEnabled,
        )
        SettingSwitch(
            title = "Encerramento automático",
            description = when {
                state.contract != ContractStatus.AVAILABLE ->
                    "Indisponível: ${state.contract.description}."
                !state.calibrated ->
                    "Indisponível até o Bridge ver o botão ser ligado e desligado neste aparelho (com o monitoramento ativo). " +
                        "Visto: ligado ${if (state.sawOnEdge) "sim" else "não"}, desligado ${if (state.sawOffEdge) "sim" else "não"}."
                else -> "Ao desligar o botão, pede ao DeskPad para encerrar a sessão desktop normalmente. Nada é apagado."
            },
            checked = state.autoClose && state.autoCloseAllowed,
            onChange = vm::setAutoClose,
            enabled = state.gamerEnabled && state.autoCloseAllowed,
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle("App de destino")
        Text(
            state.targetLabel ?: state.targetPackage ?: "Nenhum escolhido",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        state.targetPackage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
        FilledTonalButton(
            onClick = {
                vm.loadApps()
                picking = true
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Escolher app") }
        SettingSwitch(
            title = "Abrir no monitor externo",
            description = if (state.targetIsDeskPad) {
                "Não vale para o DeskPad: ele abre o touchpad no celular e leva o desktop para o monitor sozinho."
            } else {
                "Abre o app direto no monitor (setLaunchDisplayId). O app precisa aceitar telas secundárias."
            },
            checked = state.openOnExternalDisplay && !state.targetIsDeskPad,
            onChange = vm::setOpenOnExternalDisplay,
            enabled = !state.targetIsDeskPad,
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle("Monitor e inicialização")
        SettingSwitch(
            title = "Abrir ao conectar o monitor",
            description = "Com o botão Gamer já ligado, abre o app quando um monitor é conectado. Desligado por padrão: " +
                "o DeskPad tem a própria entrada automática, e as duas juntas pediriam a abertura duas vezes.",
            checked = state.openOnMonitorConnect,
            onChange = vm::setOpenOnMonitorConnect,
            enabled = state.gamerEnabled && state.autoOpen,
        )
        SettingSwitch(
            title = "Iniciar após reiniciar o aparelho",
            description = "Volta a monitorar depois de ligar o celular. O REDMAGIC OS pode bloquear isso se a otimização de " +
                "bateria estiver ativa para o Bridge (veja Permissões).",
            checked = state.startOnBoot,
            onChange = vm::setStartOnBoot,
        )
    }

    if (picking) {
        val apps by vm.apps.collectAsStateWithLifecycle()
        AppPickerDialog(
            apps = apps,
            onPick = {
                vm.selectTarget(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun AppPickerDialog(apps: List<LaunchableApp>, onPick: (LaunchableApp) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = apps.filter {
        query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App de destino") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (apps.isEmpty()) {
                    Text("Carregando apps…", modifier = Modifier.padding(top = 12.dp))
                }
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
