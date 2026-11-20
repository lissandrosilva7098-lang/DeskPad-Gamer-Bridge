package com.deskpad.gamerbridge.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.deskpad.gamerbridge.core.BridgeAction
import com.deskpad.gamerbridge.core.BridgeConfig
import com.deskpad.gamerbridge.core.BridgeDecisions
import com.deskpad.gamerbridge.core.DeskPadContract
import com.deskpad.gamerbridge.core.DisplayFilter
import com.deskpad.gamerbridge.core.LaunchGuard
import com.deskpad.gamerbridge.core.SwitchEdge
import com.deskpad.gamerbridge.core.SwitchReading
import com.deskpad.gamerbridge.core.SwitchState
import com.deskpad.gamerbridge.core.SwitchStateMachine
import com.deskpad.gamerbridge.data.BridgeState
import com.deskpad.gamerbridge.data.Prefs
import com.deskpad.gamerbridge.log.EventLog
import com.deskpad.gamerbridge.platform.ContractStatus
import com.deskpad.gamerbridge.platform.DeskPadSession
import com.deskpad.gamerbridge.platform.ExternalDisplays
import com.deskpad.gamerbridge.platform.GamerSwitchReader
import com.deskpad.gamerbridge.platform.LaunchResult
import com.deskpad.gamerbridge.platform.Notifier
import com.deskpad.gamerbridge.platform.TargetApps
import com.deskpad.gamerbridge.platform.TargetLauncher

/**
 * Serviço em primeiro plano que observa o botão Gamer (configurações do sistema) e a conexão do monitor.
 * Tudo é dirigido por eventos do Android (ContentObserver e DisplayListener): não há polling nem laço.
 */
class BridgeService : Service() {

