package com.deskpad.gamerbridge.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskpad.gamerbridge.core.GamerSignals
import com.deskpad.gamerbridge.core.SwitchState
import com.deskpad.gamerbridge.log.EventLog

@Composable
fun DiagnosticsScreen(state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    val log by vm.log.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val reading = state.reading

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionTitle("Valores ao vivo") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Mexa no botão Gamer com esta tela aberta: os valores mudam na hora.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    for (key in GamerSignals.SIGNAL_KEYS) {
                        val vote = reading?.votes?.get(key.name)
                        Mono("${key.name} = ${reading?.values?.get(key.name) ?: "(ausente)"} → ${voteName(vote)}")
                    }
                    for (key in GamerSignals.DIAGNOSTIC_KEYS) {
                        Mono("$key = ${reading?.values?.get(key) ?: "(ausente)"} (só diagnóstico)")
                    }
                    Text(
                        "Resultado (2 de 3): ${voteName(reading?.state)}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    state.readError?.let { Text("Erro: $it", color = StatusColors.error) }
                }
            }
        }

        item { SectionTitle("Calibração") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Transições vistas pelo monitoramento neste aparelho:", style = MaterialTheme.typography.bodyMedium)
                    Mono("ligado: ${if (state.sawOnEdge) "visto" else "ainda não"} · desligado: ${if (state.sawOffEdge) "visto" else "ainda não"}")
                    Text(
                        "O encerramento automático só pode ser ativado depois das duas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = vm::resetCalibration) { Text("Zerar calibração") }
                }
            }
        }

        item { SectionTitle("Aparelho") }
        item { Mono(vm.deviceInfo) }

        item { SectionTitle("Registro de eventos (${log.size})") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    copy(ctx, vm.deviceInfo + "\n" + EventLog.text())
                    vm.say("Registro copiado.")
                }) { Text("Copiar") }
                OutlinedButton(onClick = vm::clearLog) { Text("Limpar") }
            }
        }
        if (log.isEmpty()) {
            item { Text("Nenhum evento registrado ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(log.asReversed()) { line -> Mono(line) }
        item { Text("", Modifier.padding(bottom = 16.dp)) }
    }
}

@Composable
private fun Mono(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
}

private fun voteName(s: SwitchState?) = when (s) {
    SwitchState.ON -> "ligado"
    SwitchState.OFF -> "desligado"
    else -> "desconhecido"
}

private fun copy(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Gamer Bridge", text))
}
