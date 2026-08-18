package com.hundewildnis.app

import android.content.Context
import android.content.res.Configuration

/**
 * Zentrale, konsistente Auflösung der Gruppenfarben für das Startseiten-Redesign.
 * Vermeidet, dass Dark/Light-Auswahl an mehreren Stellen (Activity + Adapter)
 * unabhängig voneinander entschieden wird.
 */
object StartGroupColors {

    data class GroupColorPair(val chipColorRes: Int, val accentColorRes: Int)

    private val darkPairs = listOf(
        GroupColorPair(R.color.rd_group1_chip_dark, R.color.rd_group1_accent_dark),
        GroupColorPair(R.color.rd_group2_chip_dark, R.color.rd_group2_accent_dark),
        GroupColorPair(R.color.rd_group3_chip_dark, R.color.rd_group3_accent_dark),
        GroupColorPair(R.color.rd_group4_chip_dark, R.color.rd_group4_accent_dark),
        GroupColorPair(R.color.rd_group5_chip_dark, R.color.rd_group5_accent_dark),
        GroupColorPair(R.color.rd_group6_chip_dark, R.color.rd_group6_accent_dark)
    )

    private val lightPairs = listOf(
        GroupColorPair(R.color.rd_group1_chip_light, R.color.rd_group1_accent_light),
        GroupColorPair(R.color.rd_group2_chip_light, R.color.rd_group2_accent_light),
        GroupColorPair(R.color.rd_group3_chip_light, R.color.rd_group3_accent_light),
        GroupColorPair(R.color.rd_group4_chip_light, R.color.rd_group4_accent_light),
        GroupColorPair(R.color.rd_group5_chip_light, R.color.rd_group5_accent_light),
        GroupColorPair(R.color.rd_group6_chip_light, R.color.rd_group6_accent_light)
    )

    /**
     * @param groupIndex 0-basiert (Gruppe 1 = Index 0, ... Gruppe 6 = Index 5)
     */
    fun forGroup(context: Context, groupIndex: Int): GroupColorPair {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val pairs = if (isDark) darkPairs else lightPairs
        return pairs[groupIndex]
    }
}
