package com.hundewildnis.app

/**
 * Block 5 – Während des Trainings
 * Erfolgssignale, Warnsignale, Abbruchkriterien, Wetterhinweise
 * Getrennt nach Disziplin (0=Verloren, 1=Person, 2=Frei) × Level (0-3)
 */
object NasenarbeitSignals {

    data class Signals(val ok: List<String>, val warn: List<String>, val stop: List<String>, val wx: List<String>)

    fun getSignals(diz: Int, level: Int, bf: Int, zustand: String): Signals {
        return when(diz) {
            0 -> verlorensuche(level, bf, zustand)
            1 -> personensuche(level, bf, zustand)
            else -> freisuche(level, bf, zustand)
        }
    }

    // ── VERLORENSUCHE ─────────────────────────────────────────

    private fun verlorensuche(lev: Int, bf: Int, zustand: String): Signals {
        val ok = when(lev) {
            0 -> listOf(
                "Nasenarbeit: Nase gelegentlich bis häufig am Boden · pendelt rechts und links zur Fährte · zeigt Bemühen dem Verlauf zu folgen",
                "Tempo: Sucht flott · leichte Unsauberkeiten · starker wechselnder Leinenzug · vorwärtsgerichtet · seitwärts ausbrechend",
                "Leinendruck: Starker Zug wechselnd mit weniger starkem und seitlichem Zug",
                "Gegenstandsanzeige: Verlangsamt · bleibt stehen · verweilt in Fundnähe bis zur Lokalisierung",
                "Körpersprache: Übermotiviert, nervös → wird konzentrierter · wenige Unsicherheiten"
            )
            1 -> listOf(
                "Nasenarbeit: Nase überwiegend in Bodennähe · Pendelbewegungen werden enger",
                "Tempo: Flotte Suche · Unsauberkeiten gehen zurück · seitwärts + rückwärts Ausbrechen seltener",
                "Leinendruck: Konstanter Zug · weniger Unregelmäßigkeiten",
                "Gegenstandsanzeige: Arbeitet Ort zügig aus · Hinsetzen oder Ablegen am Gegenstand",
                "Körpersprache: Ruhiger, fokussierter · sucht fokussierter die Spur ab"
            )
            2 -> listOf(
                "Nasenarbeit: Nase überwiegend sehr nah am Boden · Pendelbewegungen gering",
                "Tempo: Flottes bis zügiges Tempo · flüssig · nur noch geringes Ausbruchsverhalten · seltenst rückwärtsgewandt",
                "Leinendruck: Konstanter Zug · kaum noch Unregelmäßigkeiten",
                "Gegenstandsanzeige: Findet recht zügig · zeigt selbständig an durch Hinsetzen, Ablegen oder Stehenbleiben",
                "Körpersprache: Sehr fokussiert · ruhiger werdend · selbstsicher trotz Störeinflüssen"
            )
            else -> listOf(
                "Nasenarbeit: Nase permanent tief am Boden · extrem geringes Pendeln · sucht direkt auf dem Fährtenverlauf",
                "Tempo: Flüssig, selbstsicher · flottes bis ruhiges Tempo · dem Verlauf folgend",
                "Leinendruck: Konstanter Zug · keine bis seltene Zugunterbrechungen",
                "Gegenstandsanzeige: Präzise · sehr schnelle Anzeige durch Hinsetzen, Hinlegen oder Stehenbleiben",
                "Körpersprache: Hoch konzentriert · sehr ökonomisch · geringe bis keine Fehler"
            )
        }
        val warn = listOf(
            "Spurverlust ${LEVEL_NAMES[lev]}: ${spurverlustV(lev)}",
            "Ablenkung: ${ablenkungV(lev)}",
            "Überforderung: Hund bleibt stehen · setzt oder legt sich hin · blickt zum Hundeführer · keine Weitersuche möglich",
            "Erschöpfung: Beginnt Suche eigenständig nicht · bricht während der Suche ab · legt sich hin"
        )
        val stop = listOf(
            "Vollständiges Desinteresse → sofort abbrechen · Pause · neuer Versuch mit besserer Motivation",
            if (lev == 3) "Spur mehrfach verlassen (über Suchleinenlänge) → Eigenkorrektur abwarten · im Zweifelsfall Abbruch · neuer Ansatz"
            else          "Spur mehrfach verlassen (über Suchleinenlänge) → sofort abbrechen · neuer Ansatz",
            "Verletzungs-, Übermüdungs-, Erschöpfungsanzeichen → sofort abbrechen"
        )
        val wx = buildWxVerlorensuche(bf, zustand)
        return Signals(ok, warn, stop, wx)
    }

