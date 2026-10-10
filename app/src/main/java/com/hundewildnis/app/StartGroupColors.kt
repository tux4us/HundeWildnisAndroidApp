package com.hundewildnis.app

import android.content.Context

/**
 * Zentrale Auflösung der Gruppenfarben für das Glas-Design.
 *
 * Jede Gruppe hat eine Akzentfarbe (Icon, Text, Rand) und eine getönte Glasfüllung (chip).
 * Beide stehen in res/values/colors_glass.xml. Die App ist ausschließlich dunkel,
 * es gibt daher nur noch einen Satz Farben.
 */
object StartGroupColors {

    data class GroupColorPair(val chipColorRes: Int, val accentColorRes: Int)

    private val pairs = listOf(
        GroupColorPair(R.color.rd_group1_chip, R.color.rd_group1_accent),
        GroupColorPair(R.color.rd_group2_chip, R.color.rd_group2_accent),
        GroupColorPair(R.color.rd_group3_chip, R.color.rd_group3_accent),
        GroupColorPair(R.color.rd_group4_chip, R.color.rd_group4_accent),
        GroupColorPair(R.color.rd_group5_chip, R.color.rd_group5_accent),
        GroupColorPair(R.color.rd_group6_chip, R.color.rd_group6_accent)
    )

    /**
     * @param groupIndex 0-basiert (Gruppe 1 = Index 0, ... Gruppe 6 = Index 5)
     */
    @Suppress("UNUSED_PARAMETER")
    fun forGroup(context: Context, groupIndex: Int): GroupColorPair = pairs[groupIndex]
}
