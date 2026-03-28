# HundeWildnis App 🐕🌲

Eine native Android-App für [wildnislehrer.com](https://wildnislehrer.com) - Dein mobiler Begleiter für Hundeerziehung und Wildnispädagogik.

# 🐾 HundeWildnisApp

> Dein mobiler Begleiter für Hundeerziehung und Wildnispädagogik

[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-purple.svg)](https://kotlinlang.org)
[![Open Source](https://img.shields.io/badge/Open%20Source-Yes-brightgreen.svg)](https://github.com/tux4us/HundeWildnisAndroidApp)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![No Google Services](https://img.shields.io/badge/Google%20Services-None-red.svg)](https://www.github.com/tux4us/HundeWildnisAndroidApp)

---

## 📱 Über die App

**HundeWildnis App** eine Anwendung das dem Anspruch gerecht wird ein gutes Werkzeug und ein hilfreicher Begleiter im Alltag, beim täglichen Training und bei allen Unternehmungen mit dem Hund zu sein.

---

## ✨ Features

### 01 · Zentrale Funktionen

Die Startseite bietet direkten Zugriff auf die täglichen Werkzeuge und Management-Bereiche.

#### 🆘 Unterstützung
Direkte Hilfe- und Support-Optionen.

#### 🐾 Übung des Tages
Regelmäßig wechselnde Trainingsimpulse direkt aus der Praxis der Hundeschule.

#### 🧘 Tagesmeditation
Regelmäßig wechselnde Meditationsübungen für ein entspanntes Mensch-Hund-Team.

#### 🏃 Trainingstempo
Echtzeit-Überwachung der Gehgeschwindigkeit in verschiedenen Trainingsszenarien mit Warnfunktion, grafischer Auswertung, Speicherung und GPX/PDF-Export mit Teilen-Option.

#### 📓 Tagesnotizen
Digitales Trainingstagebuch mit Foto-Upload, optionalen Standortwetterdaten zur Notiz, automatischem GPS-Tagging, automatischer Sicherung und PDF-Export-, Teilen- und GPX-Exportfunktionen.

#### 🌤️ Wetter
Aktuelle Wetterdaten und Vorhersagen für den Standort. Abruf über die freie Open-Meteo API und das RainViewer-Radar – ohne Tracking durch Google oder andere kommerzielle Wetterdienste.

**Aktuelle Wetterdaten (Echtzeit)**
- **Temperatur:** Aktuelle Lufttemperatur in 2 m Höhe.
- **Gefühlte Temperatur:** Berechnet unter Berücksichtigung von Luftfeuchtigkeit und Windchill (spezielle Berechnung bei Kälte und Wind).
- **Wetterzustand:** Textuelle Beschreibung (z. B. „Leicht bewölkt", „Starker Regen") inklusive passendem Emoji-Icon.
- **Luftfeuchtigkeit:** Relative Feuchte in Prozent.
- **Luftdruck:** Aktueller Oberflächendruck in hPa.
- **Niederschlag:** Aktuelle Niederschlagsmenge in mm.
- **UV-Index:** Belastung durch ultraviolette Strahlung – wichtig für den Sonnenschutz von Mensch und Hund.

**Wind-Informationen (Detailliert)**
- **Windgeschwindigkeit** in km/h.
- **Windrichtung:** Himmelsrichtung inklusive Richtungs-Pfeil (z. B. „↗ SW").
- **Windböen:** Maximale Böengeschwindigkeit.
- **Beaufort-Skala:** Einstufung der Windstärke (Bft 0 bis 12).

**Astronomische Daten**
- **Sonnenaufgang:** Exakte Uhrzeit für den aktuellen Tag.
- **Sonnenuntergang:** Exakte Uhrzeit für den aktuellen Tag.

**Vorhersagen & Übersichten**
- **Stündliche 24h-Vorhersage:** Temperatur, gefühlte Temperatur, Regenwahrscheinlichkeit und Wetterzustand für jede der nächsten 24 Stunden.
- **Tages-Min/Max:** Die erwartete Tiefst- und Höchsttemperatur des Tages.
- **Wetterwarnung-Badge:** Ein farbiges Status-Badge (Grün/Gelb/Rot) mit Warnungen vor Gewitter, Schneefall, Nebel oder starkem Regen.

**Interaktives Regenradar (Nowcast)**
- **Historie:** Visualisierung der Regenwolken-Bewegung der letzten 6 Stunden.
- **Vorhersage:** Animierte Projektion für die kommenden 60 Minuten (Nowcast), um Regenschauer während des Spaziergangs präzise umgehen zu können.

> 📌 **Datenschutz-Hinweis:** Alle Wetterdaten werden über die freie Open-Meteo API (ohne API-Key-Zwang) und das RainViewer-Radar abgerufen. Es findet kein Tracking durch Google oder andere kommerzielle Wetterdienste statt.

#### 🗺️ Standort & POI
Interaktive Karte mit Suche nach wander- und hunderelevanten Orten via OpenStreetMap.

**Karten- & Standort-Grundfunktionen**
- **Interaktive OSM-Karte:** Nutzt das freie OpenStreetMap (Mapnik) Material für eine detaillierte Darstellung der Umgebung.
- **Standort-Anzeige:** Zeigt die aktuelle Position auf der Karte an.
- **Echtzeit-Tracking:** Die Karte folgt der eigenen Bewegung im Gelände (aktualisiert sich alle 5 Meter oder alle 2 Sekunden).
- **Standort-Zentrierung:** Über den Button „📍 Standort" lässt sich die Karte jederzeit wieder auf die aktuelle Position ausrichten.
- **Koordinaten-Anzeige:** Die exakten GPS-Daten (Breiten- und Längengrad) werden live eingeblendet.
- **Standort teilen:** Die aktuelle Position lässt sich direkt an weitere Personen versenden oder zu anderen Apps teilen.

**POI-Suche (Points of Interest)**

Kategorien-Grid für schnelle Auswahl hundespezifischer Orte:
- Hundeausläufe & Hundeparks
- Tierärzte & Tierkliniken
- Tierbedarf & Tierheime
- Wanderwege & Aussichtspunkte
- Trinkwasser & Toiletten
- Supermärkte, Bäckereien & Restaurants
- Notfall-Services (Apotheke, Polizei, Feuerwehr, Arztpraxen, Krankenhäuser)
- Naturschutzgebiete & Nationalparks
- SOS-Punkte und vieles mehr

- **Radar-Suche:** Sucht automatisch in einem Umkreis von 3 km nach allen Einträgen der gewählten Kategorie via Overpass-API.
- **Ergebnis-Vorschau:** Zeigt alle Fundorte gleichzeitig als Marker auf der Karte an und zoomt so, dass alle Ziele plus die eigene Position sichtbar sind.

**Intelligente Navigation (Routing)**
- **Wegbeschreibung:** Ein Klick auf einen Ort in der Liste oder einen Marker auf der Karte startet die Navigation.
- **Routenberechnung:** Die App nutzt professionelle Routing-Dienste (OpenRouteService, Graphhopper oder OSRM), um den besten Weg für Fußgänger zu berechnen.
- **Statistiken:** Zeigt die exakte Entfernung und die geschätzte Gehzeit zum Ziel an.
- **Live-Update:** Während des Laufens wird die Route ständig an die aktuelle Position angepasst.
- **Luftlinie-Fallback:** Sollte kein Fußweg in der Karte bekannt sein, wird automatisch eine gestrichelte Luftlinie zum Ziel angezeigt.

**POI-Details**
- **Umfassende Infos:** Sofern in OSM hinterlegt, zeigt die App Details wie Adresse, Telefonnummer, Webseite, Öffnungszeiten und Zutrittsbeschränkungen (z. B. Leinenpflicht) an.
- **Direkt-Aktionen:** Telefonnummern und Web-Links sind klickbar, um sofort Kontakt aufzunehmen.
- **Lokal & Frei:** Keine Nutzung von Google Maps. Der Standort wird nur für die aktuelle Suche anonym an freie Karten-Server gesendet.
- **Zwei-Klick-Logik:** Erster Klick auf Marker zeigt Info, zweiter Klick startet die Navigation – für eine fehlerfreie Bedienung.

#### 🧭 Kompass
Digitale Orientierungshilfe für Wanderungen und Gelände.

#### ⛰️ Höhenmesser
Anzeige der aktuellen Höhe über Meeresspiegel via GPS.

#### 🐕 Hunde-Stammdaten
Verwaltung von bis zu 3 Hunde-Profilen. Die Stammdaten werden über Infokarten dargestellt und können angezeigt, bearbeitet und geteilt werden.

**Basis-Informationen**
- **Name:** Rufname des Hundes.
- **Rasse:** Rassebezeichnung oder Mischling.
- **Geschlecht:** Rüde oder Hündin.
- **Kastrationsstatus:** Ja, Nein oder Unbekannt.
- **Geburtsdatum:** Tag der Geburt.
- **Farbe:** Fellfarbe und besondere Kennzeichen.
- **Foto:** Ein individuelles Profilbild des Hundes.

**Identifikation (Wichtig bei Verlust)**
- **Chipnummer:** Die 15-stellige Transponder-Nummer.
- **Tasso-Nummer:** Kennnummer der Registrierung bei Tasso e.V.
- **Steuernummer:** Die amtliche Hundesteuer-Nummer.

**Gesundheit & Vorsorge**
- **Impfstatus:** Aktuelle Informationen zu den Schutzimpfungen.
- **Letzte Entwurmung:** Datum der letzten Behandlung.
- **Letzter Floh-/Zeckenschutz:** Datum der letzten Prophylaxe.
- **Gewicht:** Aktuelles Körpergewicht inklusive Datum der Messung.
- **Medizinische Hinweise:** Platz für chronische Krankheiten, Allergien oder notwendige Medikamente.

**Kontakte & Versicherung**
- **Tierarzt:** Name und Kontaktdaten des Haustierarztes.
- **Versicherung:** Angaben zur Hundehaftpflicht- oder Krankenversicherung.
- **Besitzer:** Name und Adresse des Eigentümers.
- **Notfallkontakt:** Telefonnummer für dringende Rückfragen.

**Haltung & Verhalten**
- **Futter:** Spezielle Ernährungsanforderungen oder Futtersorten.
- **Verhalten:** Besonderheiten im Umgang (z. B. „ängstlich bei Gewitter" oder „verträglich mit Artgenossen").

**Export & Transfer**
- **PDF mit QR-Code:** Es lässt sich eine komplette PDF inklusive QR-Code erzeugen.
- **QR-Code-Transfer:** Die App generiert aus den Stammdaten einen QR-Code. Ein Scan (z. B. durch einen Tierarzt) überträgt die Stammdaten sofort auf das Zielgerät.
- **Datenschutz:** Alle Daten werden ausschließlich lokal auf dem Smartphone gespeichert und verlassen das Gerät nur, wenn der Nutzer aktiv den Export (Teilen, PDF, QR-Code) nutzt.

#### 💾 Datensicherung (Backup)
Komplette Sicherung und Wiederherstellung aller angelegten App-Daten lokal auf dem Gerät. Das Backup kann auf Wunsch auch geteilt werden, um es auf weiteren Geräten bzw. nach Neuinstallation in die App zu importieren.

---

### 02 · Untere Navigationsleiste

| Bereich | Beschreibung |
|---------|-------------|
| 📰 **Blog** | Aktuelle Artikel, Tipps und Neuigkeiten der HundeWildnisSchule. |
| 📄 **Seiten** | Direkter Zugriff auf Webseiten der HundeWildnisSchule. |
| 🎓 **Kurse** | Verknüpfung zu über 40 professionellen Online-Trainingskursen (nach individueller Freischaltung über die Webseite). |
| ▶ **YouTube** | Direkter Link zu unserem YouTube-Kanal. |
| 🎙 **Podcast** | Zugriff auf Audiobeiträge der HundeWildnisSchule. |
| ✕ **X (Twitter)** | Direkter Link zu unserem X-Kanal. |
| 💬 **Signal-Kontakt** | Direkter Draht zu den Trainern Manuela und Jörg. |

---

### 03 · System- & Hilfemenü

Globale Aktionen, verfügbar über das 3-Punkte-Menü in der oberen Toolbar.

| | Funktion | Beschreibung |
|--|---------|-------------|
| 🔁 | Aktualisieren | Manuelles Laden neuer Blog-Inhalte. |
| 🌞🌒 | Theme wechseln | Umschalten zwischen hellem Design und augenschonendem Dark Mode. |
| ⚙️ | Einstellungen | Verwaltung von Hintergrund-Updates und Benachrichtigungen. |
| 📦 | Backup erstellen | Schneller Zugriff auf die Datensicherung. |
| 📥 | Backup importieren | Einspielen einer bestehenden Sicherungsdatei. |
| 📖 | Anleitung | Detaillierte Bedienungshilfe für alle App-Bereiche. |
| 🛡️ | Berechtigungen | Erläuterung der genutzten System-Berechtigungen (GPS, Kamera etc.). |
| 📜 | Datenschutz | Einsehen der aktuellen Datenschutzerklärung. |
| 📎 | Impressum | Rechtliche Informationen zum Anbieter. |
| ℹ️ | Über die App | Versionsinfo, Anbieterdaten und direkter Kontakt für Feedback. |
| 🏡 | Home | Schneller Rücksprung zur Hauptübersicht. |

---

### 04 · Besondere technische Merkmale

| Merkmal | Beschreibung |
|---------|-------------|
| 🔒 **Privacy-First** | Keine Cloud-Synchronisation – alle Daten (GPS, Fotos, Notizen) bleiben lokal auf dem Gerät. |
| 🚫 **Google-Free** | Vollständiger Verzicht auf proprietäre Google-Services. F-Droid-kompatibel. |
| 📤 **Export-Stark** | Umfassende PDF-Protokollierung, Export-Optionen und GPX-Unterstützung für Tracks und Wegpunkte. |
| 🛡️ **Screenshot-Schutz** | Aktive Sicherung sensibler Daten vor unbefugten Bildschirmfotos. |

---

## 🛠️ Technologie

Modern, sicher und performant - die App setzt auf bewährte Android-Technologien:

### Tech Stack

```
Kotlin 100%
Android 7.0 - 15
AndroidX
MVVM + Coroutines
ViewBinding
Jsoup HTML Parser
OSMDroid Maps
Open-Meteo API
```

### Besonderheiten

✅ **Keine Google Play Services** • ✅ **Kein Tracking** • ✅ **Open Source**

### Dependencies

- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 36 (Android 15)
- **Build-System:** Gradle (Kotlin DSL)
- **UI:** Android Views mit ViewBinding
- **Architektur:** MVVM mit Kotlin Coroutines
- **HTML-Parsing:** Jsoup 1.17.2
- **Maps:** OSMDroid
- **Wetter:** Open-Meteo API

---

## 📥 Installation

### Voraussetzungen

- Android 7.0 (API 24) oder höher
- ~20 MB Speicherplatz
- Internetverbindung für Inhalte
- GPS für Standortfunktionen (optional)

---

## 🤝 Beitragen

Contributions sind willkommen! Bitte erstelle einen Pull Request oder öffne ein Issue.

---

## 📄 Lizenz

Dieses Projekt ist unter der MIT-Lizenz lizenziert - siehe [LICENSE](LICENSE) Datei für Details.

---

## 👥 Entwickler

**Die HundeWildnisApp ist eine 100%ige Eigenentwicklung von:**

**HundeWildnisSchule Manuela Ulbricht und Peter Jörg Ulbricht GbR**

---

## 🔗 Links

- 🌐 [wildnislehrer.com](https://wildnislehrer.com)
- 🐙 [GitHub Repository](https://github.com/tux4us/HundeWildnisAndroidApp)

---

## 📊 Status

- **Status:** Aktiv entwickelt ✅

---

## 🙏 Danksagungen

- Kartendaten © [OpenStreetMap Contributors](https://www.openstreetmap.org/copyright)
- Wetterdaten © [Open-Meteo](https://open-meteo.com/) (CC BY 4.0)

---

## 📞 Support

Bei Fragen oder Problemen:

- 📧 Kontakt über [wildnislehrer.com](https://wildnislehrer.com)
- 💬 SIGNAL-Chat (verfügbar in der App)
- 🐛 [GitHub Issues](https://github.com/tux4us/HundeWildnisAndroidApp/issues)

---

<div align="center">

**Entwickelt mit 💚 für Hunde und ihre Menschen**

[![GitHub](https://img.shields.io/badge/GitHub-Repository-black.svg)](https://github.com/tux4us/HundeWildnisAndroidApp)

</div>