    private fun spurverlustV(lev: Int) = when(lev) {
        0 -> "Bricht ab · verlässt Spur bis max. Leinenlänge · springt am Hundeführer hoch · läuft unkoordiniert mit erhobenem Kopf"
        1 -> "Hebt immer wieder den Kopf · sucht seitwärts im großen Radius · kehrt zum Hundeführer zurück · wirkt unsicher"
        2 -> "Bleibt stehen · hebt den Kopf · blickt zum Hundeführer"
        else -> "Sucht zögerlich · rechts und links der Spur kontrollsuchend · korrigiert selbständig"
    }

    private fun ablenkungV(lev: Int) = when(lev) {
        0 -> "Starke Reaktion auf Umgebungsreize · Abbruch der Suche"
        1 -> "Lässt sich ablenken · ist vom Hundeführer beeinflussbar · kehrt mit Unterstützung zur Fährte zurück"
        2 -> "Nimmt Ablenkungen wahr · lässt sich kurz ablenken · findet teilweise mit Unterstützung zurück"
        else -> "Nur noch geringes Ablenkungspotenzial · findet schnell eigenständig zur Suche zurück"
    }

    private fun buildWxVerlorensuche(bf: Int, zustand: String): List<String> {
        val wx = mutableListOf<String>()
        wx.add("Über 25°C: Trinkwasser bereithalten · schattige Strecken · kürzere Längen · unbedingt mit Erfolg abschließen")
        if (zustand in listOf("nass","feucht","stehend","matschig")) wx.add("Feuchter Boden: Sehr gute Bedingungen für Verlorensuche · Geruch haftet gut")
        if (zustand == "trocken") wx.add("Trockener Boden: Geruch haftet weniger gut · Spuralter reduzieren")
        if (zustand in listOf("schnee_m","schnee_h")) wx.add("Schnee mittel bis hoch: Verlorensuche vermeiden – Hunde neigen zur Sichtfährte statt Nasenarbeit")
        if (bf >= 6) wx.add("Starker Wind von der Seite: Hund sucht stark seitlich versetzt aufgrund des Duftüberhangs in Windrichtung")
        wx.add("Nebel: Relativ gute Bedingungen · hohe Feuchtigkeit begünstigt Geruchshaftung")
        if (zustand == "gefroren") wx.add("Gefroren: Wenig Fährtegeruch · Hund eventuell wenig motiviert · ⚠️ Verletzungsgefahr auf hart gefrorenem Boden")
        return wx
    }

    // ── PERSONENSUCHE ─────────────────────────────────────────

