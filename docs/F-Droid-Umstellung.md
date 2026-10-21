# Umstellung auf freie Lizenz / F-Droid – Änderungsübersicht

Stand: Version 24.1 (versionCode 131). **Nicht gebaut und nicht auf einem Gerät getestet.**
Geprüft wurde nur statisch: `PdfLayout.kt`, `AppSettings.kt`, `AppNetwork.kt` und `OverpassHelper.kt`
ließen sich mit einem Kotlin-Compiler gegen Android-Attrappen übersetzen; alle übrigen geänderten
Dateien auf Syntaxfehler. Ein echter Gradle-Build steht aus.

## Lizenz
- `LICENSE`: GPL-3.0-or-later (ersetzt die bisherige Nur-Lesen-Lizenz)
- `NOTICE.md`, `LICENSES/OFL-1.1.txt` (Schrift Black Ops One), README angepasst

## PDF (iText entfernt)
- Neu: `PdfLayout.kt` (auf Basis von `android.graphics.pdf.PdfDocument`)
- Umgestellt: `SoundLevelPDFGenerator`, `SpielSpassPDFGenerator`, `VoicePDFGenerator`,
  `NasenarbeitPDFGenerator`, `TimelineExport`, `TrainingExportManager`, `EmergencyActivity`, `DailyNotesActivity`
- Bekannte Unterschiede zu iText: Systemschrift statt Helvetica (Emoji werden, soweit das Gerät sie hat, mit gedruckt),
  Seitenumbrüche werden nach eigener Logik gesetzt. Alle 8 Exporte mit echten Daten ansehen!

## Dienste
- Regenradar (RainViewer, Leaflet/unpkg) entfernt: Code, Layout-Karte, Texte
- Kartenausschnitt im Nasenarbeit-PDF: aus der sichtbaren Karte gerendert (`NasenarbeitMapSnapshot`),
  keine eigenen Kachelabrufe mehr. Alte Sessions haben keinen Ausschnitt (dort erscheint nur der Link).
- Wetter: kein vorbelegter Dienst und kein Standardserver. Anbieter-Auswahl unter Einstellungen → Dienste & Server:
  OpenWeatherMap (eigener Schlüssel, kostenloser Tarif) oder Open-Meteo (eigene Serveradresse, ggf. Schlüssel).
  Ohne Anbieter und Zugang ruft die App nichts ab und zeigt einen Hinweis.
  Umsetzung: `WeatherProvider` (Abruf, 10 Minuten Zwischenspeicher, Fehlerarten), `OpenWeatherMapAdapter`
  (wandelt die OWM-Antworten in die Form um, mit der die Bildschirme arbeiten).
- Mit OpenWeatherMap (kostenloser Tarif) nicht verfügbar und deshalb ausgeblendet: UV-Index (aktuell, stündlich, Tagesmaximum),
  Sonnenscheindauer, Pollen, Gewitter-Vorhersage (CAPE), Höhendaten (Nasenarbeit-Karte fällt auf „Eben“ zurück).
  Näherungen: Vorhersage in 3-Stunden-Schritten stündlich interpoliert; keine Vergangenheitsdaten (Luftdruck-Trend im Biowetter
  fehlt, Tageswerte „heute“ gelten ab jetzt); Sonnenauf-/-untergang astronomisch berechnet (wenige Minuten Abweichung).
- Quellenangabe in der Wetteransicht: „Weather data © OpenWeather“ bzw. „Daten: © Open-Meteo (CC BY 4.0)“
- Overpass: Standardserver `overpass-api.de` und `overpass.private.coffee`, eigener Server einstellbar,
  Serverwechsel bei 429/504, Sucheingabe wird maskiert
- Einheitliche Kennung (User-Agent) in allen Abrufen, Nominatim auf 1 Anfrage/Sekunde begrenzt
- Quellenangabe „© OpenStreetMap contributors“ auf den Karten, DWD/Bright Sky im Höhenmesser

## Aufräumen
- Entfernt: jsoup, Glide (Coil ersetzt es), Repository `mvn.0110.be`, `.idea`, `.artifacts`, ProGuard-Regeln für iText
- `dependenciesInfo` in Gradle ausgeschaltet (F-Droid)
- `local.properties.example` bereinigt; Play-Store-Links durch die Projektseite ersetzt
- Neu: `fastlane/metadata/android/…`, Entwurf `fdroid/com.hundewildnis.app.yml`

## Noch zu tun (nicht durch mich erledigt)
1. Build und alle Tests auf einem Gerät (insbesondere PDFs, Karte, Einstellungsdialog, Wetter mit eigener Adresse)
2. Datenschutzerklärung auf wildnislehrer.com anpassen (die App verlinkt dorthin); `Datenschutzerklaerung` hier ist ein Entwurf
3. Wetter: mit eigenem Schlüssel von OpenWeatherMap und mit Open-Meteo (Kundenadresse) auf einem Gerät testen; die Adressen des
   Open-Meteo-Kundenzugangs (Wetter, Luftqualität) stehen in deren Dokumentation und sind hier nicht vorbelegt. Bright Sky (DWD,
   Höhenmesser) ist noch ein fest eingetragener Dienst – Nutzungsbedingungen prüfen oder ebenfalls konfigurierbar machen
4. Rechtliche Prüfung der Lizenzumstellung (Mitwirkende Dritte? Dann deren Zustimmung nötig)
5. Tag mit freier Lizenz setzen, `fdroid build` lokal laufen lassen, dann Merge-Request bei fdroiddata
