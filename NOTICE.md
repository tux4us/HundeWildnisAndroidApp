# HundeWildnis – Lizenzhinweise

**Lizenz der App:** GNU General Public License, Version 3 oder (nach Wahl) jede spätere Version
(SPDX: `GPL-3.0-or-later`). Der Lizenztext steht in der Datei `LICENSE`.

Copyright (c) 2026 tux4us (www.wildnislehrer.de)

## Eigene Inhalte

Die Texte, Grafiken und Audiodateien dieses Projekts (u. a. `res/raw/entspannung_music.mp3`,
`res/drawable/entspannung_bg.jpg`, `assets/wero_qr_code.jpg`) stammen vom Rechteinhaber und werden
ebenfalls unter `GPL-3.0-or-later` bereitgestellt.

## Schrift

| Datei | Schrift | Lizenz |
|---|---|---|
| `app/src/main/res/font/stencil.ttf` | Black Ops One | SIL Open Font License 1.1 (`LICENSES/OFL-1.1.txt`) |

## Bibliotheken (Auswahl, alle freie Lizenzen)

| Bibliothek | Lizenz |
|---|---|
| AndroidX, Material Components, WorkManager, Security-Crypto | Apache-2.0 |
| osmdroid | Apache-2.0 |
| Coil | Apache-2.0 |
| Gson | Apache-2.0 |
| MPAndroidChart | Apache-2.0 |
| ZXing / zxing-android-embedded | Apache-2.0 |

Die PDF-Ausgabe nutzt ausschließlich die Plattform-API `android.graphics.pdf.PdfDocument`
(Datei `PdfLayout.kt`); es ist keine PDF-Bibliothek eingebunden.

## Externe Dienste (nicht Teil der App)

Die App ruft nur auf Nutzeraktion hin Daten von diesen Diensten ab; Wetterdaten nur, nachdem der Nutzer einen Anbieter gewählt und seinen eigenen Zugang eingetragen hat. Die Dienste haben eigene
Nutzungsbedingungen, die jede Person selbst beachten muss. Die Adressen lassen sich in den
Einstellungen ändern bzw. eigene Schlüssel hinterlegen, soweit unten angegeben.

| Dienst | Zweck | Hinweis |
|---|---|---|
| OpenWeatherMap | Wetter, Vorhersage, Luftqualität (Anbieter-Auswahl) | Nur mit eigenem API-Schlüssel des Nutzers (kostenloser Tarif genügt). Datenlizenz ODbL; Quellenangabe „Weather data © OpenWeather“ wird in der Wetteransicht angezeigt. |
| Open-Meteo | Wetter, Luftqualität, Pollen, Höhe (Anbieter-Auswahl) | Kein vorbelegter Server: Der Nutzer trägt Adresse und ggf. Schlüssel seines eigenen Zugangs (kommerzielle Kundenadresse oder eigener Server) ein. Die kostenlose Adresse ist nur für nicht-kommerzielle Nutzung erlaubt. Datenlizenz CC BY 4.0. |
| OpenStreetMap-Kacheln | Kartenanzeige (osmdroid) | © OpenStreetMap contributors (ODbL); [Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/) |
| Nominatim | Ortssuche | [Nutzungsrichtlinie](https://operations.osmfoundation.org/policies/nominatim/) |
| Overpass API | Umgebungsdaten, Orte in der Nähe | Gemeinsam genutzte Server; Serveradresse einstellbar |
| OpenRouteService | Routen | eigener API-Schlüssel der Nutzerin oder des Nutzers |
