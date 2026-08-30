<div align="center">

# 🐕 HundeWildnis App

**Mobiler Begleiter für professionelles Hundetraining, Wildnispädagogik und den Alltag mit dem Hund**

[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Version](https://img.shields.io/badge/Version-20.2%20Build%20114-C9A84C?style=flat-square)](#)
[![License](https://img.shields.io/badge/Lizenz-MIT-5C8A3C?style=flat-square)](LICENSE)
[![No Google Services](https://img.shields.io/badge/Google%20Services-keine-brightgreen?style=flat-square)](#-datenschutz--sicherheit)
[![F-Droid](https://img.shields.io/badge/F--Droid-in%20Vorbereitung-lightgrey?style=flat-square)](https://f-droid.org/)

> 🔒 **100 % Datenschutz** · Kein Tracking · Kein Datenabfluss · Keine Werbung · Keine Google Services · DSGVO-konform

**Eine 100 %ige Eigenentwicklung der HundeWildnisSchule Manuela Ulbricht und Peter Jörg Ulbricht GbR, Freital**

[📥 Download](#-download) · [🌐 Website](https://wildnislehrer.com/hundewildnisapp/) · [📖 Funktionsübersicht](#-funktionsübersicht)

</div>

---

## 📑 Inhaltsverzeichnis

- [Über die App](#-über-die-app)
- [Datenschutz & Sicherheit](#-datenschutz--sicherheit)
- [Was ist neu in 20.2](#-was-ist-neu-in-202)
- [Funktionsübersicht](#-funktionsübersicht)
  - [A · Hamburger-Menü](#a--hamburger-menü)
  - [B · Startseite (6 Kachelgruppen)](#b--startseite-6-kachelgruppen)
  - [C · Untere Navigationsleiste (Footer)](#c--untere-navigationsleiste-footer)
- [Technologie-Stack](#️-technologie-stack)
- [Berechtigungen](#-berechtigungen)
- [Installation](#-installation)
- [Download](#-download)
- [Mitmachen](#-mitmachen)
- [Lizenz](#-lizenz)
- [Danksagungen](#-danksagungen)

---

## 📱 Über die App

Die HundeWildnis App ist ein mobiler Begleiter für den Alltag mit dem Hund – vom täglichen Training über Nasenarbeit, Vitalwertmessung und Erste Hilfe bis zu GPS-Navigation und Wetteranalyse. Alle Funktionsbereiche teilen eine gemeinsame Datenbasis: die Hunde-Profile.

**Kernprinzipien:**
- Alle Nutzerdaten bleiben lokal auf dem Gerät – keine Cloud-Pflicht
- Keine proprietären Google-Bibliotheken, keine Google Services
- WebDAV-Synchronisation für geräteübergreifende Datensicherung (Nextcloud & kompatible Server)
- **Ausschließlich verfügbar über den Google Play Store**

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
| Compliance | DSGVO-konform |

---

## 🆕 Was ist neu in 20.2

**Version 20.2 · VersionCode 114**

### Revier-Navigation
- Overpass-Ergebnisse vom Typ „way" (Weg) und „relation" (Fläche) werden jetzt als **echter Umriss** (Polygon bzw. Polyline) statt als Punktwolke aus Einzelmarkern gezeichnet; reine Geometrie-Stützpunkte eines Weges/einer Fläche werden dabei korrekt herausgefiltert und nicht mehr zusätzlich als eigenständige Punkte angezeigt
- Neue Untertyp-Erkennung (`deriveSubtype`): OSM-Rohdaten wie `emergency=defibrillator` werden in präzise deutsche Bezeichnungen übersetzt (z. B. „Defibrillator (AED)", „Rettungswache", „Hydrant", „Notruftelefon", „Arztpraxis"); besonders relevant für die Kategorie „Notfall", die zuvor jeden Eintrag nur als „Notfall" auswies. Der Untertyp erscheint sowohl auf der Ergebnis-Karte in der Liste als auch im Detaildialog
- Suchabfragen für „Feuerwehr" (jetzt inkl. Hydranten) und „Notfall" (jetzt inkl. Wasserrettung, Notruftelefon, Rettungsstation) präzisiert; eine zu unscharfe Sammelabfrage wurde entfernt
- Kartenbedienung: feste Zoom-Buttons (+/−) ergänzt; das Bedienfeld am unteren Rand lässt sich nun zusätzlich zum Ziehen auch per Tap auf den gesamten Griffbereich ein-/ausklappen (bisheriger reiner Ziehbalken war als Fläche zu schmal)
- Suchergebnisse bleiben nach einem Kartenwechsel (z. B. Rückkehr aus der Navigation) jetzt korrekt erhalten, statt sich auf die zuletzt gewählte Kategorie zurückzusetzen

### Kompass
- Neue Klasse `SkyCalculator`: berechnet nach astronomischen Standardverfahren (Jean Meeus, vereinfacht) in Echtzeit die Position von Sonne, Mond und Polarstern/Großem Wagen für den aktuellen Standort
- Anzeige von Azimut, Himmelsrichtung und Höhe über Horizont für Sonne und Mond, zusätzlich der Mond-Beleuchtungsgrad in Prozent
- Polaris-Anzeige inklusive „Himmelsuhr": Position des Großen Wagens relativ zum Polarstern als Uhrzeigerstellung (1–12), eine traditionelle Orientierungsmethode zur nächtlichen Zeit- und Richtungsbestimmung
- Sonne, Mond und Polaris werden zusätzlich direkt als Icons am Kompassring eingezeichnet (Sonnenscheibe mit Strahlen, Mondscheibe mit tatsächlicher Sichelform entsprechend dem Beleuchtungsgrad, funkelnder Polarstern mit Mini-Zifferblatt für den Großen Wagen); Himmelskörper unter dem Horizont werden abgedunkelt, aber weiterhin richtungsweisend dargestellt
- Aktualisierung der Himmelsberechnung alle 30 Sekunden, ausschließlich während die Kompass-Ansicht aktiv im Vordergrund ist
- Kompassglättung beschleunigt für spürbar direktere Reaktion (Glättungsfaktor von 0,2 auf 0,35 erhöht, Historienpuffer von 5 auf 3 Werte verkürzt, Mindest-Änderungsschwelle von 2° auf 1° gesenkt)

### Standortfreigabe – Sicherheitsfeature mit Effizienzoptimierung
Umfassende Überarbeitung von `LocationShareService` zur Reduzierung von Prozessorlast und Akkuverbrauch um geschätzt 80–90 %, ohne Genauigkeit bei tatsächlicher Bewegung einzubüßen:
1. **Kein Dauer-WakeLock mehr:** Der Dienst hält den Prozessor nicht länger durchgehend wach. Ein WakeLock wird nur noch transient und ausschließlich für die Dauer eines einzelnen Upload-Vorgangs angefordert (maximal 60 Sekunden), inklusive Schutz gegen überlappende Anfragen (Mutex).
2. **Adaptives GPS-Polling:** Das minimale GPS-Abfrageintervall wurde von 10 auf 20 Sekunden erhöht, zusätzlich wurde eine Mindestbewegungsdistanz von 10 Metern eingeführt – das GPS-Modul kann dadurch im Stillstand in einen Stromsparmodus übergehen.
3. **Smart-Upload-Logik:** Ein regulärer Upload erfolgt alle 60 Sekunden nur dann, wenn seit dem letzten Upload eine Bewegung von mehr als 15 Metern erkannt wurde. Steht das Gerät still, wird stattdessen nur alle 5 Minuten ein Status-Paket (Heartbeat) gesendet, damit andere Teilnehmende weiterhin erkennen können, dass das Gerät online ist.
4. **Sofort-Trigger:** Bei einer signifikanten Bewegung von mehr als 20 Metern wird unabhängig vom regulären Intervall sofort ein Upload ausgelöst, um bei aktiver Bewegung (z. B. beim Wandern) die Genauigkeit hochzuhalten.

---

## 🧭 Funktionsübersicht

Die App gliedert sich in drei Bedienebenen: das **Hamburger-Menü** (App-weite Systemfunktionen), die **Startseite** mit sechs Kachelgruppen (die eigentlichen Funktionsmodule) und die **untere Navigationsleiste** als Footer (Wechsel zwischen den vier Hauptbereichen sowie externe Kanäle).

---

### A · Hamburger-Menü

Erreichbar über das Drei-Punkte-Symbol rechts oben in der Toolbar – auf der Startseite ebenso wie in Blog, Seiten und Online-Kursen.

| Eintrag | Funktion |
|---|---|
| 🔁 Aktualisieren | Lädt die Blog-Vorschau bzw. die aktuell sichtbare Liste neu |
| 🌞🌒 Theme wechseln | Umschalten zwischen dunklem und hellem Erscheinungsbild, gespeichert für künftige Starts |
| ⚙️ Einstellungen | Öffnet Dialog mit: Hintergrund-Updates ein/aus · Beschränkung auf WLAN · Benachrichtigungen ein/aus · eigener OpenRouteService-API-Key (eingeben/testen/löschen) |
| 📦 Backup erstellen | Vollständige Sicherungsdatei in `Downloads/HundeWildnis_Backups`, anschließend Teilen-Angebot |
| 📥 Backup importieren | Dateiauswahl, Prüfung, Abfrage der Merge-Art (Hinzufügen/Ersetzen/Zusammenführen) vor der Wiederherstellung |
| 📖 Anleitung zur Nutzung | Kurzanleitung zu den wichtigsten Funktionen |
| 🛡️ Berechtigungen der App | Erläuterung der angeforderten Systemberechtigungen |
| 📜 Datenschutzerklärung | Vollständige Datenschutzerklärung von wildnislehrer.com, in-app |
| 📎 Impressum | Anbieterkennzeichnung |
| ℹ️ Über die App | Versionsnummer, Website- und Kontakt-Link |

**Zum ORS-Routing-Key:** Standardmäßig nutzt die App einen in der App hinterlegten OpenRouteService-Schlüssel. Ein eigener, kostenloser Schlüssel ist optional, wird verschlüsselt gespeichert und hat Vorrang. Ablauf der Routenberechnung: zunächst vollständige Route mit Höhenprofil/Oberflächen-/Wegtyp-Daten → bei Fehlschlag Route nur mit Höhendaten → letzter Fallback gestrichelte Luftlinie.

---

### B · Startseite (6 Kachelgruppen)

#### Gruppe 1 – Übungen der Woche
| Kachel | Beschreibung |
|---|---|
| 🏋️ Übung der Woche | Wöchentlich wechselnde Basisübung aus der Schulpraxis (Webinhalt in-app) |
| 🧘 Meditation | Wöchentlich neue Teamgeist-Meditation (Webinhalt in-app) |
| 🌬️ Entspannung | Geführte 4-Phasen-Atemübung (Ein-/Ausatmen/Halten, je 4 s) mit wachsendem/schrumpfendem Atemkreis, Vibration je Phasenwechsel, optionale Hintergrundmusik, Laufzeitanzeige |

#### Gruppe 2 – Trainingswerkzeuge
| Kachel | Kernfunktionen | Unterfunktionen |
|---|---|---|
| ⏱️ Tempo-Trainer | GPS-Geschwindigkeitsüberwachung als Hintergrunddienst | Limits 2,5/3,0/4,0 km/h laufend wechselbar · Vibration/Ton bei Überschreitung separat schaltbar · Live-Diagramm · Messintervall 30/60 s · eigenes Untermenü (Anleitung/Berechtigungen/Über) · Historie mit Löschfunktion · Export PDF/GPX/Teilen |
| 🗣️ Sprach-Trainer | Stimmfrequenzanalyse, 5 Zielfrequenzen (108/216/324/432/648 Hz, ±5 Hz) | Ganzheitliches oder Fokus-Training · Vibration/Ton unabhängig schaltbar · Statistik je Sitzung · PDF-Export, Historie |
| ⏲️ Intervall-Timer | Konfigurierbarer Intervall-Timer | Aktiv/Pause 10–120 s, 1–20 Runden, Abkühlphase 0–300 s · 6 Schnellprofile (Standard/Welpe/Senior/Energiegeladen/Arbeitshund/Tierschutzhund) · automatischer Vorschlag nach Wesensmerkmalen · Ton/Vibration je Phase · Historie · Übergabe ans Trainingstagebuch |
| 📔 Trainingstagebuch | 18 Trainingskategorien je Hund, Wochenstatistik auf Hundekarte | Session-Erfassung (Dauer/Wiederholungen, Erfolg, Ablenkung, Schwierigkeit, Ort, Wetter, GPS, Notiz) · Fortschritt 0–100 % mit 5 Trainingsständen · Trend/Streak/Konsistenz · automatische Meilensteine · PDF-/CSV-Export gesamt, vollständiges Löschen je Hund |
| 📅 Aktivitätskalender | Monatskalender mit Trainingstagen | Tagesdetail per Tap · Vorausplanung mit automatischem Dauervorschlag und Erinnerung · 7-Tage-Vorschau mit Wettereignungs-Ampel |
| 🐾 Nasenarbeit | Regelbasiertes Expertensystem, 3 Disziplinen (Verlorensuche/Personensuche/Freisuche) | Vier-Schichten-Analyse (Wetter/Topografie/Bodentyp/Level) · automatische Gewitterwarnung · Karten-Werkzeug (Polyline/Polygon, Snap-to-Way) · Sitzungsdokumentation mit Fotos · Statistik, PDF-/CSV-Export je Hund |

#### Gruppe 3 – Outdoorwerkzeuge
| Kachel | Kernfunktionen | Unterfunktionen |
|---|---|---|
| 📝 Tagesnotizen | Notizen mit Text, Foto, Geodaten | Automatischer Wetter-/GPS-Abruf · Kamera/Galerie · PDF-/GPX-Export, Teilen, Löschen · Historie |
| 🌦️ Revier-Wetter | Umfassender Wetterbericht (Open-Meteo) | Temperatur/Wind/Luftdruck/UV · Pfotenwetter-Index · Tageslicht/Mondphase · 24h-/7-Tage-Vorschau · Luftqualität/Pollen · Regenradar · Wetter-Favoriten |
| ⛈️ Gewitterwarner | Zwei Funktionen: Blitz-Donner-Distanzrechner (offline) + Gewitterrisiko-Vorhersage | Zeitmessung Blitz→Donner, Ampel-Einstufung, Trend/Näherungsgeschwindigkeit/ETA bei Folgemessungen |
| 🗺️ Revier-Navigation | OSM-Karte, POI-Suche, Routing – **s. Detailabschnitt „Was ist neu"** | 34 POI-Kategorien (3 km, Gewitterschutz 5 km) mit Untertyp-Erkennung und Flächen-/Wege-Umrissen · Routing Fuß/Fahrrad/Auto · Live-Rerouting · Favoriten · Zoom-Buttons, umschaltbares Bedienfeld |
| 🧭 Kompass | Digitale Orientierung + Himmelsberechnung – **s. Detailabschnitt „Was ist neu"** | Sensor-Fallback-Kette · Sonne/Mond/Polaris-Anzeige inkl. Großer-Wagen-Uhrzeiger, alle 30 s aktualisiert, vollständig offline berechnet |
| ⛰️ Höhenmesser | GPS- und Barometer-Höhe | Automatische Kalibrierung über GPS-Startwert · QNH-Abruf für höhere Genauigkeit |

#### Gruppe 4 – Hund, Sicherheit und Notfall
| Kachel | Kernfunktionen | Unterfunktionen |
|---|---|---|
| 🐕 Stammdaten | Beliebig viele Hundeprofile | Basis-/Identifikations-/Gesundheits-/Kontaktdaten · Läufigkeitstracker · 6 Wesensmerkmal-Skalen, 11 Schnellprofile · Export (Text/QR/PDF) · eigenständige Notfallmeldung ab Profilkarte |
| ❤️ Pulscheck | Geführte Puls-/Atemfrequenzmessung | 15-s-Countdowns mit automatischer Hochrechnung · Bewertung nach Schulterhöhe · Kapillarfüllzeit, Schleimhautfarbe, Bewusstsein, Belastungsszenario · Übernahme in Notfallmeldung · Verlauf, CSV-Export |
| 🔍 Zeckenlupe | Digitale Lupe (Camera2 API) | 3 Filter (Wärmebild/Invertierung/Hoher Kontrast) · Antipp-Fokus · Freeze-Frame · Speichern in Galerie |
| 🌿 Gefahrenpflanzen | Offline-Datenbank, 56 Giftpflanzen | Saisonale Warnung · Textsuche, Toxizitäts-Filter, Saisonal-Umschalter · eigene Fotos je Pflanze · Verknüpfung mit Notfall-Assistent |
| 📍 Standortfreigabe | WebDAV-basierte Geräteortung – **s. Detailabschnitt „Was ist neu"** | Bewegungsbasierte Uploads mit Heartbeat-Fallback · frei wählbares Geräte-Label · Karte mit Stale-Markierung · Routing zum Standort |
| 🚨 NOTFALL | Vierstufiger Erste-Hilfe-Assistent | Hund wählen → Unfall/Erkrankung → 17 Notfalltypen → Symptom-Check → Anleitung mit HLW-Timer (30:2, 120 BPM) und Countdown-Timer je nach Bedarf · Ein-Tap-Übergabe in Notfallmeldung mit PDF-Export |

#### Gruppe 5 – Informationen und Lernen
| Kachel | Beschreibung |
|---|---|
| 📰 Blog | Artikel von wildnislehrer.com mit Kategorie-Filter |
| 📄 Seiten | Themengruppen Hundeschule, Wildnisschule, weitere Informationen |
| 🎓 Online-Kurse | 41 nummerierte Onlinekurse, Freischaltung über wildnislehrer.com |

#### Gruppe 6 – App-Service
| Kachel | Kernfunktionen | Unterfunktionen |
|---|---|---|
| 📦 Backup | Sicherung, Wiederherstellung, Synchronisation | Backup erstellen · Wiederherstellen (3 Merge-Modi) · WebDAV-Sync (Hub-and-Spoke, 14 selektive Datenbereiche) · Standortfreigabe-Zugriff · Speicher bereinigen |
| 💛 Unterstützung | Freiwillige Finanzierung | Wero, PayPal, Banküberweisung, Zahlungslink · kostenlos: bewerten, teilen |

---

### C · Untere Navigationsleiste (Footer)

Durchgehend sichtbare Navigationsleiste am unteren Bildschirmrand mit fünf Einträgen:

| Eintrag | Funktion |
|---|---|
| 🏠 Start | Zurück zur Kachel-Startseite |
| 📰 Blog | Blog-Ansicht mit Kategorie-Filter |
| 📄 Seiten | Seiten-Ansicht (Hundeschule/Wildnisschule/Weitere Informationen) |
| 🎓 Kurse | Kursübersicht, 41 Onlinekurse |
| ⋯ Mehr | Öffnet Auswahlfenster mit externen Kanälen |

**Untermenü „Mehr":**
- 🎥 YouTube-Kanal (öffnet nach Möglichkeit direkt die YouTube-App)
- 🎙️ Podcast-Übersicht auf wildnislehrer.com, in-app
- 🎧 Podcast-Kanal auf Spotify
- 💬 SIGNAL-Chat mit Manuela (mit Bestätigungsdialog vor Weiterleitung)
- 💬 SIGNAL-Chat mit Jörg (mit Bestätigungsdialog vor Weiterleitung)

---

## 🛠️ Technologie-Stack

```
Sprache          Kotlin 100 %
Android          7.0 – 15  (API 24 – 36)
Version          20.2 (Build 114)
Architektur      MVVM + Kotlin Coroutines
UI               Android Views · ViewBinding · Material Design
```

| Kategorie | Bibliothek / Dienst |
|---|---|
| Karten | OSMDroid (OpenStreetMap) |
| Routing | OpenRouteService (Fuß/Fahrrad/Auto) |
| Wetter | Open-Meteo API (CC BY 4.0) |
| Regenradar | RainViewer API (Leaflet.js in WebView) |
| Gelände / POI | Overpass API (OpenStreetMap) |
| Astronomische Berechnung | Eigene Implementierung nach Jean Meeus (`SkyCalculator`) |
| Diagramme | MPAndroidChart |
| Kamera | Camera2 API |
| PDF | iText7 |
| HTML-Parser | Jsoup |
| Hintergrund | WorkManager |
| Netzwerk | HttpURLConnection |
| JSON | Gson |
| Sichere Einstellungen | EncryptedSharedPreferences (AES256-GCM) |
| WebDAV | HttpURLConnection + Reflection-Workaround (PROPFIND/MKCOL) |
| Build | Gradle Kotlin DSL |

**Keine Google Play Services · Kein Tracking · Kein Ad-SDK · Kein Crash-Reporting an Dritte**

---

## 🔑 Berechtigungen

| Berechtigung | Verwendung |
|---|---|
| `INTERNET` | Wetter, Karte, Blog, WebDAV-Sync |
| `ACCESS_FINE_LOCATION` | Navigation, Wetter, Nasenarbeit-Karte, Tempo-Trainer, Kompass (Himmelsberechnung), Standortfreigabe |
| `ACCESS_COARSE_LOCATION` | Standort-Fallback |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` | GPS-Tracking (Tempo-Trainer, Standortfreigabe, Navigation) |
| `CAMERA` | Zeckenlupe, Fotos für Notizen, Notfall-PDF, Gefahrenpflanzen |
| `RECORD_AUDIO` | Sprach-Trainer (Frequenzanalyse) |
| `POST_NOTIFICATIONS` | Tempo-Trainer-Warnungen, Läufigkeits-Erinnerungen, Blog-Updates |
| `VIBRATE` | Feedback in Sprach-Trainer, Entspannung, Intervall-Timer |
| `READ_EXTERNAL_STORAGE` | Backup-Import, Foto-Auswahl |
| `WRITE_EXTERNAL_STORAGE` | Backup-Export, PDF/GPX-Export |

---

## 📲 Installation

| Anforderung | Details |
|---|---|
| Android | 7.0 (API 24) oder höher |
| Internet | Für Karte, Wetter, Blog-Inhalte, WebDAV-Sync |
| GPS | Optional – für Navigation, Tempo-Trainer, Nasenarbeit, Kompass-Himmelsberechnung |
| ORS-API-Key | Optional – eigener Schlüssel für Routing (kostenlos bei openrouteservice.org) |

---

## 📥 Download

Die App ist **ausschließlich über den Google Play Store** verfügbar:

▶ [play.google.com/store/apps/details?id=com.hundewildnis.app](https://play.google.com/store/apps/details?id=com.hundewildnis.app)

Eine F-Droid-Veröffentlichung befindet sich in Vorbereitung. Die App ist kostenlos, solange sie sich durch freiwillige Unterstützung finanzieren kann.

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
- Astronomische Berechnungsgrundlage: Jean Meeus, *Astronomical Algorithms*

---

<div align="center">

Entwickelt mit 💚 für Hunde und ihre Menschen

[🌐 wildnislehrer.com](https://wildnislehrer.com) · [📱 Play Store](https://play.google.com/store/apps/details?id=com.hundewildnis.app) · MIT-Lizenz

</div>