    private fun personensuche(lev: Int, bf: Int, zustand: String): Signals {
        val ok = when(lev) {
            0 -> listOf(
                "Nasenarbeit: Nase häufig in Spurrichtung ausgerichtet · sucht häufig mit erhobenem Kopf · zeigt Bemühen der Spur zu folgen · pendelt rechts und links",
                "Tempo: Unruhiges, wechselndes Tempo · sehr häufige Richtungsänderungen · wechselnd starker Leinenzug",
                "Personenfund: Verlangsamt · umkreist · bleibt in Nähe der Zielperson stehen · unsicheres Verhalten nimmt ab",
                "Körpersprache: Aufgeregte Körpersprache aus Übermotivation wird ausgeglichener · häufige Blicke zum Hundeführer"
            )
            1 -> listOf(
                "Nasenarbeit: Nase wechselt zwischen Bodenspur und Körperhöhe · beginnt gezielter der Spur zu folgen · Pendeln geringer",
                "Tempo: Gleichmäßiger · Richtungsänderungen werden gezielter · Leinenzug gleichmäßiger",
                "Personenfund: Geht Zielperson zügiger an · zeigt souveräner an · teils Hinsetzen, Hinlegen oder Stehenbleiben",
                "Körpersprache: Zunehmend ruhiger, fokussierter · erkennbar durch zielgerichtetes souveränes Gangbild"
            )
            2 -> listOf(
                "Nasenarbeit: Nase über große Teile der Strecke gleichbleibend in Bodennähe bzw. Körperhöhe · ruhiger · konzentrierter",
                "Tempo: Flottes gleichmäßiges Tempo · Richtungsänderungen selbständig angepasst · nur noch wenige Korrekturen",
                "Personenfund: Findet Zielperson sehr sicher · kein Umkreisen mehr · schnelle Anzeige durch Hinsetzen, Ablegen, Stehenbleiben",
                "Körpersprache: Konzentriert, selbstsicher · vorwärtssuchend · wenig Blicke zum Hundeführer"
            )
            else -> listOf(
                "Nasenarbeit: Arbeitet mit der Nase flexibel auf verschiedenen Höhen von Bodennähe bis Körperhöhe · immer präziser · kompensiert Windeinflüsse selbständig",
                "Tempo: Sucht flüssig, selbstbewusst · ruhig bis zügig · folgt dem Spurverlauf direkt · wenig Ablenkungsbereitschaft",
                "Personenfund: Präzise, schnelle Anzeige · unmittelbares Herangehen · eindeutiges Hinsetzen, Hinlegen oder Stehenbleiben",
                "Körpersprache: Hoch konzentriert · ökonomisch · weitestgehend unabhängig · praktisch keine Blickkontakte mehr nötig"
            )
        }
        val warn = listOf(
            "Spurverlust ${LEVEL_NAMES[lev]}: ${spurverlustP(lev)}",
            "Ablenkung: ${ablenkungP(lev)}",
            "Überforderung: Hund bleibt stehen · setzt oder legt sich hin · keine Möglichkeit zur Weitersuche",
            "Erschöpfung: Beginnt Suche nicht eigenständig · bricht ab · legt sich hin"
        )
        val stop = listOf(
            "Vollständiges Desinteresse → sofort abbrechen · größere Pause · neuer Versuch mit besserer Motivation",
            if (lev == 3) "Spur deutlich verlassen → Eigenkorrektur abwarten · kurze Pause · Motivation · neuer Ansatz"
            else          "Spur deutlich verlassen → kurze Pause · Motivation · neuer Ansatz",
            "Verletzungs-, Übermüdungs-, Erschöpfungsanzeichen → sofort abbrechen"
        )
        val wx = buildWxPersonensuche(bf, zustand)
        return Signals(ok, warn, stop, wx)
    }

    private fun spurverlustP(lev: Int) = when(lev) {
        0 -> "Verlässt Spur · läuft ziellos mit erhobenem Kopf · kehrt häufig zum Hundeführer zurück · springt hoch oder unruhig"
        1 -> "Hebt häufig den Kopf · sucht im großen Radius · kehrt zum Hundeführer zurück · wirkt unsicher"
        2 -> "Zögert · hebt kurz den Kopf · blickt zum Hundeführer · sucht selbständig im näheren Radius"
        else -> "Minimales Zögern · korrigiert sich weitgehend selbständig · selten Einweisung notwendig"
    }

    private fun ablenkungP(lev: Int) = when(lev) {
        0 -> "Starke Reaktion · Abbruch der Suche · schwer zur Spur zurückzuführen"
        1 -> "Lässt sich auf Ablenkungen ein · mit Unterstützung des Hundeführers zurückführbar"
        2 -> "Kurze Ablenkung · findet teilweise selbständig zur Suche zurück"
        else -> "Minimale Ablenkungsbereitschaft · kehrt nur mit Hilfe zur Suche zurück"
    }

