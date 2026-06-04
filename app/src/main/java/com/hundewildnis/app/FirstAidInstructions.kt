package com.hundewildnis.app

// ════════════════════════════════════════════════════════════════
// ERSTE-HILFE ANWEISUNGEN DATENBANK
// Neu erstellen: FirstAidInstructions.kt
// ════════════════════════════════════════════════════════════════

object FirstAidInstructions {

    /**
     * Alle Erste-Hilfe Anleitungen
     */
    fun getAll(): Map<String, FirstAidInstruction> = mapOf(

        // ════════════════════════════════════════════════════════
        // BEWUSSTLOSIGKEIT
        // ════════════════════════════════════════════════════════
        "UNCONSCIOUSNESS" to FirstAidInstruction(
            type = "UNCONSCIOUSNESS",
            title = "Bewusstlosigkeit",
            urgency = UrgencyLevel.CRITICAL,
            symptoms = listOf(
                "Keine Reaktion auf Ansprache",
                "Keine Reaktion auf Berührung",
                "Schlaffer Muskeltonus",
                "Augen ggf. offen mit starrem Blick"
            ),
            immediateActions = listOf(
                "1️⃣ ATEMWEGE FREI MACHEN",
                "• Kopf leicht nach hinten überstrecken",
                "• Zunge nach vorne ziehen",
                "• Fremdkörper entfernen (sichtbar!)",
                "",
                "2️⃣ ATMUNG & PULS PRÜFEN",
                "• Brustkorb beobachten (hebt/senkt sich?)",
                "• Puls an Oberschenkelinnenseite fühlen",
                "• KEIN PULS → SOFORT HLW!",
                "",
                "3️⃣ STABILE SEITENLAGE",
                "• Hund auf RECHTE Seite legen",
                "• Kopf leicht gestreckt",
                "• Zunge raus",
                "• Atemwege frei halten"
            ),
            doList = listOf(
                "✅ Atemwege freihalten",
                "✅ Stabile Seitenlage (rechts!)",
                "✅ Alle 30 Sek. Atmung/Puls prüfen",
                "✅ SOFORT Tierarzt anrufen",
                "✅ Warm halten (Decke)"
            ),
            dontList = listOf(
                "❌ KEIN Wasser eingeben",
                "❌ KEINE Medikamente",
                "❌ NICHT allein lassen",
                "❌ NICHT zu stark bewegen"
            ),
            transportInfo = "Vorsichtig auf festem Brett/Decke. Kopf gestreckt halten.",
            needsHLW = true
        ),

        // ════════════════════════════════════════════════════════
        // HITZSCHLAG
        // ════════════════════════════════════════════════════════
        "HEAT_STROKE" to FirstAidInstruction(
            type = "HEAT_STROKE",
            title = "Hitzschlag",
            urgency = UrgencyLevel.CRITICAL,
            symptoms = listOf(
                "Starkes, lautes Hecheln",
                "Ziegelrote Schleimhäute",
                "Glasige Augen",
                "Speichelfluss",
                "Taumeln, Koordinationsstörungen",
                "Erbrechen, Durchfall",
                "Körpertemperatur > 40,5°C"
            ),
            immediateActions = listOf(
                "1️⃣ SOFORT IN DEN SCHATTEN",
                "",
                "2️⃣ LANGSAM KÜHLEN (15-20 Min)",
                "• Lauwarmes (!) Wasser",
                "• Von Pfoten → Beine → Körper",
                "• KEIN Eiswasser!",
                "• NICHT in nasse Tücher wickeln!",
                "",
                "3️⃣ ZAHNFLEISCH BEFEUCHTEN",
                "• Wenige Tropfen Wasser",
                "• Nur wenn bewusst!",
                "",
                "4️⃣ TIERARZT - SOFORT!"
            ),
            doList = listOf(
                "✅ Sofort Schatten",
                "✅ Lauwarmes Wasser von unten",
                "✅ Luftzirkulation (Fächer)",
                "✅ Zahnfleisch feucht halten",
                "✅ Temperatur messen (rektal)"
            ),
            dontList = listOf(
                "❌ KEIN Eiswasser",
                "❌ NICHT in feuchte Tücher wickeln",
                "❌ NICHT große Mengen trinken lassen",
                "❌ NICHT zu schnell kühlen"
            ),
            transportInfo = "Klimaanlage an. Fenster offen. Unterwegs weiter leicht kühlen.",
            specialNotes = "🚨 Auch nach scheinbarer Erholung IMMER zum Tierarzt! " +
                    "Organschäden können verzögert auftreten!",
            needsTimer = true,
            timerMinutes = 15
        ),

        // ════════════════════════════════════════════════════════
        // VERGIFTUNG
        // ════════════════════════════════════════════════════════
        "POISONING_KNOWN" to FirstAidInstruction(
            type = "POISONING_KNOWN",
            title = "Vergiftung (bekannt)",
            urgency = UrgencyLevel.CRITICAL,
            symptoms = listOf(
                "Erbrechen, Durchfall",
                "Zittern, Krämpfe",
                "Schläfrigkeit / Lethargie",
                "Atemnot, Herzrasen",
                "Speichelfluss",
                "Blasse/Blaue Schleimhäute"
            ),
            immediateActions = listOf(
                "1️⃣ TIERGIFTNOTRUF SOFORT ANRUFEN",
                "📞 Berlin: 030 19240",
                "📞 München: 089 19240",
                "📞 Freiburg: 0761 19240",
                "",
                "2️⃣ SUBSTANZ SICHERN",
                "• Verpackung mitnehmen!",
                "• Erbrochenes in Tüte",
                "• Pflanzenprobe (Handschuhe!)",
                "",
                "3️⃣ KEIN ERBRECHEN OHNE ANWEISUNG!",
                "",
                "4️⃣ SOFORT ZUM TIERARZT"
            ),
            doList = listOf(
                "✅ Giftzentrale anrufen",
                "✅ Verpackung/Probe mitnehmen",
                "✅ Zeitpunkt notieren",
                "✅ Menge schätzen",
                "✅ Ruhig halten"
            ),
            dontList = listOf(
                "❌ KEIN Erbrechen provozieren!",
                "❌ KEINE Milch geben",
                "❌ KEIN Öl geben",
                "❌ NICHT abwarten!"
            ),
            transportInfo = "Verpackung/Gift mitnehmen. Erbrochenes in Tüte. " +
                    "Hund ruhig halten.",
            specialNotes = "⚠️ TIERGIFTZENTRALE: Berlin 030 19240, München 089 19240, " +
                    "Freiburg 0761 19240"
        ),

        // ════════════════════════════════════════════════════════
        // MAGENDREHUNG
        // ════════════════════════════════════════════════════════
        "GASTRIC_TORSION" to FirstAidInstruction(
            type = "GASTRIC_TORSION",
            title = "Magendrehung",
            urgency = UrgencyLevel.CRITICAL,
            symptoms = listOf(
                "Aufgeblähter, harter Bauch",
                "Erfolgloses Würgen ohne Erbrechen",
                "Starke Unruhe",
                "Speichelfluss",
                "Schnelle, flache Atmung",
                "Blasse Schleimhäute",
                "Schwäche, Kollaps"
            ),
            immediateActions = listOf(
                "🚨 ABSOLUTE NOTFALL-SITUATION!",
                "",
                "1️⃣ SOFORT TIERKLINIK ANRUFEN",
                "• Operation innerhalb von 1-2h nötig!",
                "",
                "2️⃣ NICHT MEHR FÜTTERN/TRÄNKEN",
                "",
                "3️⃣ BEWEGUNG MINIMIEREN",
                "• Ruhig halten",
                "• Nicht rennen lassen",
                "",
                "4️⃣ SCHNELLSTER TRANSPORT",
                "• Jede Minute zählt!",
                "• Klinik vorab informieren"
            ),
            doList = listOf(
                "✅ SOFORT Tierklinik",
                "✅ Vorab anrufen",
                "✅ Bewegung minimieren",
                "✅ Zeit notieren (wichtig!)"
            ),
            dontList = listOf(
                "❌ KEIN Futter/Wasser",
                "❌ NICHT warten",
                "❌ NICHT selbst behandeln",
                "❌ KEINE Bewegung"
            ),
            transportInfo = "Vorsichtig transportieren. Bauch nicht drücken. " +
                    "SCHNELLSTE Route zur Klinik!",
            specialNotes = "⚠️ LEBENSBEDROHLICH! Überlebenschance sinkt mit jeder Minute. " +
                    "Risikohunde: Schäferhund, Dogge, Setter, Dobermann, Sennenhunde."
        ),

        // ════════════════════════════════════════════════════════
        // KRAMPFANFALL
        // ════════════════════════════════════════════════════════
        "SEIZURE" to FirstAidInstruction(
            type = "SEIZURE",
            title = "Krampfanfall / Epilepsie",
            urgency = UrgencyLevel.CRITICAL,
            symptoms = listOf(
                "Plötzlicher Bewusstseinsverlust",
                "Unkontrollierte Muskelzuckungen",
                "Paddelbewegungen",
                "Kieferschlagen",
                "Speichelfluss, Schaum",
                "Unkontrollierter Kot-/Urinabsatz"
            ),
            immediateActions = listOf(
                "1️⃣ UMGEBUNG SICHERN",
                "• Möbel wegräumen",
                "• Polster unterlegen",
                "• Raum abdunkeln",
                "",
                "2️⃣ ZEIT STOPPEN",
                "• Dauer notieren!",
                "• > 5 Min = NOTFALL",
                "",
                "3️⃣ NICHT FESTHALTEN",
                "• Hund nicht berühren!",
                "• Nicht in Maul greifen!",
                "",
                "4️⃣ NACH DEM ANFALL",
                "• Ruhig bleiben",
                "• Sanft ansprechen",
                "• Nicht bedrängen"
            ),
            doList = listOf(
                "✅ Umgebung sichern",
                "✅ Zeit stoppen",
                "✅ Ruhig bleiben",
                "✅ Raum abdunkeln",
                "✅ Nach > 5 Min → Tierarzt"
            ),
            dontList = listOf(
                "❌ NICHT festhalten",
                "❌ NICHT ins Maul greifen",
                "❌ NICHT erschrecken",
                "❌ NICHT bedrängen danach"
            ),
            transportInfo = "Nach Anfall: Hund ist desorientiert. Langsam, vorsichtig. " +
                    "Kann aggressiv reagieren.",
            specialNotes = "⚠️ Tierarzt wenn: > 5 Min Dauer, mehrere Anfälle hintereinander, " +
                    "erster Anfall überhaupt.",
            needsTimer = true,
            timerMinutes = 5
        ),

        // ════════════════════════════════════════════════════════
        // STARKE BLUTUNG
        // ════════════════════════════════════════════════════════
        "BLEEDING" to FirstAidInstruction(
            type = "BLEEDING",
            title = "Starke Blutung",
            urgency = UrgencyLevel.CRITICAL,
            symptoms = listOf(
                "Hellrotes, pulsierendes Blut (Arterie - NOTFALL!)",
                "Dunkelrotes, stetiges Blut (Vene)",
                "Große Wundfläche",
                "Schockzeichen: blasse Schleimhäute, Schwäche"
            ),
            immediateActions = listOf(
                "1️⃣ DRUCKVERBAND ANLEGEN",
                "• Sterile Kompresse auf Wunde",
                "• Fest andrücken (2-3 Min)",
                "• Mit Binde fixieren",
                "• NICHT zu fest!",
                "",
                "2️⃣ VERBAND NICHT ENTFERNEN",
                "• Blutet durch → 2. Lage drauf",
                "• Nie alten Verband abnehmen!",
                "",
                "3️⃣ HUND RUHIG HALTEN",
                "• Keine Bewegung",
                "• Warm halten",
                "",
                "4️⃣ SOFORT TIERARZT"
            ),
            doList = listOf(
                "✅ Druckverband",
                "✅ Fest andrücken",
                "✅ Ruhig halten",
                "✅ Warm halten (Schock!)",
                "✅ SOFORT Tierarzt"
            ),
            dontList = listOf(
                "❌ NICHT Verband abnehmen",
                "❌ KEINE Abbindung (Tourniquet)",
                "❌ NICHT Wunde ausspülen",
                "❌ NICHT zu fest abbinden"
            ),
            transportInfo = "Vorsichtig. Verletzte Stelle ruhig stellen. " +
                    "Bei Schock: Hinterbeine leicht erhöhen.",
            specialNotes = "🚨 Arteriell (hellrot, pulsierend) = HÖCHSTE GEFAHR! " +
                    "Jede Minute zählt!"
        )

        ,

        // ════════════════════════════════════════════════════════
        // KNOCHENBRUCH
        // ════════════════════════════════════════════════════════
        "BONE_FRACTURE" to FirstAidInstruction(
            type = "BONE_FRACTURE",
            title = "Knochenbruch / Lahmheit",
            urgency = UrgencyLevel.URGENT,
            symptoms = listOf(
                "Humpeln oder nicht auftreten",
                "Abnormale Stellung der Gliedmaße",
                "Sichtbare Knochenteile (offener Bruch!)",
                "Schwellung, Schmerzen",
                "Schonhaltung"
            ),
            immediateActions = listOf(
                "1️⃣ HUND RUHIG HALTEN",
                "• Keine Bewegung!",
                "• Nicht aufstehen lassen",
                "",
                "2️⃣ NICHT SCHIENEN",
                "• Nur bei extremer Outdoor-Notsituation",
                "• Falsches Schienen → mehr Schaden!",
                "",
                "3️⃣ BEI OFFENER FRAKTUR",
                "• Sterile Kompresse auflegen",
                "• Nicht reinigen!",
                "• Locker abdecken",
                "",
                "4️⃣ BEWEGUNGSARMER TRANSPORT"
            ),
            doList = listOf(
                "✅ Bewegung minimieren",
                "✅ Ruhig halten",
                "✅ Offene Wunde abdecken",
                "✅ Vorsichtiger Transport",
                "✅ Schmerzäußerungen beachten"
            ),
            dontList = listOf(
                "❌ NICHT schienen (Ausnahme: Profi)",
                "❌ NICHT Knochen zurückdrücken",
                "❌ NICHT offene Wunde spülen",
                "❌ NICHT bewegen lassen"
            ),
            transportInfo = "Brett/Trage. Bei Wirbelsäulenverdacht: keine Rotation! " +
                    "Kleinhunde: stabiler Karton.",
            specialNotes = "⚠️ Wirbelsäulenverletzung? Lähmung, Inkontinenz → " +
                    "ABSOLUT KEINE BEWEGUNG!"
        ),

        // ════════════════════════════════════════════════════════
        // AUGENVERLETZUNG
        // ════════════════════════════════════════════════════════
        "EYE_INJURY" to FirstAidInstruction(
            type = "EYE_INJURY",
            title = "Augenverletzung",
            urgency = UrgencyLevel.URGENT,
            symptoms = listOf(
                "Zugekniffenes Auge",
                "Rötung, Schwellung",
                "Tränenfluss",
                "Sichtbare Verletzung",
                "Trübe Hornhaut",
                "Fremdkörper sichtbar"
            ),
            immediateActions = listOf(
                "1️⃣ NICHT REIBEN LASSEN",
                "• Halskragen/Trichter!",
                "",
                "2️⃣ BEI VERSCHMUTZUNG (keine Verletzung)",
                "• Sterile NaCl 0,9% Lösung",
                "• Von innen nach außen spülen",
                "• Sanft, kein Druck",
                "",
                "3️⃣ BEI VERLETZUNG",
                "• NICHT spülen!",
                "• Feuchte Kompresse auflegen",
                "• Locker abdecken",
                "",
                "4️⃣ SOFORT TIERARZT"
            ),
            doList = listOf(
                "✅ Halskragen anlegen",
                "✅ Bei Schmutz: NaCl spülen",
                "✅ Feuchte Kompresse",
                "✅ Abdunkeln (Licht meiden)",
                "✅ SOFORT Tierarzt"
            ),
            dontList = listOf(
                "❌ NICHT reiben lassen",
                "❌ NICHT Fremdkörper entfernen",
                "❌ KEINE Salben ohne Tierarzt",
                "❌ NICHT bei Verletzung spülen"
            ),
            transportInfo = "Abdunkeln. Ruhig halten. Halskragen anlegen. " +
                    "Auge feucht halten.",
            specialNotes = "🚨 Hornhautverletzung = NOTFALL! Jede Stunde zählt für Sehkraft."
        ),

        // ════════════════════════════════════════════════════════
        // ERTRINKEN
        // ════════════════════════════════════════════════════════
        "DROWNING" to FirstAidInstruction(
            type = "DROWNING",
            title = "Ertrinken / Beinahe-Ertrinken",
            urgency = UrgencyLevel.CRITICAL,
            symptoms = listOf(
                "Wasser in Atemwegen",
                "Husten, Würgen",
                "Atemnot",
                "Blau-graue Schleimhäute",
                "Bewusstlosigkeit",
                "Unterkühlung"
            ),
            immediateActions = listOf(
                "1️⃣ WASSER AUS ATEMWEGEN",
                "• Hinterbeine 15-30 Sek. hochhalten",
                "• Kopf nach unten",
                "• Wasser ablaufen lassen",
                "",
                "2️⃣ ATMUNG & PULS PRÜFEN",
                "• Kein Puls → HLW sofort!",
                "",
                "3️⃣ TROCKNEN & WÄRMEN",
                "• Nasses Fell trocknen",
                "• In Decken wickeln",
                "• Körperwärme",
                "",
                "4️⃣ AUCH BEI ERHOLUNG: TIERARZT!",
                "• Lungenödem kann verzögert auftreten"
            ),
            doList = listOf(
                "✅ Wasser aus Atemwegen",
                "✅ Trocknen",
                "✅ Aufwärmen",
                "✅ Atmung/Puls überwachen",
                "✅ IMMER zum Tierarzt (24-48h Risiko!)"
            ),
            dontList = listOf(
                "❌ NICHT zu lange kopfüber halten",
                "❌ NICHT zu schnell erwärmen",
                "❌ NICHT allein lassen",
                "❌ NICHT auf Erholung verlassen"
            ),
            transportInfo = "Warm halten. Atemwege frei. Beobachten auf Atemnot.",
            specialNotes = "⚠️ NACHBEOBACHTUNG 24-48h PFLICHT! Verzögertes Lungenödem möglich.",
            needsHLW = true
        ),

        // ════════════════════════════════════════════════════════
        // UNTERKÜHLUNG
        // ════════════════════════════════════════════════════════
        "HYPOTHERMIA" to FirstAidInstruction(
            type = "HYPOTHERMIA",
            title = "Unterkühlung",
            urgency = UrgencyLevel.URGENT,
            symptoms = listOf(
                "Körpertemperatur < 37,5°C",
                "Zittern",
                "Steife Muskeln",
                "Apathie, Lethargie",
                "Verlangsamter Puls",
                "Kalte Haut"
            ),
            immediateActions = listOf(
                "1️⃣ NASSES FELL SOFORT TROCKNEN",
                "• Handtücher",
                "• Föhn (lauwarm, Abstand!)",
                "",
                "2️⃣ LANGSAM AUFWÄRMEN",
                "• In Decken wickeln",
                "• Körperwärme",
                "• Warme Umgebung",
                "",
                "3️⃣ WARME GETRÄNKE",
                "• Nur wenn bewusst!",
                "• Lauwarm, nicht heiß",
                "• Kleine Mengen",
                "",
                "4️⃣ BEI < 35°C → TIERARZT NOTFALL"
            ),
            doList = listOf(
                "✅ Trocknen",
                "✅ Langsam aufwärmen",
                "✅ Decken, Körperwärme",
                "✅ Temperatur messen",
                "✅ Bei < 35°C → Notfall!"
            ),
            dontList = listOf(
                "❌ NICHT zu schnell erwärmen",
                "❌ KEINE Wärmflasche direkt auf Haut",
                "❌ KEIN heißes Wasser",
                "❌ KEIN Alkohol"
            ),
            transportInfo = "Warm einpacken. Temperatur überwachen. " +
                    "Bei < 35°C sofort Klinik.",
            specialNotes = "🧊 Nach Eiseinbruch: EIGENSICHERUNG BEACHTEN!"
        ),

        // ════════════════════════════════════════════════════════
        // FREMDKÖRPER IM SCHLUND
        // ════════════════════════════════════════════════════════
        "FOREIGN_OBJECT" to FirstAidInstruction(
            type = "FOREIGN_OBJECT",
            title = "Fremdkörper verschluckt",
            urgency = UrgencyLevel.URGENT,
            symptoms = listOf(
                "Plötzliches Würgen, Husten",
                "Pfoten ans Maul",
                "Speichelfluss",
                "Blaue/graue Schleimhäute (Erstickung!)",
                "Erstickungsanfall"
            ),
            immediateActions = listOf(
                "1️⃣ MAUL ÖFFNEN & SCHAUEN",
                "• NUR wenn sichtbar!",
                "• Nicht blind greifen",
                "",
                "2️⃣ BEI SICHTBAREM OBJEKT",
                "• Vorsichtig entfernen",
                "• Mit Finger/Pinzette",
                "",
                "3️⃣ BEI ERSTICKUNG (HEIMLICH-GRIFF)",
                "• Hund aufrecht/stehend",
                "• Hände unter Rippenbogen",
                "• 5x kräftig nach oben drücken",
                "• Prüfen ob Objekt raus",
                "",
                "4️⃣ SOFORT TIERARZT",
                "• Auch wenn Objekt raus!"
            ),
            doList = listOf(
                "✅ Maul inspizieren (vorsichtig!)",
                "✅ Nur sichtbare Objekte entfernen",
                "✅ Bei Erstickung: Heimlich-Griff",
                "✅ IMMER zum Tierarzt danach"
            ),
            dontList = listOf(
                "❌ NICHT blind in Rachen greifen",
                "❌ NICHT zu tief greifen",
                "❌ KEIN Wasser eingeben",
                "❌ NICHT abwarten"
            ),
            transportInfo = "Kopf leicht nach unten. Maul offen halten. " +
                    "Notfalls weiter Heimlich-Griff.",
            specialNotes = "🚨 Blaue Schleimhäute = AKUTE ERSTICKUNG! " +
                    "Heimlich-Griff SOFORT!"
        ),

        // ════════════════════════════════════════════════════════
        // ATEMNOT
        // ════════════════════════════════════════════════════════
        "BREATHING_DIFFICULTY" to FirstAidInstruction(
            type = "BREATHING_DIFFICULTY",
            title = "Atemnot",
            urgency = UrgencyLevel.CRITICAL,
            symptoms = listOf(
                "Schnelle, flache Atmung",
                "Ringen nach Luft",
                "Geräuschvolle Atmung",
                "Blaue/graue Schleimhäute",
                "Panik, Unruhe",
                "Atmung > 30/Min in Ruhe"
            ),
            immediateActions = listOf(
                "1️⃣ FRISCHE LUFT",
                "• Fenster auf",
                "• Raus ins Freie",
                "• Nichts einengen",
                "",
                "2️⃣ HALSBAND/GESCHIRR LOCKERN",
                "",
                "3️⃣ RUHIG HALTEN",
                "• Panik überträgt sich!",
                "• Beruhigend sprechen",
                "",
                "4️⃣ AUFRECHTE POSITION",
                "• Brust frei",
                "• Nicht hinlegen zwingen",
                "",
                "5️⃣ SOFORT TIERARZT ANRUFEN"
            ),
            doList = listOf(
                "✅ Frische Luft",
                "✅ Halsband ab",
                "✅ Aufrecht lagern",
                "✅ Ruhig bleiben",
                "✅ SOFORT Tierarzt"
            ),
            dontList = listOf(
                "❌ NICHT hinlegen zwingen",
                "❌ KEIN Maulkorb",
                "❌ NICHT einengen",
                "❌ KEINE Panik"
            ),
            transportInfo = "Aufrecht/sitzend transportieren. Brust frei. " +
                    "Fenster offen.",
            specialNotes = "🚨 Blaue Schleimhäute = AKUTER SAUERSTOFFMANGEL! " +
                    "Lebensbedrohlich!"
        ),

        // ════════════════════════════════════════════════════════
        // ERBRECHEN / DURCHFALL
        // ════════════════════════════════════════════════════════
        "VOMITING" to FirstAidInstruction(
            type = "VOMITING",
            title = "Erbrechen / Durchfall",
            urgency = UrgencyLevel.URGENT,
            symptoms = listOf(
                "Häufiges Erbrechen",
                "Wässriger/blutiger Durchfall",
                "Apathie",
                "Bauchschmerzen",
                "Dehydration",
                "Fieber"
            ),
            immediateActions = listOf(
                "1️⃣ FUTTER-PAUSE (12-24h)",
                "• Kein Futter",
                "• Wasser in kleinen Mengen",
                "",
                "2️⃣ DEHYDRATION PRÜFEN",
                "• Hautfalte ziehen",
                "• Bleibt stehen → dehydriert!",
                "",
                "3️⃣ PROBE SICHERN",
                "• Erbrochenes/Kot in Tüte",
                "• Zum Tierarzt mitnehmen",
                "",
                "4️⃣ TIERARZT WENN:",
                "• Blut im Erbrochenen/Kot",
                "• > 6x in 24h",
                "• Welpe/Senior",
                "• Starke Apathie"
            ),
            doList = listOf(
                "✅ Futter-Pause 12-24h",
                "✅ Kleine Wassermengen",
                "✅ Probe sichern",
                "✅ Dehydration prüfen",
                "✅ Temperatur messen"
            ),
            dontList = listOf(
                "❌ KEIN Futter",
                "❌ KEINE großen Wassermengen",
                "❌ KEINE Medikamente ohne Tierarzt",
                "❌ NICHT abwarten bei Blut"
            ),
            transportInfo = "Probe mitnehmen. Temperatur notieren. " +
                    "Zeitpunkt/Häufigkeit dokumentieren.",
            specialNotes = "⚠️ SOFORT Tierarzt bei: Blut, Welpen, > 6x/24h, Fieber > 39,5°C"
        ),

        // ════════════════════════════════════════════════════════
        // ALLERGISCHE REAKTION
        // ════════════════════════════════════════════════════════
        "ALLERGIC_REACTION" to FirstAidInstruction(
            type = "ALLERGIC_REACTION",
            title = "Allergische Reaktion",
            urgency = UrgencyLevel.URGENT,
            symptoms = listOf(
                "Schwellung (Gesicht, Schnauze)",
                "Nesselsucht, Quaddeln",
                "Juckreiz, Kratzen",
                "Rötung der Haut",
                "Atemnot (NOTFALL!)",
                "Erbrechen, Durchfall"
            ),
            immediateActions = listOf(
                "1️⃣ AUSLÖSER ENTFERNEN",
                "• Halsband/Geschirr ab",
                "• Fell abwaschen (Kontaktaller.)",
                "",
                "2️⃣ KÜHLEN",
                "• Kalte Kompressen",
                "• Auf Schwellungen",
                "",
                "3️⃣ BEI ATEMNOT → NOTFALL!",
                "• Sofort Tierklinik",
                "• Anaphylaktischer Schock!",
                "",
                "4️⃣ TIERARZT",
                "• Antihistaminikum",
                "• Ggf. Kortison"
            ),
            doList = listOf(
                "✅ Auslöser entfernen",
                "✅ Kühlen",
                "✅ Beobachten",
                "✅ Bei Atemnot → NOTFALL",
                "✅ Foto der Reaktion"
            ),
            dontList = listOf(
                "❌ KEINE Medikamente ohne Tierarzt",
                "❌ NICHT kratzen lassen",
                "❌ KEIN Cortison ohne Rücksprache",
                "❌ NICHT abwarten bei Atemnot"
            ),
            transportInfo = "Auslöser dokumentieren (Foto). Zeitverlauf notieren.",
            specialNotes = "🚨 Atemnot/Schwellung am Hals = ANAPHYLAXIE = NOTFALL!"
        ),

        // ════════════════════════════════════════════════════════
        // VERKEHRSUNFALL
        // ════════════════════════════════════════════════════════
        "TRAFFIC_ACCIDENT" to FirstAidInstruction(
            type = "TRAFFIC_ACCIDENT",
            title = "Verkehrsunfall",
            urgency = UrgencyLevel.CRITICAL,
            symptoms = listOf(
                "Sichtbare Verletzungen",
                "Schock",
                "Blutungen",
                "Lahmheit",
                "Bewusstlosigkeit",
                "Atemnot"
            ),
            immediateActions = listOf(
                "1️⃣ EIGENSCHUTZ!",
                "• Warndreieck aufstellen",
                "• Warnweste anziehen",
                "",
                "2️⃣ HUND SICHERN",
                "• Aus Gefahrenzone",
                "• Provisorischer Maulkorb!",
                "• Seitlich nähern",
                "",
                "3️⃣ VITALFUNKTIONEN PRÜFEN",
                "• Atmung, Puls, Bewusstsein",
                "• Sichtbare Verletzungen",
                "",
                "4️⃣ ERSTE HILFE",
                "• Blutung stillen",
                "• Schock behandeln",
                "• Nicht bewegen (Wirbelsäule!)",
                "",
                "5️⃣ NOTFALL-TRANSPORT"
            ),
            doList = listOf(
                "✅ EIGENSCHUTZ zuerst!",
                "✅ Provisorischer Maulkorb",
                "✅ Vitalfunktionen checken",
                "✅ Blutung stillen",
                "✅ 112 + Tierarzt anrufen"
            ),
            dontList = listOf(
                "❌ NICHT ohne Eigenschutz",
                "❌ NICHT frontal nähern",
                "❌ NICHT bewegen (außer Gefahr)",
                "❌ NICHT allein transportieren"
            ),
            transportInfo = "Bei Wirbelsäulenverdacht: feste Unterlage, keine Rotation. " +
                    "Zu zweit tragen.",
            specialNotes = "⚠️ Auch sanftester Hund beißt aus Schmerz! IMMER Maulschutz!"
        ),

        // ════════════════════════════════════════════════════════
        // VERÄTZUNG (SÄUREN/LAUGEN)
        // ════════════════════════════════════════════════════════
        "CHEMICAL_BURN" to FirstAidInstruction(
            type = "CHEMICAL_BURN",
            title = "Verätzung (Säure/Lauge)",
            urgency = UrgencyLevel.CRITICAL,
            symptoms = listOf(
                "Starke Schmerzen",
                "Speichelfluss",
                "Kratzen im Gesicht/Maul",
                "Gerötete/weiße Schleimhäute",
                "Atemnot",
                "Erbrechen"
            ),
            immediateActions = listOf(
                "1️⃣ EIGENSCHUTZ!",
                "• Handschuhe anziehen",
                "• Schutzbrille",
                "",
                "2️⃣ 15-20 MIN SPÜLEN",
                "• Lauwarmes Wasser",
                "• Kontaminierte Bereiche",
                "• Von Wunde wegspülen",
                "",
                "3️⃣ NICHT NEUTRALISIEREN!",
                "• Keine Säure + Natron",
                "• Keine Lauge + Essig",
                "• = Hitzeentwicklung!",
                "",
                "4️⃣ VERPACKUNG MITNEHMEN",
                "",
                "5️⃣ SOFORT TIERKLINIK"
            ),
            doList = listOf(
                "✅ EIGENSCHUTZ (Handschuhe!)",
                "✅ 15-20 Min spülen",
                "✅ Verpackung mitnehmen",
                "✅ SOFORT Klinik",
                "✅ Tiergiftnotruf anrufen"
            ),
            dontList = listOf(
                "❌ NICHT neutralisieren!",
                "❌ KEIN Erbrechen auslösen",
                "❌ KEIN Öl/Milch",
                "❌ NICHT ins Fell sprühen"
            ),
            transportInfo = "Weiter spülen während Transport. Verpackung mitnehmen. " +
                    "Kontaminiertes Fell abwaschen.",
            specialNotes = "☎️ Tiergiftnotruf: Berlin 030 19240, München 089 19240"
        ),

        // ════════════════════════════════════════════════════════
        // BLUTUNGEN OHNE UNFALL
        // ════════════════════════════════════════════════════════
        "BLEEDING_NO_ACCIDENT" to FirstAidInstruction(
            type = "BLEEDING_NO_ACCIDENT",
            title = "Blutungen ohne Unfall",
            urgency = UrgencyLevel.CRITICAL,
            symptoms = listOf(
                "Blut aus Nase/Maul/After",
                "Blut im Urin/Kot",
                "Blut im Erbrochenen",
                "Blutergüsse ohne Ursache",
                "Schockzeichen"
            ),
            immediateActions = listOf(
                "1️⃣ RUHE & BEOBACHTUNG",
                "• Keine Bewegung",
                "• Nicht aufregen",
                "",
                "2️⃣ BLUTUNGSQUELLE LOKALISIEREN",
                "• Nase? Maul? After?",
                "• Menge schätzen",
                "",
                "3️⃣ PROBE SICHERN",
                "• In Tüte/Behälter",
                "• Zum Tierarzt mitnehmen",
                "",
                "4️⃣ BEI SCHOCK:",
                "• Hinterbeine erhöhen",
                "• Warm halten",
                "",
                "5️⃣ SOFORT TIERARZT"
            ),
            doList = listOf(
                "✅ Ruhig halten",
                "✅ Probe sichern",
                "✅ Menge/Farbe notieren",
                "✅ Bei Schock: erhöhte Lagerung",
                "✅ SOFORT Tierarzt"
            ),
            dontList = listOf(
                "❌ NICHT bewegen lassen",
                "❌ KEIN Wasser/Futter",
                "❌ NICHT abwarten",
                "❌ NICHT selbst behandeln"
            ),
            transportInfo = "Vorsichtig. Probe mitnehmen. Zeitverlauf dokumentieren.",
            specialNotes = "🚨 Innere Blutung möglich! Schock ohne sichtbare Verletzung = NOTFALL!"
        ),

        // ════════════════════════════════════════════════════════
        // VERGIFTUNG (UNBEKANNT)
        // ════════════════════════════════════════════════════════
        "POISONING_UNKNOWN" to FirstAidInstruction(
            type = "POISONING_UNKNOWN",
            title = "Vergiftung (unbekannt)",
            urgency = UrgencyLevel.CRITICAL,
            symptoms = listOf(
                "Plötzliches Erbrechen, Durchfall",
                "Zittern, Krämpfe",
                "Starker Speichelfluss",
                "Schläfrigkeit, Apathie",
                "Atemnot, unregelmäßiger Herzschlag",
                "Blasse oder blaue Schleimhäute",
                "Koordinationsstörungen, Taumeln",
                "Veränderte Pupillen"
            ),
            immediateActions = listOf(
                "1️⃣ TIERGIFTNOTRUF SOFORT ANRUFEN",
                "📞 Berlin: 030 19240",
                "📞 München: 089 19240",
                "📞 Freiburg: 0761 19240",
                "",
                "2️⃣ SYMPTOME GENAU BEOBACHTEN",
                "• Zeitpunkt notieren (wann begonnen?)",
                "• Alle Symptome aufschreiben",
                "• Bewusstsein, Atmung, Puls prüfen",
                "",
                "3️⃣ UMGEBUNG UNTERSUCHEN",
                "• Was könnte gefressen worden sein?",
                "• Pflanzen in der Nähe? (Foto machen!)",
                "• Erbrochenes sichern (Tüte/Behälter)",
                "• Verdächtige Substanzen mitnehmen",
                "",
                "4️⃣ KEIN ERBRECHEN OHNE TIERGIFTNOTRUF!",
                "",
                "5️⃣ SOFORT ZUM TIERARZT",
                "• Probe/Erbrochenes mitnehmen",
                "• Zeitverlauf dokumentieren"
            ),
            doList = listOf(
                "✅ Sofort Tiergiftnotruf anrufen",
                "✅ Zeitpunkt & Symptome notieren",
                "✅ Erbrochenes sichern",
                "✅ Umgebung fotografieren",
                "✅ Verdächtige Substanzen mitnehmen",
                "✅ Ruhig halten, beobachten",
                "✅ Bewusstsein/Atmung/Puls prüfen"
            ),
            dontList = listOf(
                "❌ KEIN Erbrechen ohne Rücksprache!",
                "❌ KEINE Milch geben",
                "❌ KEIN Öl geben",
                "❌ KEINE Kohle ohne Anweisung",
                "❌ NICHT abwarten",
                "❌ NICHT selbst behandeln"
            ),
            transportInfo = "Erbrochenes/Probe in Tüte mitnehmen. Zeitverlauf dokumentieren. " +
                    "Bei Bewusstlosigkeit: Stabile Seitenlage.",
            specialNotes = "⚠️ TIERGIFTNOTRUF:\n" +
                    "Berlin: 030 19240\n" +
                    "München: 089 19240\n" +
                    "Freiburg: 0761 19240\n\n" +
                    "🚨 HÄUFIGE GIFTE:\n" +
                    "• Schokolade/Kakao (Theobromin)\n" +
                    "• Weintrauben/Rosinen (Nierenversagen)\n" +
                    "• Zwiebeln/Knoblauch (Blutarmut)\n" +
                    "• Xylitol/Birkenzucker (Unterzucker)\n" +
                    "• Schneckenkorn (Krämpfe)\n" +
                    "• Rattengift (innere Blutungen)\n" +
                    "• Giftpflanzen (Oleander, Eibe, Buchsbaum)\n" +
                    "• Frostschutzmittel (süßlich! = verlockend)"
        )
    )

