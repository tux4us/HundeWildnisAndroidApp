# WetterActivity bilingual machen (DE/EN)

Die `WeatherActivity` enthält derzeit viele hartkodierte deutsche Texte. Dieses Projekt extrahiert diese Texte in die Ressourcen-Dateien und bietet eine englische Übersetzung an.

## Zielsetzung
- Alle UI-relevanten Strings in `WeatherActivity.kt` lokalisieren.
- Fehlende Wetter-Ressourcen in `values-en/strings.xml` ergänzen.
- Die Biowetter-Texte vollständig übersetzen.

## Vorgeschlagene Änderungen

### Ressourcen-Management
#### [MODIFY] [strings.xml (DE)](file:///home/tuxus/Android/HundeWildnis/app/src/main/res/values/strings.xml)
- Hinzufügen aller neuen Keys, die aus der `WeatherActivity` extrahiert werden.
- Gruppierung nach Themen (Mondphase, Beaufort, Windrichtungen, Wetterwarnungen, UV-Tipps, Pfotenwetter, Pollen).

#### [MODIFY] [strings.xml (EN)](file:///home/tuxus/Android/HundeWildnis/app/src/main/res/values-en/strings.xml)
- Übersetzung aller neu hinzugefügten Keys.
- Vervollständigung bestehender Wetter-Keys, die bisher nur auf Deutsch vorlagen.

### Quellcode-Anpassungen
#### [MODIFY] [WeatherActivity.kt](file:///home/tuxus/Android/HundeWildnis/app/src/main/java/com/hundewildnis/app/WeatherActivity.kt)
- Ersetzen aller hartkodierten Strings durch `getString(R.string.key)` oder `context.getString(R.string.key)`.
- Nutzung von `String.format` mit den neuen Ressourcen, wo Platzhalter benötigt werden.
- Lokalisierung von Datums- und Zeitformaten (z.B. Nutzung von `Locale.getDefault()`).

### Biowetter-Engine
- Überprüfung der verbleibenden deutschen Strings in `BiowetterEngine.kt` und deren Übertragung in die Ressourcen (die meisten sind bereits vorhanden, aber einige Ergänzungen könnten nötig sein).

## Verifizierungsplan
- Kompilieren der App und Starten der `WeatherActivity`.
- Wechseln der Systemsprache auf Englisch und Prüfung der korrekten Anzeige aller Texte.
- Stichprobenartige Prüfung der Mondphasen-Beschreibungen, Biowetter-Tipps und UV-Empfehlungen in beiden Sprachen.
- Prüfung der Formatierungen (z.B. "Uhr" im Englischen vs. Deutschen).
