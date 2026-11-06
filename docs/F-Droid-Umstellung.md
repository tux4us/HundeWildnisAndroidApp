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
- Anleitung für unerfahrene Nutzer: Button „Anleitung: Wetter-Zugang einrichten“ im Dialog „Dienste & Server“ (Warum, Schritte für OpenWeatherMap, Hinweise, Open-Meteo, Overpass)
- Quellenangabe in der Wetteransicht: „Weather data © OpenWeather“ bzw. „Daten: © Open-Meteo (CC BY 4.0)“
- Overpass: Standardserver `overpass-api.de` und `overpass.private.coffee`, eigener Server einstellbar,
  Serverwechsel bei 429/504, Sucheingabe wird maskiert
- Einheitliche Kennung (User-Agent) in allen Abrufen, Nominatim auf 1 Anfrage/Sekunde begrenzt
- Quellenangabe „© OpenStreetMap contributors“ auf den Karten
- Höhenmesser: kein Internetabruf mehr (Bright Sky/DWD entfernt). Das Barometer wird an der GPS-Höhe kalibriert, davor gilt die Standardatmosphäre (Anzeige „nicht kalibriert“)

## Aufräumen
- Entfernt: jsoup, Glide (Coil ersetzt es), Repository `mvn.0110.be`, `.idea`, `.artifacts`, ProGuard-Regeln für iText
- `dependenciesInfo` in Gradle ausgeschaltet (F-Droid)
- `local.properties.example` bereinigt; Play-Store-Links durch die Projektseite ersetzt
- Neu: `fastlane/metadata/android/…`, Entwurf `fdroid/com.hundewildnis.app.yml`

## Noch zu tun (nicht durch mich erledigt)
1. Build und alle Tests auf einem Gerät (insbesondere PDFs, Karte, Einstellungsdialog, Wetter mit eigener Adresse)
2. Datenschutzerklärung: Sie ist in der App eingebaut (Menü, Deutsch/Englisch; Quelle `privacy_content` in `values/` und `values-en/strings.xml`). Die Klartext-Fassungen `Datenschutzerklaerung` und `PrivacyPolicy.en.txt` im Repository entsprechen diesem Text. Für Play Console und F-Droid wird zusätzlich eine öffentlich erreichbare URL benötigt, z. B. die Raw-Datei im Repository oder eine Seite auf der Webseite. Änderungshinweise: `docs/Datenschutz-Anpassungen.md`.
3. Wetter: mit eigenem Schlüssel von OpenWeatherMap und mit Open-Meteo (Kundenadresse) auf einem Gerät testen; die Adressen des
   Open-Meteo-Kundenzugangs (Wetter, Luftqualität) stehen in deren Dokumentation und sind hier nicht vorbelegt. Höhenmesser testen (GPS-Fix abwarten,
   Kalibrierung des Barometers prüfen)
4. Rechtliche Prüfung der Lizenzumstellung (Mitwirkende Dritte? Dann deren Zustimmung nötig)
5. Tag mit freier Lizenz setzen, `fdroid build` lokal laufen lassen, dann Merge-Request bei fdroiddata


## Nachtrag 27.1 / 138: insecure-gradlew

- `gradle/wrapper/gradle-wrapper.properties` enthält jetzt `distributionSha256Sum` (Wert von der Gradle-Release-Seite übernommen, **bitte gegen `https://services.gradle.org/distributions/gradle-9.6.0-bin.zip.sha256` prüfen**).
- `gradle-wrapper.jar` weicht von der offiziellen Prüfsumme des 9.6.0-Wrappers ab. Neu erzeugen: `./gradlew wrapper --gradle-version 9.6.0 --gradle-distribution-sha256-sum <Hash>` (zweimal ausführen) und `sha256sum gradle/wrapper/gradle-wrapper.jar` mit der offiziellen `…-wrapper.jar.sha256` vergleichen.
- Sicherungsregeln (`backup_rules.xml`, `data_extraction_rules.xml`) sind im Manifest eingebunden und schließen die verschlüsselten Einstellungen aus.
- Signal-/X-Logos entfernt, Play-Store-Link durch Projektseite ersetzt.
