package com.deskpad.gamerbridge.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deskpad.gamerbridge.platform.ContractStatus
import com.deskpad.gamerbridge.platform.Permissions
import com.deskpad.gamerbridge.platform.ShizukuState

@Composable
fun PermissionsScreen(
    state: UiState,
    onRequestNotifications: () -> Unit,
    onOpen: (Intent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle("Necessárias")
        StatusCard(
            title = "Leitura do botão Gamer",
            value = if (state.readError == null) "Funcionando (não exige permissão)" else "Bloqueada",
            level = if (state.readError == null) Level.OK else Level.ERROR,
            detail = state.readError
                ?: "O estado do botão vem de configurações do sistema que qualquer app pode ler.",
        )
        StatusCard(
            title = "Sobrepor a outros apps",
            value = if (state.overlayOk) "Concedida" else "Não concedida",
            level = if (state.overlayOk) Level.OK else Level.ERROR,
            detail = "Exceção oficial do Android para abrir o DeskPad a partir do segundo plano. Se a opção aparecer " +
                "bloqueada (\"configuração restrita\"), abra os detalhes do app, toque em ⋮ e em \"Permitir configurações restritas\".",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onOpen(Permissions.overlaySettings(ctx)) }) { Text("Abrir") }
                OutlinedButton(onClick = { onOpen(Permissions.appDetails(ctx)) }) { Text("Detalhes do app") }
            }
        }

        SectionTitle("Recomendadas")
        StatusCard(
            title = "Notificações",
            value = if (state.notificationsOk) "Permitidas" else "Bloqueadas",
            level = if (state.notificationsOk) Level.OK else Level.WARN,
            detail = "Aviso do monitoramento e avisos como \"conecte o monitor\" e \"toque para abrir\". Sem elas o Bridge funciona, mas sem avisos.",
        ) {
            FilledTonalButton(onClick = onRequestNotifications) { Text(if (state.notificationsOk) "Ajustar" else "Permitir") }
        }
        StatusCard(
            title = "Sem restrição de bateria",
            value = if (state.batteryOk) "Sem restrição" else "Otimização ativa",
            level = if (state.batteryOk) Level.OK else Level.WARN,
            detail = "O REDMAGIC OS encerra apps em segundo plano. Na lista, escolha \"Todos os apps\", encontre o Gamer Bridge e marque \"Não otimizar\".",
        ) {
            FilledTonalButton(onClick = { onOpen(Permissions.batterySettings()) }) { Text("Abrir") }
        }

        SectionTitle("Encerramento do DeskPad")
        StatusCard(
            title = "Permissão de assinatura do DeskPad",
            value = when (state.contract) {
                ContractStatus.AVAILABLE -> "Concedida"
                ContractStatus.PERMISSION_MISSING -> "Não concedida"
                else -> "Não se aplica agora"
            },
            level = when (state.contract) {
                ContractStatus.AVAILABLE -> Level.OK
                ContractStatus.PERMISSION_MISSING -> Level.ERROR
                else -> Level.NEUTRAL
            },
            detail = state.contract.description.replaceFirstChar { it.uppercase() } + ". O Android a concede sozinho " +
                "quando o Bridge e o DeskPad têm a mesma assinatura; não há tela para pedir.",
        )

        SectionTitle("Opcional")
        StatusCard(
            title = "Shizuku",
            value = state.shizuku.description.replaceFirstChar { it.uppercase() },
            level = if (state.shizuku == ShizukuState.NOT_INSTALLED) Level.NEUTRAL else Level.OK,
            detail = "Nenhuma função atual precisa do Shizuku, então o Bridge não pede autorização a ele.",
        )

        SectionTitle("Não solicitadas")
        Text(
            "Acesso ao uso, acessibilidade e lista de todos os apps não são necessários: o botão é lido pelas " +
                "configurações do sistema e a lista de apps usa só os que têm ícone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}
