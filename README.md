<div align="center">

# 🐕 HundeWildnis App

**Der professionelle digitale Begleiter für Hundetraining, Wildnispädagogik und Sicherheit**

[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Version](https://img.shields.io/badge/Version-13.2%20Build%2078-C9A84C?style=flat-square)](#)
[![License](https://img.shields.io/badge/Lizenz-HWS-5C8A3C?style=flat-square)](LICENSE)
[![No Google Services](https://img.shields.io/badge/Google%20Services-keine-brightgreen?style=flat-square)](#datenschutz)

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
  - [1 · Hunde-Stammdaten & Identität](#1--hunde-stammdaten--identität)
  - [2 · Intelligentes Trainingstagebuch](#2--intelligentes-trainingstagebuch)
  - [3 · Nasenarbeit – Expertensystem](#3--nasenarbeit--expertensystem)
  - [4 · Läufigkeitstracker](#4--läufigkeitstracker)
  - [5 · Sprach-Trainer (Frequenzanalyse)](#5--sprach-trainer-frequenzanalyse)
  - [6 · Tempo-Trainer (GPS)](#6--tempo-trainer-gps)
  - [7 · Notfall-Assistent & HLW-Timer](#7--notfall-assistent--hlw-timer)
  - [8 · Hundewetter & Pfoten-Index](#8--hundewetter--pfoten-index)
  - [9 · Revier-Navigation (OSM)](#9--revier-navigation-osm)
  - [10 · Outdoor-Werkzeuge](#10--outdoor-werkzeuge)
  - [11 · Backup & Data Merge](#11--backup--data-merge)
  - [12 · Wissen & Medien](#12--wissen--medien)
- [Technologie-Stack](#️-technologie-stack)
- [Berechtigungen](#-berechtigungen)
- [Installation & Voraussetzungen](#-installation--voraussetzungen)
- [Lizenz](#-lizenz)

---

## 📱 Über die App

Die HundeWildnis App ist ein umfassendes Werkzeug für den Alltag mit dem Hund. Sie vereint professionelles Training mit Sicherheitsfeatures und spezialisierter Outdoor-Navigation. Alle 14 Module sind vernetzt und teilen eine gemeinsame Datenbasis, wobei der Datenschutz durch eine strikte **Offline-First-Strategie** gewährleistet wird.

**Kernmerkmale:**
- **Globale Home-Navigation**: Ein Haus-Icon in jeder Toolbar ermöglicht den sofortigen Rücksprung zur Startseite.
- **Vollständige Datensouveränität**: Keine Cloud-Pflicht, keine proprietären Google-Bibliotheken.
- **Expertensysteme**: Mathematische und regelbasierte Modelle unterstützen bei der Trainingsplanung (z.B. Nasenarbeit).

---

## 🔐 Datenschutz & Sicherheit

Die App wurde von Grund auf so entwickelt, dass keine sensitiven Informationen das Gerät verlassen.

| Merkmal | Details |
|---|---|
| Datenhaltung | 100 % lokal in einer verschlüsselten Datenbankstruktur |
| Tracking | Keines (keine Analytics, keine Werbe-IDs) |
| Google Services | Vollständig entfernt (F-Droid kompatibel) |
| Screenshot-Schutz | Aktiv (`FLAG_SECURE`) zum Schutz persönlicher Profile |
| Sicherheit | HTTPS/TLS für alle notwendigen API-Anfragen (Wetter, OSM) |

---

## 🧩 Module im Überblick

### 1 · 🐕 Hunde-Stammdaten & Identität

Verwalte detaillierte Akten für beliebig viele Hunde sicher auf deinem Gerät.

*   **Identifikation**: 15-stellige Chipnummer, Steuernummer, Tasso-ID und Heimtierausweis-Daten.
*   **Gesundheit**: Protokollierung von Impfungen, Entwurmungen und Parasitenschutz mit automatischer Wiedervorlage. Erfassung von Gewicht (mit Kurve), Schulterhöhe und Körperlänge.
*   **Charakter-Profil**: Einzigartiges System zur Erfassung von 6 Wesensmerkmalen (Temperament, Selbstbewusstsein, Sozialverhalten, Erregbarkeit, Lerntempo, Frustrationstoleranz) zur individuellen Trainingsanpassung.
*   **Notfall-Identität**: Generierung eines scanfähigen QR-Codes für die Halsmarke. Findet jemand deinen Hund, kann er ohne Speziallesegerät sofort Kontakt aufnehmen oder lebenswichtige Infos (Allergien, Medikamente) einsehen.

### 2 · 🦮 Intelligentes Trainingstagebuch

Vollständige Dokumentation abgestimmt auf die 18 Fachbereiche der HundeWildnisSchule.

*   **18 Kategorien**: Leinenarbeit, Fuß, Distanzunterbrechung, Jagdvermeidung, Freifolge, Teamwalk, Stresstoleranz u.v.m.
*   **Flexibles Tracking**: Erfassung wahlweise nach **Zeit (Minuten)** oder **Wiederholungen (🔢)**.
*   **Fortschrittsanalyse**: Automatische Berechnung von 5 Leveln (Beginner bis Etabliert), Erfolgsraten und Konsistenz-Scores.
*   **Empfehlungs-Engine**: Intelligente Vorschläge für die nächste Session basierend auf Erfolg, Charakterdimensionen des Hundes und Trainingsfrequenz.
*   **Meilensteine**: System für Belohnungen bei Erreichen von Zielen (z.B. 20 Sessions, 5-Tage-Streak, 100% Fortschritt).

### 3 · 🐾 Nasenarbeit – Expertensystem

Das Expertensystem zur Planung von Verlorensuche, Personensuche (Mantrailing) und Freisuche.

*   **Vier-Schichten-Analyse**:
    1.  **Wetter**: Echtzeit-Check von Temperatur, Windstärke (Beaufort), Windrichtung und Luftfeuchtigkeit.
    2.  **Topografie**: Höhenprofil und Steigungsklassen (flach bis bergig) via SRTM-Daten.
    3.  **Boden**: Detektion von Untergründen (Wald, Wiese, Asphalt) via Overpass API.
    4.  **Erfahrung**: Abgleich mit dem individuellen Trainingsstand des Hundes.
*   **Karten-Integration**: Strecke per Fingertipp einzeichnen, Signale (Fundstücke) setzen und die Fläche (Hektar) automatisch berechnen lassen.
*   **Eignungsprüfung**: Regelbasiertes Scoring schlägt die optimale Disziplin für den heutigen Tag vor (z.B. "Starker Wind begünstigt heute die Freisuche").

### 4 · ♀️ Läufigkeitstracker

Spezialisiertes Modul für Hündinnen zur gesundheitlichen Überwachung.

*   **Zyklus-Log**: Dokumentation von Dauer, Intensität (Leicht bis Stark) und Verhaltensauffälligkeiten.
*   **Gesundheits-Check**: Überwachung von Scheinträchtigkeit und psychischen Symptomen.
*   **Erinnerung**: Automatisierte Prognose und Benachrichtigung für den nächsten voraussichtlichen Zyklus.

### 5 · 🗣️ Sprach-Trainer (Frequenzanalyse)

Echtzeit-Frequenzanalyse der menschlichen Stimme für ein besseres Hund-Mensch-Verständnis.

*   **FFT-Messung**: Schnelle Fourier-Transformation zur Analyse der Stimmlage während der Kommandogabe.
*   **Zielbereiche**: Markierung von 5 Frequenzzonen, die für Hunde optimal lernunterstützend wirken.
*   **Feedback**: Optische Effekte (Shimmer) bei Treffern im Zielbereich und haptisches Feedback zur Konditionierung der eigenen Stimme.
*   **Export**: PDF-Zusammenfassung der Sprechzeiten und Trefferraten pro Trainingseinheit.

### 6 · ⏱️ Tempo-Trainer (GPS)

Echtzeit-Überwachung der Gehgeschwindigkeit zur Vermeidung von Fehlbelastungen.

*   **Modi**: Einstellbare Limits für Geländetraining, Alltagsroutinen oder Wandern.
*   **Warnfunktion**: Akustische und haptische Signale bei Überschreitung des gewählten Tempolimits.
*   **Hintergrund-Betrieb**: Ein `ForegroundService` sorgt für stabile GPS-Messung auch bei ausgeschaltetem Bildschirm.
*   **Export**: GPX-Dateien zur Ansicht der Tempoverteilung in externen Karten-Apps.

### 7 · 🚨 Notfall-Assistent & HLW-Timer

Geführte Soforthilfe in kritischen Situationen.

*   **Erste-Hilfe-Wizard**: Schritt-für-Schritt-Anleitungen für Notfälle wie Magendrehung, Vergiftung, Hitzschlag oder Knochenbrüche.
*   **HLW-Assistent (CPR)**: Akustisches und visuelles Metronom für die Wiederbelebung (30 Kompressionen à 120 BPM gefolgt von 2 Beatmungen) inklusive Puls-Check-Erinnerung alle 2 Minuten.
*   **Notfall-PDF**: Erstellt binnen Sekunden eine komplette Meldung für den Tierarzt mit GPS-Standort, Fotos und Vitalwerten.

### 8 · 🌦️ Hundewetter & Pfoten-Index

Spezialisierte Wetterdaten für Hundehalter (via Open-Meteo & RainViewer).

*   **Pfoten-Index**: Spezifische Warnungen vor heißem Asphalt, Kälte, Zeckenrisiko und UV-Belastung.
*   **Wind-Check**: Anzeige nach Beaufort-Skala zur Einschätzung der Sicherheit für kleine Hunde oder Nasenarbeit.
*   **Regenradar**: Animiertes Nowcast-Radar zur präzisen Planung der nächsten Gassirunde.
*   **Licht & Mond**: Anzeige von Sonnenaufgang/-untergang, goldener Stunde und offline berechneten Mondphasen.

### 9 · 🗺️ Revier-Navigation (OSM)

Spezialisierte Outdoor-Navigation vollständig ohne Google Maps.

*   **POI-Suche**: Schneller Zugriff auf hunderelevante Orte wie Tierärzte, Hundeparks, Trinkwasserquellen, Apotheken oder SOS-Punkte via Overpass API.
*   **Routing-Profile**: Umschaltbar zwischen **Fußgänger (🥾)** und **Fahrrad (🚲)**.
*   **Hunde-Sicherheit**: Automatische Begrenzung der Fahrrad-Geschwindigkeit auf **max. 10 km/h** für realistische ETA-Berechnungen beim Mitführen von Hunden.
*   **Gelände-Infos**: Detaillierte Anzeige von Oberflächen (Waldweg, Schotter, Gras, Asphalt) und Wegtypen entlang der Route.

### 10 · Outdoor-Werkzeuge

Präzise Instrumente für das Gelände.

*   **Höhenmesser**: Kombiniert GPS-Daten mit dem **barometrischen Luftdrucksensor** des Geräts (Sensor-Fusion) für maximale Genauigkeit. Automatischer QNH-Abgleich via Wetterdienst.
*   **Kompass**: Klassische, offline-fähige Richtungsanzeige via Magnetfeldsensor.
*   **Tagesnotizen**: Digitales Tagebuch mit Fotos, automatischen Wetterdaten und Geotags pro Eintrag.

### 11 · Backup & Data Merge

Datensicherheit und Austausch ohne Cloud-Zwang.

*   ** ZIP-Sicherung**: Vollständiger Export aller Daten, Profile und Fotos in einem offenen Format (`.hundebackup`).
*   **Intelligenter Merge**: Konfliktfreie Zusammenführung von Daten mehrerer Geräte. Erkennt Duplikate via UUID und Chipnummer; neuere Änderungen gewinnen automatisch.
*   **Transfer**: Einfaches Teilen via Android Share-Sheet (E-Mail, Messenger, Lokal).

### 12 · Wissen & Medien

Direkte Anbindung an das Ökosystem der HundeWildnisSchule.

*   **Online-Kurse**: Zugriff auf über 40 Kursmodule (Offline-Cache unterstützt).
*   **Blog & Community**: Integrierter Reader für Fachartikel, Übungen des Tages und Direktkontakt via SIGNAL.

---

## 🛠️ Technologie-Stack

*   **Sprache**: 100 % Kotlin (API 24 – 36)
*   **Architektur**: MVVM + Kotlin Coroutines + ViewBinding
*   **Karten**: OSMDroid (OpenStreetMap)
*   **Routing**: OpenRouteService (foot-hiking / cycling)
*   **Wetter & Gelände**: Open-Meteo API (CC BY 4.0), Overpass API, RainViewer
*   **Bibliotheken**: iText7 (PDF), MPAndroidChart, Jsoup, OkHttp, Gson

---

## 📄 Lizenz

Dieses Projekt ist unter [diesen Bedingungen(https://github.com/tux4us/HundeWildnisAndroidApp?tab=License-1-ov-file)] lizenziert.

---

<div align="center">

Entwickelt mit 💚 für Hunde und ihre Menschen
[🌐 wildnislehrer.com](https://wildnislehrer.com) · [🐙 GitHub](https://github.com/HundeWildnisSchule)

</div>
