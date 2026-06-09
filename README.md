<div align="center">

# 🐕 HundeWildnis App

**Dein mobiler Begleiter für professionelles Hundetraining und Wildnispädagogik**

[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Version](https://img.shields.io/badge/Version-13.0%20Build%2076-C9A84C?style=flat-square)](#)
[![License](https://img.shields.io/badge/Lizenz-MIT-5C8A3C?style=flat-square)](LICENSE)
[![No Google Services](https://img.shields.io/badge/Google%20Services-keine-brightgreen?style=flat-square)](#datenschutz)
[![F-Droid](https://img.shields.io/badge/F--Droid-in%20Vorbereitung-lightgrey?style=flat-square)](https://f-droid.org/)

<br>

> 🔒 **100 % Datenschutz** · Kein Tracking · Kein Datenabfluss · Keine Werbung · Keine Google Services · DSGVO-konform

<br>

Eine 100 %ige Eigenentwicklung der **HundeWildnisSchule Manuela Ulbricht und Peter Jörg Ulbricht GbR**, Freital.

[📥 Download](#-download) · [🌐 Website](https://wildnislehrer.com) · [📖 Dokumentation](#-module-im-überblick)

</div>

---

## 📑 Inhaltsverzeichnis

- [Über die App](#-über-die-app)
- [Datenschutz & Sicherheit](#-datenschutz--sicherheit)
- [Module im Überblick](#-module-im-überblick)
  - [1 · Hunde-Stammdaten](#1--hunde-stammdaten)
  - [2 · Intelligentes Trainingstagebuch](#2--intelligentes-trainingstagebuch)
  - [3 · Nasenarbeit – Expertensystem](#3--nasenarbeit--expertensystem)
  - [4 · Läufigkeitstracker](#4--läufigkeitstracker)
  - [5 · Sprach-Trainer](#5--sprach-trainer)
  - [6 · Tempo-Trainer](#6--tempo-trainer)
  - [7 · Notfall-Assistent](#7--notfall-assistent)
  - [8 · Hundewetter](#8--hundewetter)
  - [9 · Revier-Navigation](#9--revier-navigation)
  - [10 · Kompass](#10--kompass)
  - [11 · Höhenmesser](#11--höhenmesser)
  - [12 · Tagesnotizen](#12--tagesnotizen)
  - [13 · Backup-System](#13--backup-system)
  - [14 · Blog, Kurse & Community](#14--blog-kurse--community)
- [Technologie-Stack](#️-technologie-stack)
- [Berechtigungen](#-berechtigungen)
- [Installation & Voraussetzungen](#-installation--voraussetzungen)
- [Download](#-download)
- [Roadmap](#-roadmap)
- [Mitmachen](#-mitmachen)
- [Lizenz](#-lizenz)
- [Danksagungen](#-danksagungen)

---

## 📱 Über die App

Die HundeWildnis App ist ein mobiler Begleiter für den Alltag mit dem Hund – vom täglichen Training über Nasenarbeit und Erste Hilfe bis zur GPS-Navigation und Wetteranalyse. Alle 14 Module sind direkt aufeinander abgestimmt und teilen eine gemeinsame Datenbasis: die Hunde-Profile.

Die App ist vollständig **offline-first** entwickelt. Karten-, Wetter- und Geländefunktionen nutzen bei Bedarf eine Datenverbindung – alle persönlichen Daten verbleiben jedoch ausschließlich lokal auf dem Gerät.

**Kernprinzipien:**
- Alle Nutzerdaten bleiben lokal auf dem Gerät
- Keine proprietären Google-Bibliotheken
- Vollständig quelloffen unter MIT-Lizenz
- F-Droid-kompatibel (Veröffentlichung in Vorbereitung)

---

## 🔐 Datenschutz & Sicherheit

| Merkmal | Details |
|---|---|
| Datenhaltung | 100 % lokal, keine Cloud-Pflicht |
| Tracking | Keines |
| Werbung | Keine |
| Analytics | Keine |
| Google Services | Nicht verwendet |
| API-Verbindungen | Ausschließlich HTTPS/TLS |
| Screenshot-Schutz | `FLAG_SECURE` auf allen sensitiven Activities aktiv |
| Datenweitergabe | Nur wenn der Nutzer aktiv den Export nutzt (PDF, GPX, Share) |
| Compliance | DSGVO-konform |

---

## 🧩 Module im Überblick

---

### 1 · 🐕 Hunde-Stammdaten

Verwalte beliebig viele Hunde-Profile mit vollständigen Stamm-, Gesundheits- und Kontaktdaten – sicher lokal gespeichert in einer übersichtlichen digitalen Akte.

**Basis-Informationen**
- Name, Rasse, Geschlecht, Kastrationsstatus
- Geburtsdatum (flexibles Format: Tag/Monat/Jahr bis nur Jahr)
- Herkunft, Fellfarbe, Profilfoto aus der Galerie

**Identifikation** *(wichtig bei Verlust)*
- 15-stellige Chipnummer
- Steuernummer inkl. Gemeinde
- Tasso-Registriernummer
- Heimtierausweis-Land und Ausweisnummer

**Gesundheit & Vorsorge**
- Impfstatus und weitere Impfungen
- Letzte Entwurmung, Floh-/Zeckenschutz (mit Datum)
- Gewicht mit Messdatum, Schulterhöhe, Körperlänge
- Freies Feld für Allergien, Medikamente, chronische Erkrankungen

**Kontakte & Versicherung**
- Tierarzt-Kontaktdaten
- Haftpflicht- und OP-Versicherungsangaben
- Besitzerdaten, Notfallkontakte

**Export-Optionen**
- PDF mit integriertem QR-Code
- Beliebige Felder als scanfähiger QR-Code (teilbar und speicherbar)
- Formatierte Textliste per Android Share-Sheet

---

### 2 · 🦮 Intelligentes Trainingstagebuch

Vollständiges Trainingstagebuch mit personalisierten Empfehlungen, automatischer Fortschrittsanalyse und Meilenstein-System – abgestimmt auf die 18 Trainingsbereiche der HundeWildnis-Onlinekurse.

**18 Trainingskategorien**

`Leinenarbeit` `Fuß` `Sitz` `Platz` `Steh` `Sozialisierungstechniken` `Langleinentraining` `Distanzunterbrechungen` `Freilauf` `Freifolge` `Teamwalk` `Scouttechniken` `Jagdvermeidung` `Nasenarbeit` `Leinenführigkeit` `Begegnungssituationen` `Stresstoleranztraining` `Straßenroutinen`

**Session-Erfassung**

| Parameter | Optionen |
|---|---|
| Trainingsdauer | bis 120 Minuten (Slider) |
| Wiederholungen | bis 100 je Session |
| Erfolgsbewertung | Schwierigkeiten / Teilweise / Erfolgreich / Sehr erfolgreich |
| Ablenkungslevel | Keine / Gering / Mittel / Hoch |
| Schwierigkeitsgrad | Leicht / Normal / Schwer |
| Wetter & GPS | automatisch vom Standort |
| Notizen | freies Textfeld |

**Fortschritts-Analyse**
- Trainingsfortschritt in % (0–100)
- Aktueller Stand: Beginn → Grundlagen → Aufbau → Festigung → Etabliert
- Trainingsstreaks (aufeinanderfolgende Tage)
- Trend-Analyse: Positiv / Stabil / Rückläufig
- Konsistenz-Score, Erfolgsrate, Verbesserung in % über Zeit

**Wesensmerkmale-System**

Sechs Charakterdimensionen fließen in alle Empfehlungen ein:

| Dimension | Skala |
|---|---|
| Grundtemperament | Sehr ruhig ↔ Sehr lebhaft |
| Selbstbewusstsein | Ängstlich ↔ Selbstsicher |
| Sozialverhalten | Zurückhaltend ↔ Kontaktfreudig |
| Erregbarkeit | Gelassen ↔ Leicht erregbar |
| Lerngeschwindigkeit | Langsam ↔ Schnell |
| Frustrationstoleranz | Niedrig ↔ Hoch |

11 Quick-Profile für schnelle Charakterisierung:
`Ausgeglichen` `Energiegeladen` `Sehr ruhig` `Unsicher` `Sehr selbstbewusst` `Welpe (4–8 Monate)` `Senior (8+ Jahre)` `Arbeitshund` `Sehr sozial` `Eigenständig` `Tierschutzhund`

**Meilenstein-System**
- Erste Session, 5 / 10 / 20+ Sessions pro Kategorie
- 5 aufeinanderfolgende Trainingstage
- Fortschrittsstufen bei 50 %, 75 %, 100 %
- Erfolgsserien (5+ sehr erfolgreiche Sessions)

---

### 3 · 🐾 Nasenarbeit – Expertensystem

Das technisch aufwändigste Modul: ein mehrstufiges Expertensystem, das anhand von Wetterdaten, Geländeanalyse und individuellem Trainingsstand eine präzise Empfehlung für jede Nasenarbeitssession berechnet.

**Drei Disziplinen**

| Disziplin | Beschreibung |
|---|---|
| 🔍 Verlorensuche | Hund folgt Fährtenmischgeruch, findet verlorene Gegenstände. Keine zweite Person nötig. |
| 🧑 Personensuche | Hund folgt individuellem Geruch einer Zielperson. Zweite Person erforderlich. |
| 🗺️ Freisuche | Hund durchsucht Gelände selbständig. Flächenempfehlung in Hektar. |

**Vier-Schichten-Analyse**

```
Schicht 1 – Wetter
  Temperatur · Windstärke (Beaufort) · Windrichtung relativ zur Suchlinie
  Luftfeuchtigkeit · WMO-Wettercode
  → Abruf via Open-Meteo API

Schicht 2 – Topografie
  Höhenprofil · Steigungsklassen (flach / leicht hügelig / hügelig / bergig)
  → Klassifikation via Open-Meteo Elevation API (SRTM-90m)

Schicht 3 – Bodentyp
  Wald · Wiese · Acker · Asphalt · Wasser · weitere OSM-Typen
  Bodenzustand: trocken / feucht / nass · pro Streckenabschnitt
  → Erkennung via Overpass API (OpenStreetMap)

Schicht 4 – Trainingslevel
  Automatisch aus Gesamtzahl absolvierter Sessions ermittelt
```

**Trainingslevel und Distanzempfehlungen**

| Level | Sessions | Verlorensuche | Personensuche | Freisuche | Schwierigkeit |
|---|---|---|---|---|---|
| Anfänger | 0–49 | bis 100 Schritte | bis 100 Schritte | bis 0,2 ha | Leicht |
| Fortgeschritten | 50–99 | bis 250 Schritte | bis 250 Schritte | bis 0,5 ha | Mittel |
| Erfahren | 100–499 | bis 500 Schritte | bis 1.000 Schritte | bis 2,0 ha | Mittel–Schwer |
| Experte | 500+ | bis 1.000 Schritte | bis 2.000 Schritte | bis 5,0 ha | Schwer |

**Kartenintegration (OSMDroid)**
- Strecke per Fingertipp einzeichnen (Polyline) oder Fläche als Polygon markieren
- Automatische Distanz- und Flächenberechnung
- Snap-to-Way: Abschnitte optional auf ORS-Routing (foot-hiking) einrasten
- Live-GPS-Tracking während des Einzeichnens
- Export als PDF und CSV

> **Hinweis:** Alle automatisch ermittelten Geländewerte (Bodentyp, Topografie, Umgebung) können manuell überschrieben werden. Die manuelle Eingabe bleibt als Fallback jederzeit erhalten.

---

### 4 · ♀️ Läufigkeitstracker

Dokumentation und Verlaufsverfolgung der Läufigkeit für Hündinnen.

- Zyklus-Nummer und Zeitraum (Start / Ende)
- Intensität: Leicht / Mittel / Stark
- Symptome (freies Textfeld)
- Verhaltensänderungen: vorbereitete Checkliste + individuelle Notizen
- Scheinträchtigkeit: eigener Abschnitt mit Symptom-Checkliste
- Erinnerungen via Android WorkManager
- Vollständig in das Backup-System integriert

---

### 5 · 🗣️ Sprach-Trainer

Echtzeit-Frequenzanalyse der Stimme: Die App misst kontinuierlich, ob im für Hunde optimal lernunterstützenden Frequenzbereich gesprochen wird.

**Trainingsmodi**

| Modus | Beschreibung |
|---|---|
| Ganzheitliches Training | 5 Zielfrequenzzonen gleichzeitig |
| Fokus-Training | Eine ausgewählte Frequenzzone |

**Feedback-Mechanismen**
- Shimmer-Glüheffekt bei Treffer im Zielbereich
- Vibration (konfigurierbar)
- Ton-Feedback via `ToneGenerator`
- Mikrofon-Fallback-Kette: `VOICE_COMMUNICATION` → `MIC` → `VOICE_RECOGNITION` → `DEFAULT`

**Auswertung**
- Statistiken: Sprechzeit, Effizienz, Treffer-Rate pro Frequenzzone
- PDF-Auswertung nach jeder Session exportierbar
- Vollständige Session-Historie
- Bedienelemente während aktiver Aufnahme gesperrt (keine versehentlichen Eingaben)

---

### 6 · ⏱️ Tempo-Trainer

Echtzeit-GPS-Überwachung der Gehgeschwindigkeit mit Warnfunktion, grafischer Auswertung und GPX/PDF-Export.

**Trainings-Limits** *(während der Messung wechselbar)*
- Wege- und Geländetraining
- Alltags- und Straßentraining
- Wandertraining

**Technisches**
- Messintervall: 30 s oder 60 s
- `ForegroundService` hält GPS auch bei ausgeschaltetem Bildschirm aktiv
- Optionale Benachrichtigung + Ton bei Limit-Überschreitung
- Langzeit-Historie aller Sessions

**Export**
- GPX (öffenbar in jeder Navigations-App, zeigt Tempoverteilung auf der Strecke)
- PDF-Protokoll mit allen Messdaten

---

### 7 · 🚨 Notfall-Assistent

Führt in wenigen Taps durch Erste-Hilfe-Maßnahmen und erstellt sofort eine vollständige Notfallmeldung als PDF.

**Notfallkategorien**

| Kategorie | Ereignistypen | Dringlichkeit |
|---|---|---|
| ⚠️ Unfall | Verkehrsunfall · Blutungen/Wunden · Knochenbruch/Lahmheit · Augenverletzung · Ertrinken · Unterkühlung · Hitzschlag · Fremdkörper verschluckt · Vergiftung (bekannt) | 🔴 Lebensbedrohlich / 🟡 Dringend |
| 💉 Erkrankung | Bewusstlosigkeit/Kollaps · Krampfanfall · Magendrehung · Atemnot · Blutungen (ohne Unfall) · Erbrechen/Durchfall · Vergiftung (unbekannt) · Allergische Reaktion | 🔴 Lebensbedrohlich / 🟡 Dringend |

**Integrierter HLW-Timer**

Automatisches Metronom für Herz-Lungen-Wiederbelebung – kein Knopfdrücken nötig:
```
30 Kompressionen  →  15 Sekunden  (120 BPM)
 2 Beatmungen     →   5 Sekunden
Puls-Check-Erinnerung alle 2 Minuten
```

**Notfall-PDF enthält**
- Stammdaten des Hundes (Name, Rasse, Geschlecht, Gewicht)
- Symptombeschreibung und Beschreibung der Notlage
- GPS-Standort als OpenStreetMap-Link *(optional)*
- Fotos per Kamera oder aus Galerie *(optional)*
- Begleittext für Empfänger ohne PDF-Viewer

---

### 8 · 🌦️ Hundewetter

Umfassender Wetterbericht für den aktuellen Standort – via [Open-Meteo API](https://open-meteo.com/) (CC BY 4.0) und [RainViewer](https://www.rainviewer.com/). Kein Tracking durch Google oder kommerzielle Wetterdienste.

**Aktuelle Wetterdaten**
- Temperatur, gefühlte Temperatur (Windchill + Luftfeuchtigkeit)
- Wetterzustand mit Beschreibung, Luftfeuchtigkeit, Luftdruck (hPa)
- Niederschlag (mm), UV-Index

**Wind**
- Windgeschwindigkeit (km/h), Windrichtung mit Himmelsrichtungspfeil
- Böengeschwindigkeit, Beaufort-Skala (Bft 0–12)

**Pfotenwetter-Index**
- Trainingstipps und Warnungen: heißer Asphalt · Kälte · Zeckenrisiko · UV · Gewitter · Starkregen · Wind (kleine Hunde)

**Tageslicht & Mondphase**
- Sonnenaufgang/-untergang, Morgen-/Abenddämmerung
- Tageslänge mit Vortagsvergleich
- Goldene Stunde für Fotos
- Offline berechnete Mondphasenanzeige

**Vorhersage**
- 24h-Temperatur- und Niederschlagsdiagramm (Auto-Skalierung, Zeitmarkierungen alle 3h)
- Stündliche Vorhersage: Temperatur, gefühlte Temperatur, Regenwahrscheinlichkeit, Wetterzustand

**Regenradar (Nowcast)**
- RainViewer-Radar, animiert über 2 Stunden
- Play/Pause-Steuerung, Zeitstempel je Frame

---

### 9 · 🗺️ Revier-Navigation

Interaktive OpenStreetMap-Karte mit hundefreundlicher POI-Suche, Echtzeit-Routing und Favoritenverwaltung – vollständig ohne Google Maps.

**Karten-Grundfunktionen**
- OpenStreetMap (Mapnik), Live-GPS-Tracking (alle 5 m / 2 s)
- Koordinatenanzeige live, Standort-Sharing per Share-Sheet

**POI-Kategorien** *(Radar-Suche 3 km via Overpass API)*

`Hundeausläufe` `Hundeparks` `Tierärzte` `Tierkliniken` `Tierbedarf` `Tierheime` `Wanderwege` `Aussichtspunkte` `Trinkwasser` `Toiletten` `Supermärkte` `Restaurants` `Apotheken` `Polizei` `Feuerwehr` `Krankenhäuser` `Naturschutzgebiete` `SOS-Punkte`

**Routing**

| Dienst | Rolle |
|---|---|
| OpenRouteService (foot-hiking) | Primär |
| Graphhopper | Fallback 1 |
| OSRM | Fallback 2 |
| Luftlinie | Fallback bei fehlendem Fußweg |

- Live-Rerouting alle 5 m / 2 s, Entfernung und Gehzeit

**POI-Details & Zwei-Klick-Logik**
- 1. Tap: Info (Adresse, Telefon, Web, Öffnungszeiten, Leinenpflicht)
- 2. Tap: Navigation starten

**Favoriten-System**
- Speichern, Umbenennen, Sortieren (Drag & Drop)
- Vollständig in das Backup-System integriert

---

### 10 · 🧭 Kompass

Digitale Orientierungshilfe via Magnetfeldsensor – präzise Himmelsrichtungsanzeige, vollständig offline, keine Internetverbindung erforderlich.

---

### 11 · ⛰️ Höhenmesser

Anzeige der aktuellen Höhe über Meeresspiegel via GPS. Nützlich für Wanderungen, Geländetraining und als Orientierungsgrundlage bei der Nasenarbeit.

---

### 12 · 📝 Tagesnotizen

Digitales Trainings- und Beobachtungstagebuch – Text, Fotos und Geodaten in einer Notiz.

- Titel und freies Textfeld
- Automatische Wetterdaten für den aktuellen Standort
- Automatische GPS-Koordinaten
- Fotos aus Galerie oder direkt per Kamera
- Export als PDF und GPX
- Chronologische Langzeit-Historie

---

### 13 · 📦 Backup-System

Vollständige Sicherung und Wiederherstellung aller App-Daten im offenen ZIP-Format – lokal auf dem Gerät, geräteübergreifend nutzbar.

**Backup-Umfang** *(.hundebackup – ein ZIP-Archiv)*

`Hunde-Profile` `Tagesnotizen` `Fotos` `Geschwindigkeits-Sessions` `Sprach-Sessions` `Notfallberichte` `Trainingstagebuch` `Nasenarbeit-Daten` `Favoriten (POIs)` `Läufigkeits-Einträge` `App-Einstellungen`

**Merge-Strategie**
- UUID-Erhaltung bei Merge (referentielle Integrität)
- Deduplizierung via Chipnummer (Primär) + UUID (Sekundär)
- `lastModified`-Feld: neuere Version gewinnt
- Konflikt-Dialog bei divergierenden Versionen

**Speicherort & Transfer**
- Pfad: `Downloads/HundeWildnis_Backups/`
- Dateiname: `HundeWildnis_Backup_YYYY-MM-DD_HHmm.hundebackup`
- Teilen per Android Share-Sheet
- Import auf beliebig vielen Geräten

> 🔭 **Geplant für Version 14:** WebDAV-Synchronisation mit Nextcloud, Hetzner Storage Box, Mailbox.org, ownCloud und selbst gehosteten Servern. Hub-and-Spoke-Mergemuster, manuelle und automatische Sync-Auslösung via WorkManager.

---

### 14 · 📰 Blog, Kurse & Community

Direkter Zugriff auf alle Inhalte der HundeWildnisSchule.

- **Übung des Tages** – wöchentlich wechselnde Basisübung aus der Schulpraxis
- **Meditation des Tages** – wöchentlich neue Teamgeist-Meditation für Mensch und Hund
- **Blog** – aktuelle Artikel und Neuigkeiten (WordPress-API, Offline-Cache)
- **Online-Kurse** – 40+ professionelle Kurse (nach Freischaltung auf der Webseite)
- **YouTube** – Direkt-Link zum Videokanal
- **Podcast** – Audiobeiträge der HundeWildnisSchule
- **SIGNAL-Gruppe** – Direktkontakt und Support mit Manuela und Jörg
- Hintergrund-Updates via WorkManager (konfigurierbar)

---

## 🛠️ Technologie-Stack

```
Sprache          Kotlin 100 %
Android          7.0 – 15  (API 24 – 36)
Architektur      MVVM + Kotlin Coroutines
UI               Android Views · ViewBinding · Material Design 3
```

| Kategorie | Bibliothek / Dienst |
|---|---|
| Karten | OSMDroid (OpenStreetMap) |
| Routing | OpenRouteService (foot-hiking) |
| Wetter | Open-Meteo API (CC BY 4.0) |
| Regenradar | RainViewer API |
| Gelände | Overpass API (OpenStreetMap) |
| Diagramme | MPAndroidChart |
| PDF | iText7 |
| HTML-Parser | Jsoup 1.17.2 |
| Hintergrund | WorkManager |
| Netzwerk | OkHttp |
| JSON | Gson |
| Sichere Einstellungen | EncryptedSharedPreferences |
| Build | Gradle Kotlin DSL |

**Keine Google Play Services · Kein Tracking · Kein Ad-SDK · Kein Crash-Reporting an Dritte**

---

## 🔑 Berechtigungen

Alle Berechtigungen sind zweckgebunden – keine wird ohne direkten Funktionsbezug angefragt.

| Berechtigung | Verwendung |
|---|---|
| `INTERNET` | Wetter, Karte, Blog-Inhalte |
| `ACCESS_FINE_LOCATION` | Navigation, Wetter, Nasenarbeit-Karte, Tempo-Trainer |
| `ACCESS_COARSE_LOCATION` | Standort-Fallback |
| `FOREGROUND_SERVICE` | GPS-Tracking im Tempo-Trainer (Hintergrund) |
| `CAMERA` | Fotos für Notizen und Notfall-PDF |
| `RECORD_AUDIO` | Sprach-Trainer (Frequenzanalyse) |
| `POST_NOTIFICATIONS` | Tempo-Trainer-Warnungen, Läufigkeits-Erinnerungen |
| `VIBRATE` | Feedback im Sprach-Trainer |
| `READ_EXTERNAL_STORAGE` | Backup-Import, Foto-Auswahl aus Galerie |
| `WRITE_EXTERNAL_STORAGE` | Backup-Export, PDF/GPX-Export |

---

## 📲 Installation & Voraussetzungen

| Anforderung | Details |
|---|---|
| Android | 7.0 (API 24) oder höher |
| Speicherplatz | ca. 30 MB |
| Internet | Für Karte, Wetter, Blog-Inhalte *(lokal gespeicherte Daten funktionieren offline)* |
| GPS | Optional – für Navigation, Tempo-Trainer, Nasenarbeit-Karte |

---

## 📥 Download

| Plattform | Status | Link |
|---|---|---|
| 🐙 GitHub Releases | ✅ Verfügbar | [Releases](https://github.com/HundeWildnisSchule/HundeWildnis-App/releases) |
| ▶ Google Play | ✅ Verfügbar | [Play Store](https://play.google.com/store/apps/details?id=com.hundewildnis.app) |
| 🤖 F-Droid | 🔜 In Vorbereitung | [f-droid.org](https://f-droid.org/) |

Die App ist **kostenlos**. Sie finanziert sich durch freiwillige Spenden – damit sie auch künftig kostenlos zur Verfügung gestellt werden kann.

---

## 🔭 Roadmap

| Version | Feature |
|---|---|
| **13.0** *(aktuell)* | Nasenarbeit-Expertensystem · Läufigkeitstracker · Favoriten-System · Backup v6 mit Merge |
| **14.0** *(geplant)* | WebDAV-Synchronisation (Nextcloud, Hetzner, Mailbox.org, ownCloud, selbst gehostet) |

---

## 🤝 Mitmachen

Contributions sind willkommen! Bitte öffne zuerst ein Issue zur Diskussion, bevor Du einen Pull Request einreichst.

- 🐛 [Bug melden](https://github.com/HundeWildnisSchule/HundeWildnis-App/issues)
- 💡 [Feature vorschlagen](https://github.com/HundeWildnisSchule/HundeWildnis-App/issues)
- 📬 [Kontakt über wildnislehrer.com](https://wildnislehrer.com)
- 💬 SIGNAL-Support-Gruppe (Link in der App)

---

## 📄 Lizenz

Dieses Projekt ist unter der [MIT-Lizenz](LICENSE) lizenziert.

---

## 👥 Entwickler

**HundeWildnisSchule Manuela Ulbricht und Peter Jörg Ulbricht GbR**
Hohe Lehne 14 · 01705 Freital

- 🌐 [wildnislehrer.com](https://wildnislehrer.com)
- 🐙 [github.com/HundeWildnisSchule](https://github.com/HundeWildnisSchule)
- 📧 Kontakt über [wildnislehrer.com/kontakt](https://wildnislehrer.com)

---

## 🙏 Danksagungen

- Kartendaten © [OpenStreetMap Contributors](https://www.openstreetmap.org/copyright) (ODbL)
- Wetterdaten © [Open-Meteo](https://open-meteo.com/) (CC BY 4.0)
- Regenradar © [RainViewer](https://www.rainviewer.com/)
- Routing © [OpenRouteService](https://openrouteservice.org/)

---

<div align="center">

Entwickelt mit 💚 für Hunde und ihre Menschen

[🌐 wildnislehrer.com](https://wildnislehrer.com) · [🐙 GitHub](https://github.com/HundeWildnisSchule) · [📄 MIT-Lizenz](LICENSE)

</div>
