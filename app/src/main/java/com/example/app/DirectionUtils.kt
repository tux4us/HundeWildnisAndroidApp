package com.hundewildnis.app

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object DirectionUtils {

    fun calculateBearing(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val dLon = Math.toRadians(toLon - fromLon)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }

    fun bearingToDirection(bearing: Double): String {
        return when {
            bearing in 0.0..22.5 || bearing > 337.5 -> "N"
            bearing in 22.5..67.5 -> "NO"
            bearing in 67.5..112.5 -> "O"
            bearing in 112.5..157.5 -> "SO"
            bearing in 157.5..202.5 -> "S"
            bearing in 202.5..247.5 -> "SW"
            bearing in 247.5..292.5 -> "W"
            bearing in 292.5..337.5 -> "NW"
            else -> "N"
        }
    }

    fun bearingToArrow(bearing: Double): String {
        return when {
            bearing in 0.0..22.5 || bearing > 337.5 -> "↑"
            bearing in 22.5..67.5 -> "↗"
            bearing in 67.5..112.5 -> "→"
            bearing in 112.5..157.5 -> "↘"
            bearing in 157.5..202.5 -> "↓"
            bearing in 202.5..247.5 -> "↙"
            bearing in 247.5..292.5 -> "←"
            bearing in 292.5..337.5 -> "↖"
            else -> "↑"
        }
    }
}