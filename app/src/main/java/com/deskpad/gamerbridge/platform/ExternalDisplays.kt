package com.deskpad.gamerbridge.platform

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import com.deskpad.gamerbridge.core.DisplayFilter
import com.deskpad.gamerbridge.core.DisplayInfo

/** Monitores pelo DisplayManager (API pública, sem permissão). */
class ExternalDisplays(ctx: Context) {
    private val dm: DisplayManager = ctx.applicationContext.getSystemService(DisplayManager::class.java)

    fun snapshot(): List<DisplayInfo> = dm.displays.map { d ->
        val mode = d.mode
        DisplayInfo(
            id = d.displayId,
            name = d.name ?: "",
            flags = d.flags,
            state = d.state,
            width = mode.physicalWidth,
            height = mode.physicalHeight,
        )
    }

    fun external(): DisplayInfo? = DisplayFilter.pickExternal(snapshot())

    fun find(id: Int): DisplayInfo? = snapshot().firstOrNull { it.id == id }

    fun listen(handler: Handler, onEvent: (event: String, displayId: Int) -> Unit): DisplayManager.DisplayListener {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = onEvent("added", displayId)
            override fun onDisplayRemoved(displayId: Int) = onEvent("removed", displayId)
            override fun onDisplayChanged(displayId: Int) = onEvent("changed", displayId)
        }
        dm.registerDisplayListener(listener, handler)
        return listener
    }

    fun stop(listener: DisplayManager.DisplayListener) {
        try {
            dm.unregisterDisplayListener(listener)
        } catch (e: Exception) {
            // já removido
        }
    }

    companion object {
        fun describe(d: DisplayInfo): String = "\"${d.name}\" ${d.width}x${d.height} (id ${d.id})"
    }
}
