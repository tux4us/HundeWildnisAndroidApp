<div align="center">

# 🐕 HundeWildnis App

**Mobiler Begleiter für professionelles Hundetraining, Wildnispädagogik und den Alltag mit dem Hund**

[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Version](https://img.shields.io/badge/Version-17.0%20Build%2092-C9A84C?style=flat-square)](#)
[![License](https://img.shields.io/badge/Lizenz-MIT-5C8A3C?style=flat-square)](LICENSE)
[![No Google Services](https://img.shields.io/badge/Google%20Services-keine-brightgreen?style=flat-square)](#datenschutz)
[![F-Droid](https://img.shields.io/badge/F--Droid-in%20Vorbereitung-lightgrey?style=flat-square)](https://f-droid.org/)

> 🔒 **100 % Datenschutz** · Kein Tracking · Kein Datenabfluss · Keine Werbung · Keine Google Services · DSGVO-konform

**Eine 100 %ige Eigenentwicklung der HundeWildnisSchule Manuela Ulbricht und Peter Jörg Ulbricht GbR, Freital**

[📥 Download](#-download) · [🌐 Website](https://wildnislehrer.com/hundewildnisapp/) · [📖 Alle Module](#-module)

</div>

---

## 📑 Inhaltsverzeichnis

- [Über die App](#-über-die-app)
- [Datenschutz & Sicherheit](#-datenschutz--sicherheit)
- [Module](#-module)
  - [1 · Hunde-Stammdaten](#1--hunde-stammdaten)
  - [2 · Vitalwerte & Pulscheck](#2--vitalwerte--pulscheck)
  - [3 · Läufigkeitstracker](#3--läufigkeitstracker)
  - [4 · Trainingstagebuch](#4--trainingstagebuch)
  - [5 · Nasenarbeit – Expertensystem](#5--nasenarbeit--expertensystem)
  - [6 · Sprach-Trainer](#6--sprach-trainer)
  - [7 · Tempo-Trainer](#7--tempo-trainer)
  - [8 · Zeckenlupe](#8--zeckenlupe)
  - [9 · Notfall-Assistent](#9--notfall-assistent)
  - [10 · Hundewetter](#10--hundewetter)
  - [11 · Revier-Navigation](#11--revier-navigation)
  - [12 · Standort teilen](#12--standort-teilen)
  - [13 · Kompass](#13--kompass)
  - [14 · Höhenmesser](#14--höhenmesser)
  - [15 · Tagesnotizen](#15--tagesnotizen)
  - [16 · Backup & WebDAV-Sync](#16--backup--webdav-synchronisation)
  - [17 · Blog, Kurse & Community](#17--blog-kurse--community)
- [Technologie-Stack](#️-technologie-stack)
- [Berechtigungen](#-berechtigungen)
- [Installation](#-installation)
- [Download](#-download)
- [Roadmap](#-roadmap)
- [Mitmachen](#-mitmachen)
- [Lizenz](#-lizenz)

---

## 📱 Über die App

Die HundeWildnis App ist ein mobiler Begleiter für den Alltag mit dem Hund – vom täglichen Training über Nasenarbeit, Vitalwertmessung und Erste Hilfe bis zu GPS-Navigation und Wetteranalyse. Alle 17 Module teilen eine gemeinsame Datenbasis: die Hunde-Profile.

**Kernprinzipien:**
- Alle Nutzerdaten bleiben lokal auf dem Gerät – keine Cloud-Pflicht
- Keine proprietären Google-Bibliotheken, keine Google Services
- WebDAV-Synchronisation für geräteübergreifende Datensicherung

---

## 🔐 Datenschutz & Sicherheit

| Merkmal | Details |
|---|---|
| Datenhaltung | 100 % lokal, keine Cloud-Pflicht |
| Tracking | Keines |
| Werbung | Keine |
| Analytics | Keine |
| Google Services | Nicht verwendet |
| Screenshot-Schutz | `FLAG_SECURE` in `SecureBaseActivity` auf allen sensitiven Screens |
| API-Verbindungen | Ausschließlich HTTPS/TLS |
| Zugangsdaten | AES256-GCM-verschlüsselt via `EncryptedSharedPreferences` |
| Datenweitergabe | Nur auf aktive Nutzer-Aktion (PDF, GPX, Share) |
| Compliance | DSGVO-konform, F-Droid-kompatibel, MIT-Lizenz |

---

## 🧩 Module

---

### 1 · 🐕 Hunde-Stammdaten

Beliebig viele Hunde-Profile – kein Limit. Vollständige digitale Akte, gemeinsame Datenbasis für alle anderen Module.

**Datenfelder:**

| Bereich | Felder |
|---|---|
| Basis | Name · Rasse · Geschlecht · Kastrationsstatus · Geburtsdatum · Herkunft · Fellfarbe · Profilfoto |
| Identifikation | Chipnummer (15-stellig) · Steuernummer · TASSO-Nr. · Heimtierausweis-Land · Ausweisnummer |
| Gesundheit | Impfstatus · Entwurmung · Floh-/Zeckenschutz · Gewicht mit Datum · Schulterhöhe · Körperlänge · Med. Hinweise |
| Kontakte | Tierarzt · Haftpflichtversicherung · OP-Versicherung · Besitzer · Notfallkontakt |
| Sonstiges | Futter · Verhaltensbesonderheiten |

**Wesensmerkmale (6 Skalen, 1–5):** Grundtemperament · Selbstbewusstsein · Sozialverhalten · Erregbarkeit · Lerngeschwindigkeit · Frustrationstoleranz

**11 Quick-Profile:** `Ausgeglichen` `Energiegeladen` `Sehr ruhig` `Unsicher` `Sehr selbstbewusst` `Welpe (4–8 Monate)` `Senior (8+ Jahre)` `Arbeitshund` `Sehr sozial` `Eigenständig` `Tierschutzhund`

**Export:** PDF mit QR-Code · QR-Code einzelner Felder · Textliste per Share-Sheet

---

### 2 · ❤️ Vitalwerte & Pulscheck

Dokumentation und Verlauf der Vitalparameter pro Hund. Direktübergabe an den Notfall-Assistenten möglich.

**Pulscheck-Assistent:**
- 15-Sekunden-Timer für Puls (× 4 = /min) und Atemfrequenz
- Automatische Bewertung Normal / Zu niedrig / Zu hoch nach Schulterhöhe:
  - Kleinhund < 30 cm: 100–160 /min
  - Mittelhund 30–50 cm: 70–120 /min
  - Großhund > 50 cm: 60–100 /min
- Kapillarfüllzeit (KFZ): 1s · 2s · >3s
- Schleimhautfarbe: Rosa (normal) · Blass rosa · Weiß/Sehr blass · Blau/Grau (Zyanose) · Gelb (Ikterus) · Ziegelrot
- Bewusstsein: Alert · Gedämpft · Somnolent · Bewusstlos
- 8 Belastungsszenarien: Ruhe bis Erkrankung

**Verlauf:** Chronologische Liste aller Messungen · Farbige Puls-Bewertung · CSV-Export · Share-Sheet · Backup-Integration

---

### 3 · ♀️ Läufigkeitstracker

Nur sichtbar bei: Geschlecht = „Hündin" UND Kastrationsstatus ≠ „Ja".

- Zyklus-Nummer (automatisch), Start-/Enddatum, Dauer in Tagen (automatisch berechnet)
- Intensität: `Leicht` `Mittel` `Stark`
- Körperliche Symptome (Freitext)
- Verhaltensänderungen (10 Optionen + Freitext): Unruhe · Anhänglichkeit · Aggressivität · Markierverhalten · Fluchtversuche · Appetitverlust · Ablenkbarkeit · Urinieren · Reitverhalten · Interesse an Rüden
- Scheinträchtigkeit (9 Optionen + Freitext): Nestbau · Gesäuge · Milch · Spielzeug adoptieren · Mutterschutzverhalten · Appetitverlust · Unruhe · Gewichtszunahme · Rückzug
- Erinnerungen via WorkManager (`HeatCycleReminderWorker`)

---

### 4 · 🦮 Trainingstagebuch

Vollständiges Trainingsdokumentations- und Analysesystem für 18 Trainingskategorien, abgestimmt auf die HundeWildnis-Onlinekurse 13–31.

**18 Kategorien:** `Leinenarbeit` `Fuß` `Sitz` `Platz` `Steh` `Sozialisierungstechniken` `Langleinentraining` `Distanzunterbrechungen` `Freilauf` `Freifolge` `Teamwalk` `Scouttechniken` `Jagdvermeidung` `Nasenarbeit` `Leinenführigkeit` `Begegnungssituationen` `Stresstoleranztraining` `Straßenroutinen`

**Session-Parameter:**

| Parameter | Optionen |
|---|---|
| Messungstyp | Minuten (bis 120) oder Wiederholungen (bis 100) |
| Erfolgsbewertung | 🔴 Schwierigkeiten · 🟡 Teilweise · 🟢 Erfolgreich · 🟢🟢 Sehr erfolgreich |
| Ablenkungslevel | Keine · Gering · Mittel · Hoch |
| Schwierigkeitsgrad | Leicht · Normal · Schwer |
| Trainingsort | Zuhause · Garten · Park · Wald · Feld · Stadt · Halle · Hundeschule · Sonstiges |
| Wetterbedingung | Sonnig · Bewölkt · Regnerisch (+ automatischer Abruf) |
| Geodaten | GPS-Koordinaten und -Ortsname automatisch |

**Fortschrittsberechnung:** Session-Score (40 %) + Erfolgs-Score (40 %) + Aufwand-Score (20 %) = 0–100 %

**Trainingsstände:** Beginn (0–20 %) → Grundlagen (20–40 %) → Aufbau (40–60 %) → Festigung (60–80 %) → Etabliert (80–100 %)

**Meilensteine:** Erste Session · 5-Tage-Streak · 10-Tage-Streak · 50 % · 75 % · 100 % Fortschritt · 5× sehr erfolgreich · Perfekte Woche

**Export:** PDF-Protokoll und CSV (alle Parameter inkl. korrekter Minuten/Wiederholungen-Anzeige via `getEffortText()`)

---

### 5 · 🐾 Nasenarbeit – Expertensystem

Regelbasiertes Vier-Schichten-Scoring für drei Disziplinen mit disziplingetrenntem Fortschritt.

**Disziplinen:** Verlorensuche · Personensuche · Freisuche (Flächenangabe in Hektar)

**Vier-Schichten-Analyse:**

```
Schicht 1 – Wetter (Open-Meteo API)
  Temperatur: Ausschluss > 30 °C und < −10 °C (Hartausschluss)
  Windstärke: Beaufort 0–12, levelabhängige Eignungsbewertung pro Disziplin
  Windrichtung: von hinten / seitlich / von vorn relativ zur Suchlinie
  WMO-Code: Gewitter + Sturm/Orkan → Hartausschluss

Schicht 2 – Topografie (Open-Meteo Elevation API, SRTM 90m)
  Klassen: eben · leichte Steigung · steil · Kuppe · Hanglage · Mulde · wechselnd · Felsabbrüche

Schicht 3 – Bodentyp & Zustand (Overpass API/OSM)
  Typen: Grasland (kurz/lang/frisch) · Laub-/Nadel-/Mischwald · Acker · Asphalt · Fels · Erde · Mineral
  Zustände: trocken · feucht · nass · matschig · gefroren · Schnee (leicht/mittel/hoch)
  Eignungswerte pro Bodentyp-Zustand-Disziplin-Kombination: 0=geeignet · 1=bedingt · 2=ungeeignet

Schicht 4 – Trainingslevel (disziplingetrennt aus absolvierten Sessions)
```

**Level-Tabelle:**

| Level | Sessions | Verlorensuche | Personensuche | Freisuche | Schwierigkeit |
|---|---|---|---|---|---|
| Anfänger | 0–49 | bis 100 Schritte | bis 100 Schritte | bis 0,2 ha | Leicht |
| Fortgeschritten | 50–99 | bis 250 Schritte | bis 250 Schritte | bis 0,5 ha | Mittel |
| Erfahren | 100–499 | bis 500 Schritte | bis 1.000 Schritte | bis 2,0 ha | Mittel–Schwer |
| Experte | 500+ | bis 1.000 Schritte | bis 2.000 Schritte | bis 5,0 ha | Schwer |

**Karten-Integration:** Strecke als Polyline / Fläche als Polygon auf OSMDroid-Karte zeichnen · Snap-to-Way (ORS) · Distanz- und Flächenberechnung · PDF- und CSV-Export

> Alle automatisch ermittelten Werte können manuell überschrieben werden.

---

### 6 · 🗣️ Sprach-Trainer

Echtzeit-Stimmfrequenzanalyse via YIN-Algorithmus und `AudioRecord`. Feedback bei Treffer einer der 5 Zielfrequenzen (Toleranz ±5 Hz).

**5 Zielfrequenzen:**

| Hz | Name | Wirkung |
|---|---|---|
| 108 Hz | Tiefenentspannung | Ruhe & Erdung |
| 216 Hz | Beruhigung | Entspannung & Vertrauen |
| 324 Hz | Aufmerksamkeit | Fokus & Konzentration |
| 432 Hz | Harmonie | Balance & Verbindung |
| 648 Hz | Motivation | Aktivierung & Freude |

**Modi:** Ganzheitliches Training (alle 5 Zonen als Limit-Linien) · Fokus-Training (eine Zone)

**Feedback:** Shimmer-Glüheffekt · Vibration (konfigurierbar) · Ton (ToneGenerator) · Mikrofon-Fallback-Kette: `MIC` → `VOICE_RECOGNITION` → `VOICE_COMMUNICATION` → `DEFAULT`

**Statistiken:** Sprechzeit · Pausenzeit · Treffer-Anzahl · Verweildauer pro Frequenz (ms) · Durchschnittsfrequenz · PDF-Export (VoicePDFGenerator)

---

### 7 · ⏱️ Tempo-Trainer

GPS-Geschwindigkeitsüberwachung via `WalkingSpeedService` (ForegroundService). GPS aktiv auch bei ausgeschaltetem Bildschirm.

- **Limits:** 2,5 km/h · 3,0 km/h · 4,0 km/h (während der Messung wechselbar)
- **Messintervall:** 30 s oder 60 s
- **Warnfunktion:** Benachrichtigung + Systemton bei Limit-Überschreitung
- **Echtzeit-Chart:** MPAndroidChart mit Geschwindigkeitskurve und Limit-Linie
- **Export:** GPX (Tempoverteilung auf Strecke) · PDF-Protokoll mit Limitwechseln

---

### 8 · 🔍 Zeckenlupe

Digitale Lupe auf Basis der **Camera2 API** zur genauen Betrachtung von Zecken.

- **2 Filter:** 🌡️ Wärmebild (Thermal-Pseudofarben) · ◑ Hoher Kontrast (invertiert S/W)
- **Tap-to-Focus:** AF-Region 15 % der Sensorfläche, automatischer Reset nach 3 s
- **Freeze-Frame:** Bild einfrieren zur veiwacklungsfreien Betrachtung
- **Speichern:** Eingefrorenes Bild in Galerie (MediaStore, JPEG)
- Sensor-Orientation aus `CameraCharacteristics` gelesen, Korrektur via `Matrix.postRotate()`
- 4:3-Seitenverhältnis mit Center-Crop (keine Verzerrung)

---

### 9 · 🚨 Notfall-Assistent

Geführter Erste-Hilfe-Wizard mit HLW-Timer und sofortiger Notfall-PDF.

**Notfallkategorien:**

| Kategorie | Typen |
|---|---|
| ⚠️ Unfall | Verkehrsunfall · Blutungen/Wunden · Knochenbruch/Lahmheit · Augenverletzung · Ertrinken · Unterkühlung · Hitzschlag · Fremdkörper verschluckt · Vergiftung (bekannt) |
| 💉 Erkrankung | Bewusstlosigkeit/Kollaps · Krampfanfall · Magendrehung · Atemnot · Blutungen (ohne Unfall) · Erbrechen/Durchfall · Vergiftung (unbekannt) · Allergische Reaktion |

**HLW-Timer:**
```
30 Kompressionen  →  15 Sekunden (120 BPM)
 2 Beatmungen     →   5 Sekunden
Puls-Check-Erinnerung alle 2 Minuten (automatisch)
```

**Notfall-PDF:** Stammdaten · Symptombeschreibung · GPS (OSM-Link) · Fotos · Vitalwert-Übergabe · Ein-Tap-Teilen

---

### 10 · 🌦️ Hundewetter

Standortbasierter Wetterbericht via Open-Meteo API (CC BY 4.0) und RainViewer-Radar.

| Funktion | Details |
|---|---|
| Aktuelle Daten | Temperatur · gefühlte Temperatur (Windchill) · Luftfeuchtigkeit · Luftdruck · Niederschlag · UV-Index |
| Wind | km/h · Windrichtungspfeil · Böen · Beaufort 0–12 mit Symbol |
| Pfotenwetter-Index | Asphalt-Warnung · Kälte · Zecken · UV · Gewitter · Starkregen · Wind kleine Hunde |
| Tageslicht | Sonnenaufgang/-untergang · Dämmerung · Tageslänge · Goldene Stunde · Mondphase (offline, Canvas) |
| Diagramme | 24h-Temperatur · 24h-Niederschlag · Stündliche Vorhersage · 7-Tage-Übersicht |
| Wind-Card | Stündliche Windvorhersage (WindForecastAdapter) |
| Luftqualität | AQI-Card stündlich (AirQualityAdapter) |
| Pollen | Erle · Birke · Gräser · Beifuß · Olive · Ragweed – 2-Tage-Vorhersage mit hundesspezifischen Hinweisen |
| Regenradar | RainViewer Leaflet.js in WebView · 2h-Animation · Play/Pause · 500ms-Frame-Intervall |
| Wetter-Favoriten | Orte speichern und schnell auswählen (WeatherFavoritesManager · Backup-Integration) |

---

### 11 · 🗺️ Revier-Navigation

OpenStreetMap-Karte (OSMDroid) ohne Google Maps. 29 POI-Kategorien via Overpass API (3 km Radius).

**POI-Kategorien:** `Hundeauslauf` `Hundebadestelle` `Hundebeutel` `Tierarzt` `Tierbedarf` `Tierheim` `Wanderweg` `Gipfel/Kreuz` `Aussichtspunkt` `Wegweiser` `Schutzhütte` `Zeltplatz` `Picknickplatz` `Sitzbank` `Feuerstelle` `Trinkwasser` `Toilette` `Supermarkt` `Bäckerei` `Outdoor-Shop` `Restaurant` `Parkplatz` `Apotheke` `Arzt` `Polizei` `Feuerwehr` `Notfall` `Naturschutzgebiet` `Nationalpark`

**Routing-Kette:**

| Dienst | Rolle | Profil |
|---|---|---|
| OpenRouteService | Primär | foot-hiking oder cycling-road |
| Graphhopper | Fallback 1 | – |
| OSRM | Fallback 2 | – |
| Luftlinie | Letzter Fallback | – |

- Toggle Fußgänger 🥾 / Fahrrad 🚲; Fahrrad-Profil: max. 10 km/h für Hundebegleitung
- Rerouting-Flag verhindert Mehrfach-Anfragen bei > 30 m Abweichung
- Zwei-Klick-Logik: 1. Tap → Info · 2. Tap → Navigation
- Favoriten-System mit Drag-and-Drop-Sortierung
- ORS-API-Key nutzerseitig eingebbar und AES256-GCM-verschlüsselt gespeichert

---

### 12 · 📍 Standort teilen

Gegenseitige Echtzeit-Standortfreigabe über WebDAV.

- `LocationShareService` (ForegroundService): eigener Standort alle 60 s hochgeladen
- Fremd-Standorte alle 60 s abgerufen und auf OSMDroid-Karte dargestellt
- Stale-Marker: Standorte > 10 Minuten werden grau dargestellt
- Routing zum freigegebenen Standort direkt startbar
- Ausschließlich über eigenen WebDAV-Server – keine Drittanbieter

---

### 13 · 🧭 Kompass

`TYPE_ROTATION_VECTOR`-Sensor · Exponentielle Glättung (α = 0,2, 5er-Historien-Puffer) · Mindest-Änderungsschwelle 2° · Eigene `CompassRoseView` (Canvas) · Vollständig offline

---

### 14 · ⛰️ Höhenmesser

GPS-Höhe (`LocationManager`) + Barometer-Höhe (`TYPE_PRESSURE`-Sensor, falls vorhanden) · Offline

---

### 15 · 📝 Tagesnotizen

- Titel + freies Textfeld · Automatische Wetterdaten (Open-Meteo) · GPS-Koordinaten (automatisch)
- Fotos (Galerie via `PickMultipleVisualMedia` oder Kamera) · Export als PDF (iText7) und GPX
- Langzeit-Historie · Backup-Integration (inkl. Fotos)

---

### 16 · 📦 Backup & WebDAV-Synchronisation

**Backup-Version 6** · Dateiendung `.hundebackup` · Offenes ZIP-Format

**11 Datenbereiche (selektiv wählbar):**
`Hunde-Profile` `Trainingstagebuch` `Tempo-Messungen` `Tagesnotizen & Fotos` `Sprach-Sessions` `Notfall-Meldungen` `Nasenarbeit` `Karten-Favoriten` `Wetter-Favoriten` `Vitalwerte` `App-Einstellungen`

**3 Merge-Modi:**
- `Hinzufügen` – Neue Daten ergänzen, vorhandene behalten
- `Ersetzen` – Vollständiges Überschreiben
- `Zusammenführen` – Neueste Version je Eintrag via `lastModified`-Timestamp

**Deduplizierung:** Chipnummer (Primär) + UUID (Sekundär) · UUID-Erhaltung für referentielle Integrität

**WebDAV-Synchronisation:**
- Nextcloud · Hetzner Storage Box · Mailbox.org · ownCloud · oCloud · weitere selbst gehostete Server
- Zugangsdaten AES256-GCM-verschlüsselt (`EncryptedSharedPreferences`)
- Auto-Sync-Intervalle: 2h / 4h / 6h / 12h / 24h (WorkManager)
- Hub-and-Spoke-Mergemuster mit gerätespezifischen Backup-Dateien

---

### 17 · 📰 Blog, Kurse & Community

- Übung der Woche · Meditation der Woche
- Blog (WordPress REST API, Jsoup, Offline-Cache)
- 40+ Online-Trainingskurse (nach Freischaltung auf wildnislehrer.com)
- YouTube-Kanal · Podcast · X-Kanal · SIGNAL-Direktkontakt
- Hintergrund-Updates via `ContentUpdateWorker` (WorkManager)

---

## 🛠️ Technologie-Stack

```
Sprache          Kotlin 100 %
Android          7.0 – 15  (API 24 – 36)
Version          17.0 (Build 92)
Architektur      MVVM + Kotlin Coroutines
UI               Android Views · ViewBinding · Material Design
```

| Kategorie | Bibliothek / Dienst |
|---|---|
| Karten | OSMDroid (OpenStreetMap) |
| Routing | OpenRouteService (foot-hiking / cycling-road) |
| Wetter | Open-Meteo API (CC BY 4.0) |
| Regenradar | RainViewer API (Leaflet.js in WebView) |
| Gelände / POI | Overpass API (OpenStreetMap) |
| Diagramme | MPAndroidChart |
| Kamera | Camera2 API |
| PDF | iText7 |
| HTML-Parser | Jsoup 1.17.2 |
| Hintergrund | WorkManager |
| Netzwerk | OkHttp |
| JSON | Gson |
| Sichere Einstellungen | EncryptedSharedPreferences (AES256-GCM) |
| WebDAV | HttpURLConnection + Reflection-Workaround (PROPFIND/MKCOL) |
| Mondphase | MoonPhaseView (eigene Canvas-Klasse) |
| Kompass | CompassRoseView (eigene Canvas-Klasse) |
| Build | Gradle Kotlin DSL |

**Keine Google Play Services · Kein Tracking · Kein Ad-SDK · Kein Crash-Reporting an Dritte**

---

## 🔑 Berechtigungen

| Berechtigung | Verwendung |
|---|---|
| `INTERNET` | Wetter, Karte, Blog, WebDAV-Sync |
| `ACCESS_FINE_LOCATION` | Navigation, Wetter, Nasenarbeit-Karte, Tempo-Trainer |
| `ACCESS_COARSE_LOCATION` | Standort-Fallback |
| `FOREGROUND_SERVICE` | GPS-Tracking (Tempo-Trainer), Navigation, Standort teilen |
| `CAMERA` | Zeckenlupe, Fotos für Notizen und Notfall-PDF |
| `RECORD_AUDIO` | Sprach-Trainer (Frequenzanalyse) |
| `POST_NOTIFICATIONS` | Tempo-Trainer-Warnungen, Läufigkeits-Erinnerungen |
| `VIBRATE` | Feedback im Sprach-Trainer |
| `READ_EXTERNAL_STORAGE` | Backup-Import, Foto-Auswahl |
| `WRITE_EXTERNAL_STORAGE` | Backup-Export, PDF/GPX-Export |

---

## 📲 Installation

| Anforderung | Details |
|---|---|
| Android | 7.0 (API 24) oder höher |
| Speicherplatz | ca. 30 MB |
| Internet | Für Karte, Wetter, Blog-Inhalte, WebDAV-Sync |
| GPS | Optional – für Navigation, Tempo-Trainer, Nasenarbeit |
| ORS-API-Key | Optional – für Routing (kostenlos bei openrouteservice.org) |

---

## 📥 Download

| Plattform | Status | Link |
|---|---|---|
| 🐙 GitHub Releases | ✅ Verfügbar | [Releases](https://github.com/tux4us/HundeWildnisAndroidApp/releases) |
| ▶ Google Play | ✅ Verfügbar | [Play Store](https://play.google.com/store/apps/details?id=com.hundewildnis.app) |

Die App ist **kostenlos**, solange Sie sich durch freiwillige Spenden finanzieren kann.

---

## 🔭 Roadmap

| Version | Status | Inhalt |
|---|---|---|
| **17.0** | ✅ Aktuell | Vitalwerte & Pulscheck · Wetter-Favoriten · Luftqualität & Pollen · 7-Tage-Vorhersage · Wind-Card · Fahrrad-Routing |
| **16.x** | ✅ | WebDAV-Sync · Standort teilen · Zeckenlupe · Nasenarbeit-Expertensystem |
| **18.0** | 🔜 Geplant | Weitere Module nach Community-Feedback |

---

## 🤝 Mitmachen

Issues und Pull Requests sind willkommen. Bitte zuerst ein Issue öffnen.

- 🐛 [Bug melden](https://github.com/tux4us/HundeWildnisAndroidApp/issues)
- 💡 [Feature vorschlagen](https://github.com/tux4us/HundeWildnisAndroidApp/issues)
- 📬 [wildnislehrer.com/kontakt](https://wildnislehrer.com/informationen/kontakt/)

---

## 📄 Lizenz

Dieses Projekt ist unter [diesen Bedingungen](https://github.com/tux4us/HundeWildnisAndroidApp?tab=License-1-ov-file) lizenziert.

---

## 👥 Entwickler

**HundeWildnisSchule Manuela Ulbricht und Peter Jörg Ulbricht GbR** · Freital

- 🌐 [wildnislehrer.com](https://wildnislehrer.com)
- 🐙 [github.com/tux4us/HundeWildnisAndroidApp](https://github.com/tux4us/HundeWildnisAndroidApp)

---

## 🙏 Danksagungen

- Kartendaten © [OpenStreetMap Contributors](https://www.openstreetmap.org/copyright) (ODbL)
- Wetterdaten © [Open-Meteo](https://open-meteo.com/) (CC BY 4.0)
- Regenradar © [RainViewer](https://www.rainviewer.com/)
- Routing © [OpenRouteService](https://openrouteservice.org/) (HeiGIT gGmbH)

---

<div align="center">

Entwickelt mit 💚 für Hunde und ihre Menschen

[🌐 wildnislehrer.com](https://wildnislehrer.com) · [📱 Play Store](https://play.google.com/store/apps/details?id=com.hundewildnis.app) · MIT-Lizenz

</div>