    /**
     * Symptom-Check Fragen pro Typ
     */
    fun getSymptomChecks(type: String): List<SymptomCheckQuestion> = when (type) {
        "UNCONSCIOUSNESS" -> listOf(
            SymptomCheckQuestion("Reagiert der Hund auf Ansprache?", criticalIfNo = true),
            SymptomCheckQuestion("Reagiert der Hund auf Berührung?", criticalIfNo = true),
            SymptomCheckQuestion("Ist Atmung sichtbar?", criticalIfNo = true),
            SymptomCheckQuestion("Ist Puls fühlbar?", criticalIfNo = true)
        )

        "HEAT_STROKE" -> listOf(
            SymptomCheckQuestion("Körpertemperatur über 40°C?", criticalIfYes = true),
            SymptomCheckQuestion("Starkes, lautes Hecheln?", criticalIfYes = true),
            SymptomCheckQuestion("Ziegelrote Schleimhäute?", criticalIfYes = true),
            SymptomCheckQuestion("Taumeln oder Koordinationsstörungen?", criticalIfYes = true)
        )

        "POISONING_KNOWN" -> listOf(
            SymptomCheckQuestion("Substanz bekannt?", criticalIfNo = false),
            SymptomCheckQuestion("Erbrechen oder Durchfall?", criticalIfYes = false),
            SymptomCheckQuestion("Krämpfe oder Zittern?", criticalIfYes = true),
            SymptomCheckQuestion("Atemnot?", criticalIfYes = true)
        )

        "GASTRIC_TORSION" -> listOf(
            SymptomCheckQuestion("Aufgeblähter, harter Bauch?", criticalIfYes = true),
            SymptomCheckQuestion("Erfolgloses Würgen ohne Erbrechen?", criticalIfYes = true),
            SymptomCheckQuestion("Starke Unruhe?", criticalIfYes = true),
            SymptomCheckQuestion("Schnelle, flache Atmung?", criticalIfYes = true)
        )

        "SEIZURE" -> listOf(
            SymptomCheckQuestion("Dauer länger als 5 Minuten?", criticalIfYes = true),
            SymptomCheckQuestion("Mehrere Anfälle hintereinander?", criticalIfYes = true),
            SymptomCheckQuestion("Erster Anfall überhaupt?", criticalIfYes = false)
        )

        "BLEEDING" -> listOf(
            SymptomCheckQuestion("Hellrotes, pulsierendes Blut?", criticalIfYes = true),
            SymptomCheckQuestion("Große Wundfläche?", criticalIfYes = true),
            SymptomCheckQuestion("Blasse Schleimhäute?", criticalIfYes = true),
            SymptomCheckQuestion("Schwäche oder Kollaps?", criticalIfYes = true)
        )

        "POISONING_UNKNOWN" -> listOf(
            SymptomCheckQuestion("Plötzliches Erbrechen oder Durchfall?", criticalIfYes = false),
            SymptomCheckQuestion("Krämpfe oder Zittern?", criticalIfYes = true),
            SymptomCheckQuestion("Atemnot oder unregelmäßiger Herzschlag?", criticalIfYes = true),
            SymptomCheckQuestion("Blasse oder blaue Schleimhäute?", criticalIfYes = true),
            SymptomCheckQuestion("Koordinationsstörungen, Taumeln?", criticalIfYes = true)
        )

        else -> emptyList()
    }
}