    private fun buildWxPersonensuche(bf: Int, zustand: String): List<String> {
        val wx = mutableListOf<String>()
        wx.add("Über 25°C: Trinkwasser bereithalten · kürzere Streckenlängen · schattige Routen · mit Erfolg abschließen")
        wx.add("Starkregen: Personengeruch stark verwässert · eventuell demotiviert · sehr kurze Strecke · mit Erfolg abschließen")
        wx.add("Schnee leicht: Keine größeren Einschränkungen")
        wx.add("Schnee mittel bis hoch: Spurverfolgung erschwert · Geruch verändert · Streckenlänge reduzieren")
        wx.add("Wind von hinten: Geruch driftet in Laufrichtung · Hund sucht vorrangig Bodenspur · kein größeres Problem")
        wx.add("Wind von der Seite: Personengeruch stark seitlich versetzt · Hund sucht versetzt zur eigentlichen Spur · ⚠️ beachten")
        wx.add("Wind von vorn: Geruch der Zielperson weht dem Hund entgegen · Hund kann Zielperson bereits auf Distanz wahrnehmen · begünstigt die Suche")
        wx.add("Nebel: Hohe Luftfeuchtigkeit hält Geruch gut · gute Bedingungen · relative Windstille → kein optimaler Witterungszutrag · engmaschig absuchen")
        if (zustand == "gefroren") wx.add("Gefroren: Bodenspur reduziert · eventuell weniger motiviert · ⚠️ Verletzungsgefahr auf hart gefrorenem Boden")
        return wx
    }

    // ── FREISUCHE ─────────────────────────────────────────────

    private fun freisuche(lev: Int, bf: Int, zustand: String): Signals {
        val ok = when(lev) {
            0 -> listOf(
                "Nasenarbeit + Suchmuster: Nase wechselnd am Boden und in der Luft · steigende Suchmotivation · unstrukturiert → strukturierter per Hilfestellung des Hundeführers",
                "Anzeige: Verlangsamt in Fundnähe · zeigt durch Stehenbleiben, Hinsetzen, Umkreisen, Anspringen · eventuell Lautgeben",
                "Körpersprache: Hoch motiviert · starke Orientierung am Hundeführer · nimmt Einweisungen zunehmend besser an"
            )
            1 -> listOf(
                "Nasenarbeit + Suchmuster: Beginnt Fläche gezielter abzusuchen in größeren Rechts-Links-Schlägen · orientiert sich besser an Einweisungen · gleichmäßiger",
                "Anzeige: Zeigt zunehmend eindeutiger an durch Hinsetzen, Hinlegen, Stehenbleiben, Lautgeben · Anzeige wird stabiler",
                "Körpersprache: Ruhiger, fokussierter · Orientierung am Hundeführer stabiler · lässt sich zunehmend besser einweisen"
            )
            2 -> listOf(
                "Nasenarbeit + Suchmuster: Suche konstant aktiv · deckt Fläche methodischer ab · zunehmend selbständig",
                "Anzeige: Klares, kontrollierbares Anzeigeverhalten · Hinsetzen, Hinlegen, Stehenbleiben, Lautgeben · zügig und selbständig",
                "Körpersprache: Selbstsicher · souverän · ruhige Ausstrahlung · nimmt Einweisungen präzise an"
            )
            else -> listOf(
                "Nasenarbeit + Suchmuster: Sucht durchgängig aktiv ohne Ermüdungserscheinungen · sehr präzise Flächenabdeckung · lässt sich gezielt und weiträumig schicken",
                "Anzeige: Präzise, schnelle Anzeige · kehrt bei Bedarf selbständig zum Hundeführer zurück um diesen zum Fund zu führen",
                "Körpersprache: Hoch konzentriert · energieökonomisch · weitgehend unabhängig · Lenkung über Akustik + Handzeichen"
            )
        }
        val warn = listOf(
            "Flächenverlust ${LEVEL_NAMES[lev]}: ${flaechenverlust(lev)}",
            "Ablenkung: ${ablenkungFl(lev)}",
            "Überforderung: Hund bleibt stehen · Abbruch · setzt oder legt sich hin · blickt dauerhaft zum Hundeführer · keine Möglichkeit zur Weitersuche",
            "Erschöpfung: Kein eigenständiger Suchbeginn · bricht während der Suche ab · legt sich hin · keine Suchmotivation"
        )
        val stop = listOf(
            "Vollständiges Desinteresse → sofort abbrechen · größere Pause · neuer Versuch mit besserer Motivation",
            if (lev == 3) "Fläche verlassen → Eigenkorrektur abwarten · im Zweifelsfall Abbruch · neuer Ansatz"
            else          "Fläche verlassen → kurze Pause · Motivation · neuer Ansatz",
            "Verletzungs-, Übermüdungs-, Erschöpfungsanzeichen → sofort abbrechen"
        )
        val wx = buildWxFreisuche(bf, zustand)
        return Signals(ok, warn, stop, wx)
    }

