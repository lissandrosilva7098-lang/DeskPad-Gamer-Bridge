package com.deskpad.gamerbridge

import android.app.Application
import com.deskpad.gamerbridge.log.EventLog
import com.deskpad.gamerbridge.platform.Notifier

class BridgeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        EventLog.init(this)
        Notifier.createChannels(this)

        // Registro de erros: um travamento fica no Diagnóstico antes de o Android encerrar o processo.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            EventLog.logNow(
                "erro",
                "Falha inesperada em \"${thread.name}\": ${error.javaClass.simpleName}: ${error.message} | " +
                    error.stackTrace.take(6).joinToString(" < "),
            )
            previous?.uncaughtException(thread, error)
        }
    }
}
