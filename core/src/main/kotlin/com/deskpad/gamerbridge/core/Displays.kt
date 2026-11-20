package com.deskpad.gamerbridge.core

/** Cópia simples de um `android.view.Display` (para a regra poder ser testada sem Android). */
data class DisplayInfo(
    val id: Int,
    val name: String,
    val flags: Int,
    val state: Int,
    val width: Int,
    val height: Int,
)

object DisplayFilter {
    const val DEFAULT_DISPLAY = 0

    // Mesmos valores das constantes públicas de android.view.Display.
    const val FLAG_PRIVATE = 1 shl 2
    const val FLAG_PRESENTATION = 1 shl 3
    const val STATE_ON = 2

    /** As janelas do DeskPad criam displays virtuais com este prefixo; eles não são monitores. */
    const val DESKPAD_VIRTUAL_PREFIX = "deskpad-"

    /**
     * Monitor externo de verdade: não é a tela do celular, aceita apresentação (o HDMI tem `flags=0x8b`), não é
     * privado e não é uma janela virtual do DeskPad (`flags=0x5f80`, sem FLAG_PRESENTATION nem FLAG_PRIVATE).
     */
    fun isExternalMonitor(d: DisplayInfo): Boolean =
        d.id != DEFAULT_DISPLAY &&
            (d.flags and FLAG_PRESENTATION) != 0 &&
            (d.flags and FLAG_PRIVATE) == 0 &&
            !d.name.startsWith(DESKPAD_VIRTUAL_PREFIX)

    /** O monitor externo atual: o primeiro ligado; se nenhum estiver ligado, o primeiro encontrado. Nunca um ID fixo. */
    fun pickExternal(displays: List<DisplayInfo>): DisplayInfo? {
        val candidates = displays.filter(::isExternalMonitor)
        return candidates.firstOrNull { it.state == STATE_ON } ?: candidates.firstOrNull()
    }
}