    private fun flaechenverlust(lev: Int) = when(lev) {
        0 -> "Verlässt Suchfläche · läuft ziellos umher · häufige Rückkehr zum Hundeführer · sucht eng am Hundeführer"
        1 -> "Unstrukturierte Suche mit Verlassen der Fläche · benötigt starke Hilfestellung · löst sich schlecht vom Hundeführer"
        2 -> "Verlässt Suchstrategie mehrmals · sehr zögerliche Suche · häufiger Blickkontakt zum Hundeführer · verlässt zu suchende Fläche"
        else -> "Weicht von Suchstrategie ab · selbständige Korrektur nicht möglich · starke Einweisung notwendig"
    }

    private fun ablenkungFl(lev: Int) = when(lev) {
        0 -> "Starke Reaktion auf Umgebungsreize · Abbruch der Suche · schwierige Rückführung in die Suchfläche"
        1 -> "Lässt sich auf Ablenkungsreize ein · mit Unterstützung des Hundeführers zurückführbar"
        2 -> "Starke Ablenkung · findet geduldig und teilweise selbständig zur Suche zurück"
        else -> "Minimale Ablenkungsbereitschaft · kurze Unterbrechung · kehrt nur mit Hilfe zur Suche zurück"
    }

    private fun buildWxFreisuche(bf: Int, zustand: String): List<String> {
        val wx = mutableListOf<String>()
        wx.add("Über 25°C: Trinkwasser bereithalten · Suchfläche reduzieren · schattige Bereiche bevorzugen · mit Erfolg abschließen")
        wx.add("Starkregen: Geruch stark verwässert · Fläche stark reduzieren · ⚠️ feuchte/nasse Flächen Unfallgefahr · vorsichtig trainieren")
        if (zustand in listOf("schnee_l")) wx.add("Schnee leicht: ⚠️ Vorsicht · Rutschgefahr · Verletzungsgefahr")
        if (zustand in listOf("schnee_m","schnee_h")) wx.add("Schnee mittel bis hoch: Freisuche den Verhältnissen anpassen · Gegenstände zweckmäßig platzieren · Personensuche möglich")
        wx.add("Wind von hinten: Eigengeruch beachten · könnte Hund irritieren · Suchstrategie + Einstiegspunkt ggf. anpassen")
        wx.add("Wind von der Seite: Geruch driftet stark seitlich ab · Hund sucht sehr stark versetzt · ⚠️ teils außerhalb des definierten Geländes")
        wx.add("Wind von vorn: Geruch wird dem Hund entgegengetragen · begünstigt Freisuche · Zielobjekte und Zielpersonen frühzeitig lokalisierbar")
        wx.add("Nebel: Hohe Luftfeuchtigkeit hält Geruch gut · aufgrund relativer Windstille kein optimaler Witterungszutrag · sehr engmaschig absuchen")
        if (zustand == "gefroren") wx.add("Gefroren: Teilweise stark reduzierte Geruchsentwicklung · Hund eventuell weniger motiviert · ⚠️ Verletzungsgefahr · Fläche + Tempo reduzieren")
        return wx
    }

    private val LEVEL_NAMES = arrayOf("Anfänger","Fortgeschritten","Erfahren","Experte")
}