    companion object {
        const val ACTION_PAUSE = "com.deskpad.gamerbridge.action.PAUSE"

        private const val LAUNCH_COOLDOWN_MS = 4_000L
        private const val MONITOR_SETTLE_MS = 1_500L
        private const val VERIFY_OPEN_MS = 3_000L

        fun start(ctx: Context) {
            try {
                ctx.startForegroundService(Intent(ctx, BridgeService::class.java))
            } catch (e: Exception) {
                EventLog.log("serviço", "O Android não deixou iniciar o monitoramento: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, BridgeService::class.java))
        }

        /** Liga ou desliga o serviço conforme a opção salva. */
        fun sync(ctx: Context) {
            if (Prefs(ctx).monitoringEnabled) start(ctx) else stop(ctx)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var reader: GamerSwitchReader
    private lateinit var displays: ExternalDisplays
    private val machine = SwitchStateMachine()
    private val guard = LaunchGuard(LAUNCH_COOLDOWN_MS)

    private var switchObserver: ContentObserver? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var lastValues: Map<String, String?> = emptyMap()
    private var lastChangeAt = 0L
    private var hadMonitor = false
    private val monitorSettled = Runnable { onMonitorSettled() }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        reader = GamerSwitchReader(this)
        displays = ExternalDisplays(this)
        if (!goForeground()) return
        register()
        BridgeState.serviceRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE) {
            prefs.monitoringEnabled = false
            EventLog.log("serviço", "Monitoramento pausado pelo aviso")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!prefs.monitoringEnabled) {
            // reinício automático depois de o usuário ter pausado: não volta sozinho
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null) EventLog.log("serviço", "O Android reiniciou o monitoramento (o processo tinha sido encerrado)")
        if (goForeground() && switchObserver == null) {
            // recuperação: o registro falhou no onCreate (por exemplo, o aviso não pôde ser mostrado)
            register()
            BridgeState.serviceRunning.value = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        switchObserver?.let { reader.stop(it) }
        switchObserver = null
        displayListener?.let { displays.stop(it) }
        displayListener = null
        BridgeState.serviceRunning.value = false
        EventLog.log("serviço", "Monitoramento parado")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun goForeground(): Boolean {
        val text = when (machine.confirmed) {
            SwitchState.ON -> "Botão Gamer ligado"
            SwitchState.OFF -> "Botão Gamer desligado"
            SwitchState.UNKNOWN -> "Observando o botão Gamer e o monitor"
        }
        return try {
            val n = Notifier.serviceNotification(this, text)
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Notifier.ID_SERVICE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(Notifier.ID_SERVICE, n)
            }
            true
        } catch (e: Exception) {
            EventLog.log("serviço", "Não consegui ficar em primeiro plano: ${e.javaClass.simpleName}: ${e.message}")
            stopSelf()
            false
        }
    }

    /** Registra os observadores uma única vez (proteção contra inicializações duplicadas). */
    private fun register() {
        if (switchObserver != null) return
        lastValues = reader.readAll()
        val r = machine.baseline(lastValues)
        reader.lastError?.let { EventLog.log("erro", "Leitura do botão Gamer: $it") }
        EventLog.log("serviço", "Monitoramento iniciado. Botão Gamer agora: ${stateName(r.state)} (${votes(r)})")
        switchObserver = reader.observe(handler) { key -> onSwitchSetting(key) }

        hadMonitor = displays.external() != null
        EventLog.log("monitor", displays.external()?.let { "Monitor externo agora: ${ExternalDisplays.describe(it)}" } ?: "Nenhum monitor externo agora")
        displayListener = displays.listen(handler) { event, id -> onDisplayEvent(event, id) }
        goForeground()
    }

    // ---------- botão Gamer ----------

    private fun onSwitchSetting(key: String?) {
        val now = SystemClock.elapsedRealtime()
        val values = reader.readAll()
        val delta = if (lastChangeAt > 0 && now - lastChangeAt < 5_000) " (+${now - lastChangeAt} ms)" else ""
        val changed = values.filter { (k, v) -> lastValues[k] != v }
        if (changed.isEmpty()) {
            EventLog.log("botão", "Aviso de mudança em ${key ?: "?"} sem mudança de valor$delta")
        } else {
            for ((k, v) in changed) EventLog.log("botão", "$k: ${lastValues[k]} → $v$delta")
        }
        reader.lastError?.let { EventLog.log("erro", "Leitura do botão Gamer: $it") }
        lastValues = values
        lastChangeAt = now
        val edge = machine.update(values) ?: return
        onSwitchEdge(edge)
    }

    private fun onSwitchEdge(edge: SwitchEdge) {
        EventLog.log("botão", "Botão Gamer ${stateName(edge.to).uppercase()} (antes: ${stateName(edge.from)}; ${votes(edge.reading)})")
        if (edge.to == SwitchState.ON) prefs.sawOnEdge = true
        if (edge.to == SwitchState.OFF) prefs.sawOffEdge = true
        goForeground()
        val target = prefs.targetPackage
        val action = BridgeDecisions.onSwitchEdge(
            edge,
            config(),
            hasMonitor = displays.external() != null,
            closeAvailable = DeskPadSession.status(this, target) == ContractStatus.AVAILABLE,
        )
        act(action)
    }

    // ---------- monitor ----------

    private fun onDisplayEvent(event: String, id: Int) {
        if (event == "changed") return
        val d = displays.find(id)
        if (d != null && d.name.startsWith(DisplayFilter.DESKPAD_VIRTUAL_PREFIX)) return // janelas do DeskPad
        // um monitor pode aparecer como dois displays e oscilar: só avalio depois que estabilizar
        handler.removeCallbacks(monitorSettled)
        handler.postDelayed(monitorSettled, MONITOR_SETTLE_MS)
    }

    private fun onMonitorSettled() {
        val ext = displays.external()
        if (ext != null && !hadMonitor) {
            EventLog.log("monitor", "Monitor externo conectado: ${ExternalDisplays.describe(ext)}")
            Notifier.cancelNoMonitor(this)
            hadMonitor = true
            act(BridgeDecisions.onMonitorConnected(machine.confirmed, config()))
        } else if (ext == null && hadMonitor) {
            EventLog.log("monitor", "Monitor externo desconectado (o DeskPad cuida do próprio fechamento)")
            hadMonitor = false
        }
    }

    // ---------- ações ----------

    private fun config() = BridgeConfig(
        gamerEnabled = prefs.gamerEnabled,
        autoOpen = prefs.autoOpen,
        autoClose = prefs.autoClose && prefs.calibrated,
        openOnMonitorConnect = prefs.openOnMonitorConnect,
    )

    private fun act(action: BridgeAction) {
        when (action) {
            is BridgeAction.Open -> openTarget(action.reason)
            is BridgeAction.AskForMonitor -> {
                EventLog.log("ação", action.reason)
                Notifier.notifyNoMonitor(this)
            }
            is BridgeAction.Close -> closeDeskPad(action.reason)
            is BridgeAction.Ignore -> EventLog.log("ação", "Nada a fazer: ${action.reason}")
        }
    }

    private fun openTarget(reason: String) {
        val target = prefs.targetPackage
        if (target == null) {
            EventLog.log("ação", "$reason, mas nenhum app de destino foi escolhido")
            Notifier.notifyError(this, "Escolha o app de destino nas configurações do Gamer Bridge.")
            return
        }
        if (!guard.tryBegin(SystemClock.elapsedRealtime())) {
            EventLog.log("ação", "Abertura repetida ignorada (já houve uma há menos de ${LAUNCH_COOLDOWN_MS / 1000} s)")
            return
        }
        val contract = DeskPadSession.status(this, target) == ContractStatus.AVAILABLE
        if (contract) {
            DeskPadSession.query(this) { code ->
                if (code == DeskPadContract.RESULT_SESSION_OPEN) {
                    EventLog.log("ação", "$reason: ${DeskPadContract.describeQuery(code)}, nada a fazer")
                    guard.end()
                } else {
                    launch(target, reason, verify = true)
                }
            }
        } else {
            launch(target, reason, verify = false)
        }
    }

    private fun launch(target: String, reason: String, verify: Boolean) {
        val isDeskPad = TargetApps.isDeskPad(target)
        // O DeskPad abre no celular (o touchpad) e ele mesmo leva o desktop para o monitor.
        val displayId = if (!isDeskPad && prefs.openOnExternalDisplay) {
            displays.external()?.id ?: DisplayFilter.DEFAULT_DISPLAY
        } else {
            DisplayFilter.DEFAULT_DISPLAY
        }
        val label = TargetApps.label(this, target) ?: target
        val t0 = SystemClock.elapsedRealtime()
        val result = TargetLauncher.launch(this, target, displayId, fromForeground = false)
        EventLog.log("ação", "$reason → abrir $label: ${result.description} (${SystemClock.elapsedRealtime() - t0} ms)")
        guard.end()
        when (result) {
            is LaunchResult.Started -> if (verify) handler.postDelayed({ verifyOpened(target, label, displayId) }, VERIFY_OPEN_MS)
            LaunchResult.NeedsTap -> Notifier.notifyTapToOpen(this, target, label, displayId)
            is LaunchResult.Failed -> Notifier.notifyTapToOpen(this, target, label, displayId)
            LaunchResult.NotInstalled, LaunchResult.NoLaunchIntent -> Notifier.notifyError(this, "$label: ${result.description}.")
        }
    }

    /** Com o contrato disponível, confere se o desktop realmente subiu; senão, oferece o toque. */
    private fun verifyOpened(target: String, label: String, displayId: Int) {
        DeskPadSession.query(this) { code ->
            EventLog.log("ação", "Conferência da abertura: ${DeskPadContract.describeQuery(code)}")
            if (code != DeskPadContract.RESULT_SESSION_OPEN) Notifier.notifyTapToOpen(this, target, label, displayId)
        }
    }

    private fun closeDeskPad(reason: String) {
        EventLog.log("ação", "$reason → pedindo ao DeskPad para encerrar a sessão desktop")
        DeskPadSession.end(this, DeskPadContract.REASON_GAMER_SWITCH_OFF) { code ->
            EventLog.log("ação", DeskPadContract.describeEnd(code))
        }
    }

    // ---------- textos ----------

    private fun stateName(s: SwitchState) = when (s) {
        SwitchState.ON -> "ligado"
        SwitchState.OFF -> "desligado"
        SwitchState.UNKNOWN -> "desconhecido"
    }

    private fun votes(r: SwitchReading) = r.votes.entries.joinToString(", ") { (k, v) -> "$k=${r.values[k]}→${stateName(v)}" }
}
