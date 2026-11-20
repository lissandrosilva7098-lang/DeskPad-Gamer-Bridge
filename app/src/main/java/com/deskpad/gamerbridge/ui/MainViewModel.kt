package com.deskpad.gamerbridge.ui

import android.app.Application
import android.content.SharedPreferences
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deskpad.gamerbridge.core.DeskPadContract
import com.deskpad.gamerbridge.core.DisplayInfo
import com.deskpad.gamerbridge.core.SwitchReading
import com.deskpad.gamerbridge.core.SwitchStateMachine
import com.deskpad.gamerbridge.data.BridgeState
import com.deskpad.gamerbridge.data.Prefs
import com.deskpad.gamerbridge.log.EventLog
import com.deskpad.gamerbridge.platform.ContractStatus
import com.deskpad.gamerbridge.platform.DeskPadSession
import com.deskpad.gamerbridge.platform.ExternalDisplays
import com.deskpad.gamerbridge.platform.GamerSwitchReader
import com.deskpad.gamerbridge.platform.LaunchResult
import com.deskpad.gamerbridge.platform.LaunchableApp
import com.deskpad.gamerbridge.platform.Permissions
import com.deskpad.gamerbridge.platform.ShizukuState
import com.deskpad.gamerbridge.platform.ShizukuStatus
import com.deskpad.gamerbridge.platform.TargetApps
import com.deskpad.gamerbridge.platform.TargetLauncher
import com.deskpad.gamerbridge.service.BridgeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** Tudo o que a tela mostra. Todos os valores são lidos do aparelho; nada é simulado. */
data class UiState(
    val serviceRunning: Boolean = false,
    val monitoringEnabled: Boolean = false,
    val gamerEnabled: Boolean = true,
    val autoOpen: Boolean = true,
    val autoClose: Boolean = false,
    val startOnBoot: Boolean = false,
    val openOnMonitorConnect: Boolean = false,
    val openOnExternalDisplay: Boolean = false,
    val reading: SwitchReading? = null,
    val readError: String? = null,
    val sawOnEdge: Boolean = false,
    val sawOffEdge: Boolean = false,
    val monitor: DisplayInfo? = null,
    val shizuku: ShizukuState = ShizukuState.NOT_INSTALLED,
    val targetPackage: String? = null,
    val targetLabel: String? = null,
    val targetInstalled: Boolean = false,
    val contract: ContractStatus = ContractStatus.TARGET_NOT_DESKPAD,
    val notificationsOk: Boolean = false,
    val overlayOk: Boolean = false,
    val batteryOk: Boolean = false,
    val gsrInstalled: Boolean = false,
) {
    val targetIsDeskPad: Boolean get() = TargetApps.isDeskPad(targetPackage)
    val calibrated: Boolean get() = sawOnEdge && sawOffEdge
    val autoCloseAllowed: Boolean get() = contract == ContractStatus.AVAILABLE && calibrated
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val prefs = Prefs(ctx)
    private val reader = GamerSwitchReader(ctx)
    private val displays = ExternalDisplays(ctx)
    private val machine = SwitchStateMachine()
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val _apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    val apps: StateFlow<List<LaunchableApp>> = _apps

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val log: StateFlow<List<String>> = EventLog.entries

    // A tela observa por conta própria (valor ao vivo), mesmo com o monitoramento pausado.
    private val switchObserver: ContentObserver = reader.observe(handler) { refresh() }
    private val displayListener: DisplayManager.DisplayListener = displays.listen(handler) { _, _ -> refresh() }
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
    private val shizukuReceived = Shizuku.OnBinderReceivedListener { handler.post { refresh() } }
    private val shizukuDead = Shizuku.OnBinderDeadListener { handler.post { refresh() } }

    init {
        if (prefs.targetPackage == null) {
            TargetApps.defaultTarget(ctx)?.let {
                prefs.targetPackage = it
                EventLog.log("config", "App de destino escolhido automaticamente: DeskPad ($it)")
            }
        }
        prefs.sp.registerOnSharedPreferenceChangeListener(prefsListener)
        try {
            Shizuku.addBinderReceivedListenerSticky(shizukuReceived)
            Shizuku.addBinderDeadListener(shizukuDead)
        } catch (t: Throwable) {
            // sem Shizuku: o estado aparece como não instalado
        }
        viewModelScope.launch { BridgeState.serviceRunning.collect { refresh() } }
        refresh()
    }

    override fun onCleared() {
        reader.stop(switchObserver)
        displays.stop(displayListener)
        prefs.sp.unregisterOnSharedPreferenceChangeListener(prefsListener)
        try {
            Shizuku.removeBinderReceivedListener(shizukuReceived)
            Shizuku.removeBinderDeadListener(shizukuDead)
        } catch (t: Throwable) {
        }
    }

    fun refresh() {
        val target = prefs.targetPackage
        val reading = machine.read(reader.readAll())
        _state.value = UiState(
            serviceRunning = BridgeState.serviceRunning.value,
            monitoringEnabled = prefs.monitoringEnabled,
            gamerEnabled = prefs.gamerEnabled,
            autoOpen = prefs.autoOpen,
            autoClose = prefs.autoClose,
            startOnBoot = prefs.startOnBoot,
            openOnMonitorConnect = prefs.openOnMonitorConnect,
            openOnExternalDisplay = prefs.openOnExternalDisplay,
            reading = reading,
            readError = reader.lastError,
            sawOnEdge = prefs.sawOnEdge,
            sawOffEdge = prefs.sawOffEdge,
            monitor = displays.external(),
            shizuku = ShizukuStatus.state(ctx),
            targetPackage = target,
            targetLabel = target?.let { TargetApps.label(ctx, it) },
            targetInstalled = target != null && TargetApps.isInstalled(ctx, target),
            contract = DeskPadSession.status(ctx, target),
            notificationsOk = Permissions.notificationsGranted(ctx),
            overlayOk = Permissions.overlayGranted(ctx),
            batteryOk = Permissions.batteryUnrestricted(ctx),
            gsrInstalled = TargetApps.isInstalled(ctx, TargetApps.GAME_SPACE_REPLACER),
        )
    }

    // ---------- ações da tela ----------

    fun toggleMonitoring() {
        val enable = !_state.value.serviceRunning
        prefs.monitoringEnabled = enable
        if (enable) {
            EventLog.log("serviço", "Monitoramento iniciado pela tela")
            BridgeService.start(ctx)
        } else {
            EventLog.log("serviço", "Monitoramento pausado pela tela")
            BridgeService.stop(ctx)
        }
    }

    fun setGamerEnabled(v: Boolean) = setting("Integração com o botão Gamer", v) { prefs.gamerEnabled = v }
    fun setAutoOpen(v: Boolean) = setting("Abertura automática", v) { prefs.autoOpen = v }
    fun setStartOnBoot(v: Boolean) = setting("Iniciar após reiniciar", v) { prefs.startOnBoot = v }
    fun setOpenOnMonitorConnect(v: Boolean) = setting("Abrir ao conectar o monitor", v) { prefs.openOnMonitorConnect = v }
    fun setOpenOnExternalDisplay(v: Boolean) = setting("Abrir no monitor externo", v) { prefs.openOnExternalDisplay = v }

    fun setAutoClose(v: Boolean) {
        if (v && !_state.value.autoCloseAllowed) {
            _message.value = "O encerramento automático ainda não está disponível (veja o motivo abaixo da opção)."
            return
        }
        setting("Encerramento automático", v) { prefs.autoClose = v }
    }

    private fun setting(name: String, v: Boolean, apply: () -> Unit) {
        apply()
        EventLog.log("config", "$name: ${if (v) "ativado" else "desativado"}")
    }

    fun loadApps() {
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.Default) { TargetApps.list(ctx) }
        }
    }

    fun selectTarget(app: LaunchableApp) {
        prefs.targetPackage = app.packageName
        if (TargetApps.isDeskPad(app.packageName)) prefs.openOnExternalDisplay = false
        EventLog.log("config", "App de destino: ${app.label} (${app.packageName})")
    }

    fun resetCalibration() {
        prefs.sawOnEdge = false
        prefs.sawOffEdge = false
        prefs.autoClose = false
        EventLog.log("config", "Calibração do botão zerada (o encerramento automático foi desativado)")
    }

    /** Teste feito pela tela (em primeiro plano): respeita a regra de não abrir sessão desktop sem monitor. */
    fun testOpen() {
        val s = _state.value
        val target = s.targetPackage ?: return say("Escolha o app de destino nas configurações.")
        val monitor = displays.external()
        if (monitor == null) {
            EventLog.log("teste", "Teste de abertura: nenhum monitor externo, nada foi aberto")
            return say("Conecte o monitor externo antes de testar: sem monitor o Bridge não abre a sessão desktop.")
        }
        val displayId = if (!s.targetIsDeskPad && s.openOnExternalDisplay) monitor.id else 0
        val r = TargetLauncher.launch(ctx, target, displayId, fromForeground = true)
        EventLog.log("teste", "Teste de abertura de ${s.targetLabel ?: target}: ${r.description}")
        say(if (r is LaunchResult.Started) "Abertura enviada para ${s.targetLabel ?: target}." else r.description)
    }

    fun testClose() {
        val s = _state.value
        if (s.contract != ContractStatus.AVAILABLE) return say("Encerramento indisponível: ${s.contract.description}.")
        EventLog.log("teste", "Teste de encerramento: pedindo ao DeskPad")
        DeskPadSession.end(ctx, DeskPadContract.REASON_MANUAL_TEST) { code ->
            val text = DeskPadContract.describeEnd(code)
            EventLog.log("teste", "Teste de encerramento: $text")
            say(text.replaceFirstChar { it.uppercase() } + ".")
        }
    }

    fun clearLog() = EventLog.clear()

    fun consumeMessage() {
        _message.value = null
    }

    fun say(text: String) {
        _message.value = text
    }

    val deviceInfo: String = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
}
