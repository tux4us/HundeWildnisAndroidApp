<div align="center">

# 🐕 HundeWildnis App

**Mobiler Begleiter für professionelles Hundetraining, Wildnispädagogik und den Alltag mit dem Hund**

[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Version](https://img.shields.io/badge/Version-20.2%20Build%20114-C9A84C?style=flat-square)](#)
[![License](https://img.shields.io/badge/Lizenz-GPL--3.0--or--later-5C8A3C?style=flat-square)](LICENSE)
[![No Google Services](https://img.shields.io/badge/Google%20Services-keine-brightgreen?style=flat-square)](#-datenschutz--sicherheit)
[![F-Droid](https://img.shields.io/badge/F--Droid-in%20Vorbereitung-lightgrey?style=flat-square)](https://f-droid.org/)

> 🔒 **100 % Datenschutz** · Kein Tracking · Kein Datenabfluss · Keine Werbung · Keine Google Services · DSGVO-konform

**Eine 100 %ige Eigenentwicklung der HundeWildnisSchule Manuela Ulbricht und Peter Jörg Ulbricht GbR, Freital**

[🌐 Website](https://wildnislehrer.com/hundewildnisapp/) · [📖 Funktionsübersicht](#-funktionsübersicht)

</div>

---

## 📑 Inhaltsverzeichnis

- [Über die App](#-über-die-app)
- [Datenschutz & Sicherheit](#-datenschutz--sicherheit)
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
- Freie Software unter **GPL-3.0-or-later**, Quelltext öffentlich; Veröffentlichung über Google Play Store und F-Droid (in Vorbereitung)

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

## 🧭 Funktionsübersicht

Die App gliedert sich in drei Bedienebenen: das **Hamburger-Menü** (App-weite Systemfunktionen), die **Startseite** mit sechs Kachelgruppen (die eigentlichen Funktionsmodule) und die **untere Navigationsleiste** als Footer (Wechsel zwischen den vier Hauptbereichen sowie externe Kanäle).

---

### A · Hamburger-Menü

Erreichbar über das Drei-Punkte-Symbol rechts oben in der Toolbar – auf der Startseite ebenso wie in Blog, Seiten und Online-Kursen.

| Eintrag | Funktion |
|---|---|
| 🔁 Aktualisieren | Lädt die Blog-Vorschau bzw. die aktuell sichtbare Liste neu |
| ⚙️ Einstellungen | Öffnet Dialog mit: Hintergrund-Updates ein/aus · Beschränkung auf WLAN · Benachrichtigungen ein/aus · eigener OpenRouteService-API-Key (eingeben/testen/löschen) |
| 📦 Backup erstellen | Vollständige Sicherungsdatei in `Downloads/HundeWildnis_Backups`, anschließend Teilen-Angebot |
| 📥 Backup importieren | Dateiauswahl, Prüfung, Abfrage der Merge-Art (Hinzufügen/Ersetzen/Zusammenführen) vor der Wiederherstellung |
| 📖 Anleitung zur Nutzung | Kurzanleitung zu den wichtigsten Funktionen |
| 🛡️ Berechtigungen der App | Erläuterung der angeforderten Systemberechtigungen |
| 📜 Datenschutzerklärung | Vollständige Datenschutzerklärung von wildnislehrer.com, in-app |
| 📎 Impressum | Anbieterkennzeichnung |
| ℹ️ Über die App | Versionsnummer, Website- und Kontakt-Link |

**Zum ORS-Routing-Key:** Für die Routenberechnung trägst du in den Einstellungen deinen eigenen, kostenlosen OpenRouteService-Schlüssel ein (openrouteservice.org). Er wird verschlüsselt gespeichert. Bei Builds, die ein Entwickler mit einem eigenen Schlüssel in `local.properties` erstellt hat, dient dieser nur als Voreinstellung; F-Droid-Builds enthalten keinen Schlüssel. Ablauf der Routenberechnung: zunächst vollständige Route mit Höhenprofil/Oberflächen-/Wegtyp-Daten → bei Fehlschlag Route nur mit Höhendaten → letzter Fallback gestrichelte Luftlinie.

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
| 🐾 Nasenarbeit | Regelbasiertes Expertensystem, 3 Disziplinen – **siehe Detailabschnitt unten** | Sechsstufiger Beratungs-Wizard (Wetter → Gelände → Boden → Trainingsstand → Empfehlung) · Vier-Schichten-Analyse · Kartenwerkzeug · Sitzungsdokumentation · Statistik, PDF-/CSV-Export je Hund |
| 🎾 Spiel und Spaß | Empfehlungssystem für spielerische Aktivitäten | Beratungs-Wizard (Ort → Gelände → Ausrüstung → Spielinhalt → Empfehlung) · Analyse · Vorschlag · Sitzungsdokumentation · Statistik, PDF-/CSV-Export je Hund |

##### Nasenarbeit im Detail

**Zweck:** Nasenarbeit ist körperlich und mental anspruchsvoll; falsch dosiert (zu große Distanz, ungeeignetes Wetter, zu hohe Anforderung für den Trainingsstand) führt sie zu Frustration oder Überforderung statt zu Lernerfolg. Das Modul nimmt die Einschätzung „Ist die geplante Übung heute für meinen Hund angemessen?" ab und macht sie nachvollziehbar, statt sie dem Gefühl zu überlassen.

Beim ersten Aufruf erscheint ein Einwilligungsdialog, der erläutert, dass GPS-Standort (temporär, nicht gespeichert) und automatisch abgerufene Wetterdaten für die Empfehlung benötigt werden.

Der Beratungs-Wizard führt durch fünf Bildschirme mit Fortschrittsanzeige:

1. **Wetter** – automatischer Abruf von Temperatur, Windstärke (Beaufort), Windrichtung relativ zur geplanten Suchlinie, Luftfeuchtigkeit und WMO-Wettercode; alle Werte manuell korrigierbar
2. **Gelände** – Auswahl einer Sequenz aus Topografie-Typen (eben, Steigung, Kuppe, Hanglage, Mulde, Felsabbrüche u. a.) und Umgebungstypen (offen, Wald, Grünland, urban, Industrie u. a.); wahlweise Übernahme direkt aus dem Kartenwerkzeug
3. **Boden** – je Geländeabschnitt Bodentyp (21 Varianten) und Bodenzustand (trocken bis Schnee in drei Stärken) einzeln festlegbar
4. **Trainingsstand** – bisherige Leistung je Disziplin, Zeitpunkt der letzten Einheit und aktuelle Tagesverfassung des Hundes
5. **Empfehlung** – Ergebnis in sechs Abschnitten: empfohlene Länge/Fläche mit Schwierigkeitsgrad und geschätztem Spuralter (2 Min. bis 24 h, je nach Bodenfeuchte/Wind) · kontextabhängige Vorbereitungscheckliste (z. B. Pfotenschutz bei Frost/Schnee, Trinkpausen ab 25 °C) · Eignungs-Ampel für alle drei Disziplinen gleichzeitig (✅/⚠️/❌) · Signal-Tabs „OK/Warnung/Stopp/Wetter" mit Begründungen · Nachbereitungshinweise inkl. automatischem Steigerungsvorschlag · Rücksetzen-Funktion für einen erneuten Durchlauf

**Automatische Anpassung an Erholung und Tagesform:** Eine bereits am selben Tag absolvierte Einheit reduziert die empfohlene Distanz automatisch um 30 %, eine Einheit innerhalb der letzten 14 Tage um 15 %; hohe Tagesmotivation erhöht die Empfehlung dagegen um 10–20 %.

**Kartenwerkzeug** (separat aufrufbar): Strecke oder Suchfläche direkt auf einer OSM-Karte einzeichnen; die App berechnet automatisch Distanz/Fläche und liest zusätzlich Umgebungs-, Boden- und Topografiedaten der betroffenen Abschnitte aus offenen Kartendaten (Overpass) aus, um Schritt 2 und 3 des Wizards vorauszufüllen. Einzelne Abschnitte lassen sich auf reale Wege einrasten (Snap-to-Way).

**Level-System:** Je Disziplin steigt der Trainingsstand automatisch mit der Zahl absolvierter Sitzungen (Anfänger 0–49, Fortgeschritten 50–99, Erfahren 100–499, Experte 500+) und bestimmt damit die maximal empfohlene Distanz/Fläche und den Schwierigkeitsgrad.

#### Gruppe 3 – Outdoorwerkzeuge
| Kachel | Kernfunktionen | Unterfunktionen |
|---|---|---|
| 📝 Tagesnotizen | Notizen mit Text, Foto, Geodaten | Automatischer Wetter-/GPS-Abruf · Kamera/Galerie · PDF-/GPX-Export, Teilen, Löschen · Historie |
| 🌦️ Revier-Wetter | Umfassender Wetterbericht (OpenWeatherMap oder Open-Meteo, jeweils mit eigenem Zugang; ohne Zugang erscheint ein Hinweis). Mit OpenWeatherMap (kostenloser Tarif) entfallen UV, Sonnenscheindauer, Pollen und die Gewitter-Vorhersage | Temperatur/Wind/Luftdruck/UV · Pfotenwetter-Index · Tageslicht/Mondphase · 24h-/7-Tage-Vorschau · Luftqualität/Pollen · Wetter-Favoriten |
| ⛈️ Gewitterwarner | Zwei Funktionen: Blitz-Donner-Distanzrechner (offline) + Gewitterrisiko-Vorhersage | Zeitmessung Blitz→Donner, Ampel-Einstufung, Trend/Näherungsgeschwindigkeit/ETA bei Folgemessungen |
| 🗺️ Revier-Navigation | OSM-Karte, POI-Suche, Routing – **siehe Detailabschnitt unten** | 34 POI-Kategorien (3 km, Gewitterschutz 5 km) mit Untertyp-Erkennung und Flächen-/Wege-Umrissen · Routing Fuß/Fahrrad/Auto · Live-Rerouting · Favoriten · Zoom-Buttons, umschaltbares Bedienfeld |
| 🧭 Kompass | Digitale Orientierung + Himmelsberechnung – s. Abschnitt „Was ist neu" | Sensor-Fallback-Kette · Sonne/Mond/Polaris-Anzeige inkl. Großer-Wagen-Uhrzeiger, alle 30 s aktualisiert, vollständig offline berechnet |
| ⛰️ Höhenmesser | GPS- und Barometer-Höhe | Automatische Kalibrierung über GPS-Startwert · QNH-Abruf für höhere Genauigkeit |

##### Revier-Navigation im Detail

**Zweck:** Ersetzt eine kommerzielle Navigations-App für die konkreten Bedürfnisse beim Gassigehen und Wandern mit Hund – wo ist der nächste Wasserzugang, wo herrscht Leinenzwang, wo ist ein Tierarzt in der Nähe, wie komme ich dorthin – ohne Google-Konto und ohne dass der eigene Standort an einen kommerziellen Anbieter übermittelt wird.

- **Kartendarstellung:** OpenStreetMap-Kachelkarte (OSMDroid), eigener Standort als Marker, feste Zoom-Buttons zusätzlich zur Zwei-Finger-Geste, Kompass-Ausrichtung
- **POI-Suche:** Auswahl einer von 34 Kategorien (Kategorienraster mit Icon und Farbe) → automatische Abfrage aller passenden Punkte, Wege und Flächen im Umkreis von 3 km (Gewitterschutz 5 km) über die Overpass-API; Ergebnisse erscheinen sowohl als Karten-Overlay als auch als sortierte Liste (nach Entfernung)
- **Zwei-Tap-Bedienlogik:** erster Tap auf einen Treffer öffnet ein Infofenster mit Entfernung, Richtung, verfügbaren Zusatzangaben (Adresse, Telefon, Öffnungszeiten, Leinenpflicht) sowie Untertyp-Bezeichnung, wo vorhanden; zweiter Tap bzw. eigener Button startet die Navigation dorthin
- **Flächen und Wege als echte Umrisse:** Naturschutzgebiete, Wanderwege oder Zeltplätze werden nicht als einzelner Punkt, sondern als tatsächliche Fläche (Polygon) bzw. Linie (Polyline) auf der Karte eingezeichnet
- **Textsuche:** freie Ortssuche zusätzlich zur Kategoriensuche
- **Favoriten:** beliebige Punkte dauerhaft speichern, frei sortierbar per Drag-and-Drop, als eigene Kategorie im Raster
- **Routing:** drei Profile – Fuß, Fahrrad (Geschwindigkeitsschätzung realistisch auf 10 km/h für die Begleitung eines Hundes gedeckelt), Auto; die Route wird entlang des tatsächlichen Wegverlaufs berechnet (nicht Luftlinie) und zeigt den Wegbelag farblich codiert an; weicht man während der laufenden Navigation mehr als 30 m von der Route ab, wird automatisch neu berechnet
- **Live-Standort teilen:** der aktuelle Standort lässt sich jederzeit als Link über die Android-Teilen-Funktion versenden

**Untertyp-Erkennung (neu in 20.2):** Sammelkategorien wie „Notfall" fassen sehr unterschiedliche Objekte zusammen; die App leitet automatisch eine präzise Bezeichnung ab (z. B. „Defibrillator (AED)", „Hydrant", „Rettungswache", „Notruftelefon", „Arztpraxis") statt jeden Treffer nur „Notfall" zu nennen – im Ernstfall entscheidend, um schnell den richtigen Punkt zu erkennen.

#### Gruppe 4 – Hund, Sicherheit und Notfall
| Kachel | Kernfunktionen | Unterfunktionen |
|---|---|---|
| 🐕 Stammdaten | Beliebig viele Hundeprofile | Basis-/Identifikations-/Gesundheits-/Kontaktdaten · Timelinetracker (gemeinsame Lebenszeit mit dem Hund inclusive aller mit der App aufgenommenenen Aktivitäten und Situationen) · Läufigkeitstracker · 6 Wesensmerkmal-Skalen, 11 Schnellprofile · Export (Text/QR/PDF/GPX) · eigenständige Notfallmeldung ab Profilkarte |
| ❤️ Pulscheck | Geführte Puls-/Atemfrequenzmessung | 15-s-Countdowns mit automatischer Hochrechnung · Bewertung nach Schulterhöhe · Kapillarfüllzeit, Schleimhautfarbe, Bewusstsein, Belastungsszenario · Übernahme in Notfallmeldung · Verlauf, CSV-Export |
| 🔍 Zeckenlupe | Digitale Lupe (Camera2 API) | 3 Filter (Wärmebild/Invertierung/Hoher Kontrast) · Antipp-Fokus · Freeze-Frame · Speichern in Galerie |
| 🌿 Gefahrenpflanzen | Offline-Datenbank, 56 Giftpflanzen | Saisonale Warnung · Textsuche, Toxizitäts-Filter, Saisonal-Umschalter · eigene Fotos je Pflanze · Verknüpfung mit Notfall-Assistent |
| 📍 Standortfreigabe | WebDAV-basierte Geräteortung – **siehe Detailabschnitt unten** | Bewegungsbasierte Uploads mit Heartbeat-Fallback · Akkustand-Anzeige · frei wählbares Geräte-Label · Karte mit Stale-Markierung · Routing zum Standort |
| 🚨 NOTFALL | Vierstufiger Erste-Hilfe-Assistent – **siehe Detailabschnitt unten** | Hund wählen → Unfall/Erkrankung → 17 Notfalltypen → Symptom-Check → Anleitung mit HLW-Timer und Countdown-Timer je nach Bedarf · Notfallmeldung mit PDF-Export |

##### Standortfreigabe im Detail

**Zweck:** Beim gemeinsamen Wandern oder Trainieren in unübersichtlichem Gelände, bei getrennten Gruppen oder wenn eine Person mit dem Hund allein unterwegs ist, erlaubt diese Funktion es vertrauten Personen (z. B. Trainingspartnern, Familienmitgliedern), den eigenen Standort in Echtzeit mitzuverfolgen – etwa um sich im Wald wiederzufinden, im Ernstfall schnell Hilfe schicken zu können, oder um bei einer verabredeten Route zu sehen, wie weit jemand ist. Die Übertragung läuft ausschließlich über einen selbst gewählten WebDAV-Server, nicht über einen Standort-Dienst eines Drittanbieters.

- Ohne hinterlegte WebDAV-Zugangsdaten ist die Funktion gesperrt und verlinkt direkt zu den WebDAV-Einstellungen
- Nach Aktivierung läuft im Hintergrund ein Dienst, der den eigenen Standort automatisch überträgt und die Standorte anderer, ebenfalls freigebender Geräte abruft und auf einer Karte anzeigt
- **Bewegungsbasierte Übertragung (überarbeitet in 20.2):** ein Upload erfolgt alle 60 Sekunden nur bei tatsächlicher Bewegung von mehr als 15 Metern; bei größerer Bewegung (> 20 m) sofort; im Stillstand nur alle 5 Minuten ein kurzes Status-Signal (Heartbeat), damit andere weiterhin erkennen können, dass das Gerät online ist – das schont spürbar den Akku, ohne dass bei aktiver Bewegung Genauigkeit verloren geht
- Jedes angezeigte Gerät zeigt zusätzlich seinen aktuellen **Akkustand** an, damit rechtzeitig erkennbar ist, wenn ein Gerät bald offline gehen könnte
- Standorte, die seit mehr als 10 Minuten nicht aktualisiert wurden, werden auf der Karte grau (veraltet) dargestellt
- Direktes Routing zu einem freigegebenen Standort ist mit einem Tap möglich (Fuß/Fahrrad/Auto)
- Jedes Gerät kann sich ein eigenes, frei wählbares Label geben (Standard: Gerätemodell), einzelne Geräte-Einträge lassen sich vom Server entfernen
- Beim ersten Aktivieren erscheint ein Hinweis, die App von der Akkuoptimierung des Systems auszunehmen, damit die Freigabe auch bei gesperrtem Bildschirm zuverlässig weiterläuft

##### NOTFALL-Assistent im Detail

**Zweck:** In einer akuten Notfallsituation mit dem Hund fehlt oft die Ruhe, sich an die richtigen Erste-Hilfe-Schritte zu erinnern oder alle notwendigen Angaben für einen Tierarzt oder Helfer zusammenzustellen. Der Assistent führt strukturiert durch die Einschätzung der Lage, liefert konkrete Handlungsanweisungen und erstellt parallel eine vollständige, teilbare Notfallmeldung.

**Ablauf in vier Schritten:**
1. **Hund auswählen** aus den vorhandenen Stammdaten (bei mehreren Hunden per Auswahldialog)
2. **Hauptkategorie wählen:** Unfall oder Erkrankung
3. **Konkrete Notfallart wählen** – 9 Unfalltypen (u. a. Verkehrsunfall, Blutungen/Wunden, Knochenbruch/Lahmheit, Ertrinken, Unterkühlung, Hitzschlag, Fremdkörper verschluckt, Vergiftung mit bekannter Substanz) und 8 Erkrankungstypen (u. a. Bewusstlosigkeit/Kollaps, Krampfanfall, Magendrehung, Atemnot, Blutungen ohne Unfall, Erbrechen/Durchfall, Vergiftung mit unbekannter Substanz, Allergische Reaktion)
4. **Symptom-Schnellcheck**, sofern für die gewählte Notfallart hinterlegt (7 der 17 Typen): kurze Ja/Nein-Fragen, die die Dringlichkeit direkt anhand der Antwort einschätzen (z. B. bei Hitzschlag: „Körpertemperatur über 40 °C?" wird als kritisch gewertet, wenn mit Ja beantwortet)

**Ergebnis – die Erste-Hilfe-Anleitung** enthält für jeden der 17 Notfalltypen: eine Liste typischer Symptome, nummerierte Sofortmaßnahmen in der richtigen Reihenfolge, getrennte Was-tun- und Was-nicht-tun-Listen sowie einen Hinweis zum sicheren Transport des Hundes. Je nach Notfallart wird zusätzlich automatisch eingeblendet:
- der **HLW-Timer** (bei Bewusstlosigkeit und Ertrinken) – automatisches Metronom für Herz-Lungen-Wiederbelebung: 30 Kompressionen in 15 Sekunden (120 pro Minute) gefolgt von 2 Beatmungen in 5 Sekunden, danach automatischer Neustart, mit Erinnerung alle 2 Minuten, den Puls erneut zu prüfen
- ein **einfacher Countdown-Timer** (z. B. bei Hitzschlag zur Kühlungsdauer, bei Krampfanfall zur Beobachtungszeit)

**Notfallmeldung:** Aus der Anleitung heraus lässt sich direkt eine Notfallmeldung anlegen, die auch unabhängig – etwa direkt von der Stammdaten-Profilkarte aus – aufgerufen werden kann, wenn keine geführte Anleitung benötigt wird. Sie übernimmt automatisch Symptomtitel und Dringlichkeitsstufe als Notiztext, erlaubt das Erfassen des aktuellen GPS-Standorts per Tastendruck sowie das Anhängen von Fotos, und erzeugt daraus ein PDF-Dokument mit:
- Symptom- und Zusatzangaben sowie GPS-Standort inklusive anklickbarem OpenStreetMap-Kartenlink
- vollständigen Stammdaten des Hundes (Name, Rasse, Geburtsdatum, Geschlecht, Kastrationsstatus, Gewicht, Schulterhöhe, Körperlänge, Chipnummer, Impfstatus, Vorerkrankungen, Besitzer)
- bei intakten, unkastrierten Hündinnen automatisch dem aktuellen Läufigkeitsstatus inklusive Warnhinweis, falls die Hündin gerade läufig ist – eine für Tierärzte relevante Zusatzinformation (u. a. für Narkose- und Blutungseinschätzung)
- allen angehängten Fotos

Die ausführliche Erste-Hilfe-Anleitung selbst wird bewusst nicht vollständig ins PDF übernommen (sie bleibt während des Notfalls in der App einsehbar); Tierarzt-Kontaktdaten aus den Stammdaten werden aktuell ebenfalls nicht automatisch übernommen und können bei Bedarf im freien Notizfeld ergänzt werden.

Das fertige PDF lässt sich mit einem Tap über die Android-Teilen-Funktion an Tierarzt, Tierrettung oder Begleitpersonen weitergeben; ein Begleittext sorgt dafür, dass auch Empfänger ohne PDF-Anwendung die wichtigsten Angaben direkt lesen können. Alle erstellten Meldungen bleiben in einer Verlaufshistorie erhalten.

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
| Wetter | Anbieter-Auswahl in „Dienste & Server“: OpenWeatherMap (eigener Schlüssel) oder Open-Meteo (eigene Adresse/Schlüssel); kein vorbelegter Dienst; Bright Sky (DWD) im Höhenmesser |
| Gelände / POI | Overpass API (OpenStreetMap) |
| Astronomische Berechnung | Eigene Implementierung nach Jean Meeus (`SkyCalculator`) |
| Diagramme | MPAndroidChart |
| Kamera | Camera2 API |
| PDF | Eigene Ausgabe auf Basis von `android.graphics.pdf.PdfDocument` (keine Bibliothek) |
| Bilder | Coil |
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
| ORS-API-Key | Für Routing erforderlich – eigener Schlüssel (kostenlos bei openrouteservice.org) |

---

## 📥 Download

Die App ist im Google Play Store verfügbar; eine Veröffentlichung auf F-Droid ist in Vorbereitung:

▶ [play.google.com/store/apps/details?id=com.hundewildnis.app](https://play.google.com/store/apps/details?id=com.hundewildnis.app)


---

## 🤝 Mitmachen

Issues und Pull Requests sind willkommen. Bitte kontaktiert uns gern.

- 📬 [wildnislehrer.com/kontakt](https://wildnislehrer.com/informationen/kontakt/)

---

## 📄 Lizenz

Die HundeWildnis App ist freie Software: Du kannst sie unter den Bedingungen der **GNU General Public License, Version 3 oder (nach deiner Wahl) jeder späteren Version** weitergeben und verändern. Den Lizenztext findest du in [`LICENSE`](LICENSE), Hinweise zu Schrift, Bibliotheken und externen Diensten in [`NOTICE.md`](NOTICE.md).

---

## 👥 Entwickler

**HundeWildnisSchule Manuela Ulbricht und Peter Jörg Ulbricht GbR** · Freital

- 🌐 [wildnislehrer.com](https://wildnislehrer.com)

---

## 🙏 Danksagungen

- Kartendaten © [OpenStreetMap Contributors](https://www.openstreetmap.org/copyright) (ODbL)
- Wetterdaten je nach gewähltem Anbieter: © [OpenWeather](https://openweathermap.org/) bzw. © [Open-Meteo](https://open-meteo.com/) (CC BY 4.0)
- Aktuelles Wetter (Bright Sky) © [Deutscher Wetterdienst](https://www.dwd.de/)
- Routing © [OpenRouteService](https://openrouteservice.org/) (HeiGIT gGmbH)
- Astronomische Berechnungsgrundlage: Jean Meeus, *Astronomical Algorithms*

---

<div align="center">

Entwickelt mit 💚 für Hunde und ihre Menschen

[🌐 wildnislehrer.com](https://wildnislehrer.com) · [📱 Play Store](https://play.google.com/store/apps/details?id=com.hundewildnis.app)

</div>
