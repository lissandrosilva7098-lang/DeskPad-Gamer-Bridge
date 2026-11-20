package com.deskpad.gamerbridge.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Configurações do Bridge. Cada leitura vai direto ao SharedPreferences: o serviço sempre vê o valor atual. */
class Prefs(ctx: Context) {
    val sp: SharedPreferences = ctx.applicationContext.getSharedPreferences("bridge", Context.MODE_PRIVATE)

    /** O monitoramento (serviço) deve ficar ligado. É o botão Iniciar/Pausar da tela inicial. */
    var monitoringEnabled by bool("monitoring_enabled", false)

    /** O botão Gamer pode disparar ações. */
    var gamerEnabled by bool("gamer_enabled", true)

    /** Abrir o app de destino ao ligar o botão (com monitor conectado). */
    var autoOpen by bool("auto_open", true)

    /** Encerrar a sessão do DeskPad ao desligar o botão (só vale com o contrato disponível e a calibração feita). */
    var autoClose by bool("auto_close", false)

    /** Voltar a monitorar depois de reiniciar o aparelho. */
    var startOnBoot by bool("start_on_boot", false)

    /** Com o botão ligado, abrir o app quando um monitor for conectado. Desligado por padrão (o DeskPad tem a própria entrada automática). */
    var openOnMonitorConnect by bool("open_on_monitor_connect", false)

    /** Abrir o app de destino direto no monitor externo. Nunca vale para o DeskPad (o touchpad dele fica no celular). */
    var openOnExternalDisplay by bool("open_on_external_display", false)

    /** Calibração: o próprio Bridge já viu o botão ser ligado / desligado neste aparelho. */
    var sawOnEdge by bool("saw_on_edge", false)
    var sawOffEdge by bool("saw_off_edge", false)

    var targetPackage: String?
        get() = sp.getString("target_package", null)
        set(v) = sp.edit().putString("target_package", v).apply()

    val calibrated: Boolean get() = sawOnEdge && sawOffEdge

    private fun bool(key: String, default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = sp.getBoolean(key, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            sp.edit().putBoolean(key, value).apply()
        }
    }
}
