package com.hundewildnis.app

import androidx.annotation.DrawableRes

/**
 * Eine einzelne Kachel im Icon-Grid der Startseite.
 *
 * @param id        eindeutige ID zur Zuordnung im onClick-Callback (ersetzt binding.btnXyz.id)
 * @param iconRes   Vector-Drawable-Resource für das Icon (optional)
 * @param emoji     Emoji-String für das Icon (optional, falls iconRes 0)
 * @param label     sichtbarer Text unter dem Icon
 * @param isDanger  true nur für die NOTFALL-Kachel — erzwingt rote Einfärbung unabhängig von der Gruppenfarbe
 */
data class StartTile(
    val id: String,
    @DrawableRes val iconRes: Int = 0,
    val label: String,
    val isDanger: Boolean = false,
    val emoji: String? = null
)

/**
 * Eine Gruppe von Kacheln mit gemeinsamer Akzentfarbe (z.B. "Trainingswerkzeuge").
 *
 * @param chipColorRes    Res-ID der Chip-Hintergrundfarbe (abhängig vom aktuellen Theme aufgelöst)
 * @param accentColorRes  Res-ID der Icon-/Text-Akzentfarbe
 */
data class StartTileGroup(
    val title: String,
    @DrawableRes val groupIconRes: Int,
    val chipColorRes: Int,
    val accentColorRes: Int,
    val tiles: List<StartTile>
)
