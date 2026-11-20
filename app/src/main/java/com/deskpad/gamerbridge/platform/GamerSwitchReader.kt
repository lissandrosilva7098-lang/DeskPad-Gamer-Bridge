package com.deskpad.gamerbridge.platform

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.Settings
import com.deskpad.gamerbridge.core.GamerSignals

/**
 * Lê as configurações do sistema que acompanham o botão Gamer (Settings.Global, sem permissão especial)
 * e avisa quando alguma muda. Não usa polling: o Android chama o observador a cada mudança.
 */
class GamerSwitchReader(ctx: Context) {
    private val resolver = ctx.applicationContext.contentResolver

    val signalKeys: List<String> = GamerSignals.SIGNAL_KEYS.map { it.name }
    val allKeys: List<String> = signalKeys + GamerSignals.DIAGNOSTIC_KEYS

    /** Último erro de leitura (por exemplo, se uma atualização do sistema bloquear a leitura), ou null. */
    @Volatile
    var lastError: String? = null
        private set

    fun readAll(): Map<String, String?> {
        var error: String? = null
        val values = allKeys.associateWith { key ->
            try {
                Settings.Global.getString(resolver, key)
            } catch (e: SecurityException) {
                error = "o Android bloqueou a leitura de \"$key\": ${e.message}"
                null
            }
        }
        lastError = error
        return values
    }

    /** Registra um observador para todas as chaves. [onChange] recebe o nome da chave que mudou (se o Android informar). */
    fun observe(handler: Handler, onChange: (String?) -> Unit): ContentObserver {
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                onChange(uri?.lastPathSegment)
            }
        }
        for (key in allKeys) {
            resolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer)
        }
        return observer
    }

    fun stop(observer: ContentObserver) {
        try {
            resolver.unregisterContentObserver(observer)
        } catch (e: Exception) {
            // já removido
        }
    }
}
