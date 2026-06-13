<div align="center">

# 🐕 HundeWildnis App

**Dein mobiler Begleiter für professionelles Hundetraining und Wildnispädagogik**

[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Version](https://img.shields.io/badge/Version-13.2%20Build%2078-C9A84C?style=flat-square)](#)
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

---

## 📱 Über die App

Die HundeWildnis App ist ein mobiler Begleiter für den Alltag mit dem Hund – vom täglichen Training über Nasenarbeit und Erste Hilfe bis zur GPS-Navigation und Wetteranalyse. Alle Module sind vernetzt und teilen eine gemeinsame Datenbasis.

**Kernprinzipien:**
- **Home-Navigation**: Globaler Home-Button (Haus-Icon) in jeder Toolbar zur schnellen Rückkehr.
- **Offline-First**: Alle persönlichen Daten verbleiben lokal auf dem Gerät.
- **Open-Source**: Keine proprietären Google-Bibliotheken, volle Transparenz.

---

## 🔐 Datenschutz & Sicherheit

| Merkmal | Details |
|---|---|
| Datenhaltung | 100 % lokal, keine Cloud-Pflicht |
| Tracking | Keines |
| Google Services | Vollständig entfernt (F-Droid kompatibel) |
| Screenshot-Schutz | `FLAG_SECURE` aktiv zum Schutz privater Daten |

---

## 🧩 Module im Überblick

### 1 · 🐕 Hunde-Stammdaten
Digitale Akte für beliebig viele Hunde.
- **Gesundheit**: Chipnummer, Tasso, Impfstatus, Gewichtskurve, Allergien.
- **Identität**: Generierung eines **QR-Codes** für die Halsmarke (Notfall-Scan).
- **Export**: Professionelle PDF-Übersicht für Tierarztbesuche oder Behörden.

### 2 · 🦮 Intelligentes Trainingstagebuch
Dokumentation basierend auf den 18 Kategorien der HundeWildnis-Onlinekurse.
- **Messmethodik**: Wahlweise Erfassung nach **Zeit (Min)** oder **Wiederholungen (🔢)**.
- **Fortschritt**: Automatisierte Berechnung von Leveln (Beginner bis Etabliert) und Meilensteinen.
- **Charakter-Profil**: Berücksichtigung von Temperament, Selbstbewusstsein und Lerntempo bei den Empfehlungen.

### 3 · 🐾 Nasenarbeit – Expertensystem
Spezialmodul für Mantrailing und Zielobjektsuche.
- **Expertensystem**: Berechnet Empfehlungen basierend auf Temperatur, Wind (Beaufort), Topografie und Bodentyp (via Overpass API).
- **Karten-Tracking**: Einzeichnen von Suchen, Setzen von Signalen (Fundstücke) und Live-GPS-Aufzeichnung.

### 4 · ♀️ Läufigkeitstracker
- **Zyklus-Management**: Dokumentation von Dauer, Intensität und Symptomen.
- **Erinnerungen**: Prognosen und Benachrichtigungen für den nächsten Zyklus.

### 5 · 🗣️ Sprach-Trainer (Frequenzanalyse)
- **Echtzeit-Feedback**: Misst die Frequenzlage der Stimme, um im optimalen "Hunde-Lernbereich" zu sprechen.
- **Analyse**: Auswertung der Sprechzeit-Effizienz pro Frequenzzone.

### 6 · ⏱️ Tempo-Trainer
- **GPS-Überwachung**: Hält das Gehtempo im optimalen Bereich (Training vs. Alltag).
- **Hintergrund-Dienst**: GPS-Messung läuft auch bei ausgeschaltetem Display stabil weiter.

### 7 · 🚨 Notfall-Assistent
- **Erste Hilfe**: Interaktive Wizards für Vergiftungen, Hitzschlag und Unfälle.
- **HLW-Assistent**: Akustisches Metronom (120 BPM) für die Wiederbelebung beim Hund.
- **Meldung**: Sofortiger PDF-Export des Notfalls inkl. GPS-Standort und Foto für den Tierarzt.

### 8 · 🌦️ Hundewetter
- **Pfoten-Index**: Warnungen vor heißem Asphalt, extremer Kälte oder Zeckenrisiko.
- **Radar**: Integriertes Regenradar (Nowcast) ohne Tracking-Datenabfluss.

### 9 · 🗺️ Revier-Navigation
Spezialisierte Outdoor-Navigation ohne Google Maps.
- **POI-Radar**: Suche nach Tierärzten, Hundeausläufen und Trinkwasserquellen.
- **Profil-Wahl**: Umschalten zwischen **Fußgänger (🥾)** und **Fahrrad (🚲)**.
- **Fahrrad-Safe**: Routenberechnung für das Radfahren mit Hund (begrenzt auf **max. 10 km/h**).
- **Gelände**: Live-Anzeige von Oberflächenbeschaffenheit (Schotter, Waldweg, Asphalt).

### 11 · ⛰️ Höhenmesser
- **Sensor-Fusion**: Nutzt GPS und den **barometrischen Luftdrucksensor** (falls vorhanden) für maximale Präzision beim Bergwandern.

### 13 · 📦 Backup-System
- ** ZIP-Export**: Vollständige Sicherung aller Daten in einer `.hundebackup` Datei.
- **Intelligenter Merge**: Konfliktfreie Zusammenführung von Daten mehrerer Geräte (UUID-basiert).

---

## 🛠️ Technologie-Stack
- **Sprache**: 100 % Kotlin (API 24 – 36)
- **Karten**: OSMDroid (OpenStreetMap)
- **Routing**: OpenRouteService (ORS)
- **Wetter**: Open-Meteo API
- **PDF**: iText7
- **Daten**: EncryptedSharedPreferences, Gson, OkHttp
