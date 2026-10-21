package com.hundewildnis.app

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * BRÜCKEN-FINDER für die Gewitterschutz-Kategorie
 *
 * WICHTIG: Es findet KEINE automatische Eignungsprüfung mehr statt (weder
 * Höhenvergleich noch Überflutungsanalyse). Ein früherer Versuch, Brücken
 * per Höhendaten nach "im Geländeeinschnitt liegend" zu filtern, erwies sich
 * als unzuverlässig: er erkennt nur die unmittelbare Umgebung (~60m seitlich),
 * nicht aber Exposition durch weiter entfernte Berghänge/Bäume oder
 * umgekehrt geschützte Taleinschnitte in flachem Gelände. Da eine
 * automatische Einschätzung hier mehr Schein- als echte Sicherheit gegeben
 * hätte, werden jetzt ALLE gefundenen Brücken angezeigt – die fachliche
 * Einschätzung (Exposition, Überflutungsrisiko) muss der Nutzer vor Ort
 * selbst treffen. Ein deutlicher Warnhinweis begleitet die Anzeige
 * (siehe showThunderstormShelterHint in LocationActivity_POI.kt).
 *
 * Die Overpass-Abfrage sucht bewusst nach dem Tag bridge=yes ohne die
 * zusätzliche Einschränkung auf highway=*, da Brücken-Tags auch auf
 * Way-Segmenten liegen können, deren sonstiges Tag-Set variiert (z. B.
 * längere benannte Straßen, die in mehrere Ways mit unterschiedlichen
 * Zusatz-Tags aufgeteilt sind). Das verhindert, dass real vorhandene
 * Brücken durch eine zu enge Abfrage übersehen werden.
 */
object BridgeExposureFilter {

    private const val MAX_BRIDGES_TO_SHOW = 5
    private const val EARTH_RADIUS_METERS = 6371000.0

    data class BridgeCandidate(
        val id: String,
        val name: String,
        val centerLat: Double,
        val centerLon: Double
    )

    /**
     * Fragt alle Brücken (bridge=yes) im Umkreis ab und gibt die 5
     * nächstgelegenen zurück, sortiert nach Entfernung zum Standort.
     * Keine Höhen- oder Eignungsprüfung mehr – siehe Klassenkommentar.
     */
    suspend fun fetchNearbyBridges(context: android.content.Context, lat: Double, lon: Double, radiusMeters: Int): List<BridgeCandidate> {
        val bridges = fetchBridgeWays(context, lat, lon, radiusMeters)
        if (bridges.isEmpty()) return emptyList()

        return bridges
            .map { bridge ->
                val (midLat, midLon) = bridge.nodes[bridge.nodes.size / 2]
                BridgeCandidate(id = bridge.id, name = bridge.name, centerLat = midLat, centerLon = midLon)
            }
            .sortedBy { haversineDistanceMeters(lat, lon, it.centerLat, it.centerLon) }
            .take(MAX_BRIDGES_TO_SHOW)
    }

    /** Distanz zwischen zwei Koordinaten in Metern (Haversine-Formel). */
    private fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    private data class RawBridge(val id: String, val name: String, val nodes: List<Pair<Double, Double>>)

    private suspend fun fetchBridgeWays(context: android.content.Context, lat: Double, lon: Double, radiusMeters: Int): List<RawBridge> {
        // Bewusst nur nach bridge=yes gefiltert, OHNE zusätzliches highway-Tag,
        // damit auch Brücken-Segmente längerer Straßen mit variierendem
        // Tag-Set zuverlässig gefunden werden.
        val query = """
            [out:json][timeout:20];
            (
              way["bridge"="yes"](around:$radiusMeters,$lat,$lon);
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()

        val json = OverpassHelper.queryWithRetry(context, query, maxRetries = 1) ?: return emptyList()
        val elements = json.getJSONArray("elements")

        // Erst alle Nodes indizieren, dann Ways daraus zusammensetzen
        val nodeCoords = mutableMapOf<String, Pair<Double, Double>>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            if (el.getString("type") == "node") {
                nodeCoords[el.getString("id")] = el.getDouble("lat") to el.getDouble("lon")
            }
        }

        val bridges = mutableListOf<RawBridge>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            if (el.getString("type") != "way") continue
            val nodeIds = el.optJSONArray("nodes") ?: continue
            val coords = mutableListOf<Pair<Double, Double>>()
            for (j in 0 until nodeIds.length()) {
                nodeCoords[nodeIds.getString(j)]?.let { coords.add(it) }
            }
            if (coords.isEmpty()) continue

            val tags = el.optJSONObject("tags")
            val name = tags?.optString("name")?.takeIf { it.isNotBlank() }
                ?: tags?.optString("ref")?.takeIf { it.isNotBlank() }
                ?: "Brücke"

            bridges.add(RawBridge(id = el.getString("id"), name = name, nodes = coords))
        }
        return bridges
    }
}