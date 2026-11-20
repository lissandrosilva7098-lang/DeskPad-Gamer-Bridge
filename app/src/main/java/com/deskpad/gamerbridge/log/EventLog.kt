package com.deskpad.gamerbridge.log

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Registro dos eventos detectados (botão, monitor, aberturas, encerramentos, erros), guardado num arquivo
 * de tamanho limitado e mostrado na tela de Diagnóstico.
 */
object EventLog {
    private const val MAX_LINES = 600
    private const val TAG = "GamerBridge"

    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private val format = SimpleDateFormat("dd/MM HH:mm:ss.SSS", Locale("pt", "BR"))
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "bridge-log").apply { isDaemon = true } }
    private var file: File? = null
    private var linesInFile = 0

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _lines

    fun init(ctx: Context) {
        synchronized(lock) {
            if (file != null) return
            val f = File(ctx.filesDir, "eventos.log")
            file = f
            val old = try {
                if (f.exists()) f.readLines() else emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            linesInFile = old.size
            old.takeLast(MAX_LINES).forEach { lines.addLast(it) }
            _lines.value = lines.toList()
        }
    }

    fun log(tag: String, message: String) {
        val line = add(tag, message)
        io.execute { append(line) }
    }

    /** Grava na hora (usado quando o processo está prestes a morrer). */
    fun logNow(tag: String, message: String) {
        append(add(tag, message))
    }

    fun clear() {
        synchronized(lock) {
            lines.clear()
            _lines.value = emptyList()
        }
        io.execute {
            synchronized(lock) {
                try {
                    file?.writeText("")
                } catch (e: Exception) {
                    Log.w(TAG, "não consegui limpar o registro", e)
                }
                linesInFile = 0
            }
        }
    }

    fun text(): String = synchronized(lock) { lines.joinToString("\n") }

    private fun add(tag: String, message: String): String {
        synchronized(lock) {
            val line = format.format(Date()) + " [" + tag + "] " + message
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
            _lines.value = lines.toList()
            Log.i(TAG, line)
            return line
        }
    }

    private fun append(line: String) {
        synchronized(lock) {
            val f = file ?: return
            try {
                if (linesInFile >= MAX_LINES * 2) {
                    // Mantém o arquivo pequeno: reescreve só as linhas mais recentes.
                    f.writeText(lines.joinToString("\n", postfix = "\n"))
                    linesInFile = lines.size
                } else {
                    f.appendText(line + "\n")
                    linesInFile++
                }
            } catch (e: Exception) {
                Log.w(TAG, "não consegui gravar o registro", e)
            }
        }
    }
}
