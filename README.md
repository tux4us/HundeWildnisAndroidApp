# HundeWildnis App 🐕🌲

Eine native Android-App für [wildnislehrer.com](https://wildnislehrer.com) - Dein mobiler Begleiter für Hundeerziehung und Wildnispädagogik.

![Android](https://img.shields.io/badge/Android-24%2B-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-100%25-purple.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

## 📱 Über die App

HundeWildnis bietet direkten Zugriff auf alle Inhalte von wildnislehrer.com in einer übersichtlichen, mobiloptimierten App. Perfekt für unterwegs beim Training mit deinem Hund oder beim Lernen in der Natur.

## ✨ Features

- 📝 **Blog** - Die neuesten Blogbeiträge mit "Weitere laden"-Funktion
- 📄 **Seiten** - Alle Seiten hierarchisch organisiert und übersichtlich dargestellt
- 🎓 **Onlinekurse** - Direkter Zugriff auf alle Kursinhalte (in Arbeit)
- 🔍 **Integrierte Suche** - Durchsuche alle Bereiche schnell und einfach
- 🧭 Integrierter Kompass - unterwegs immer die Orientierung behalten
- 📐 Integrierter Höhenmesser - immer wissen wie hoch man unterwegs ist
- 🌐 Standortbestimmung per GPS und der Möglichkeit den Standort unmittelbar über "Teilen" an Apps, Navigationsanwendungen und andere Empfänger zu übermitteln
- ☀️🌧️ standortgenaue aktuelle Wettersituation mit 
   24h Vorhersage:
   ✅ Temperatur alle 3 Stunden
   ✅ Wettersymbol (Emoji)
   ✅ Niederschlagswahrscheinlichkeit
   ✅ Horizontales Scrollen
   Niederschlagsradar:
   ✅ OpenStreetMap als Basis
   ✅ RainViewer Radar-Overlay (Echtzeit-Niederschlag)
   ✅ Marker auf deinem Standort
   ✅ Zoom- und Pan-Funktion
   ✅ Wetter-API (Open-Meteo)
   ✅ Kein API-Key nötig
   ✅ Aktuelle Temperatur, gefühlte Temperatur
   ✅ Luftfeuchtigkeit, Wind, Niederschlag
   ✅ Luftdruck, UV-Index
   ✅ Wetter-Icons (Emojis)
   ✅ Automatische Standorterkennung
- 🌓 **Dark/Light Mode** - Wechsle zwischen heller und dunkler Darstellung
- 🔄 **Pull-to-Refresh** - Aktualisiere Inhalte durch Herunterziehen
- 📱 **Responsive WebView** - Mobile-optimierte Darstellung aller Inhalte
- ✅ **100% ohne Google-Dienste** 

## 🎯 App-Bereiche

### 1. Blog
- Zeigt die 20 neuesten Blogbeiträge
- "Weitere Beiträge laden"-Button für ältere Artikel
- Anzeige von Titel, Datum und Textauszug
- Durchsuchbar nach Titel und Inhalt
- Öffnen der Blogbeiträge

### 2. Seiten
- Hierarchische Darstellung aller Webseiten
- **Ausnahme:** Onlinekurs-Seiten (eigener Bereich)
- Einrückung zur Visualisierung der Seitenstruktur
- Schneller Zugriff auf alle Inhalte

### 3. Onlinekurse
- Dedizierter Bereich für alle Onlinekurse
- Strukturierte Darstellung der Kursinhalte
- Direkter Zugriff auf Kursmaterialien

## 🛠️ Technologie-Stack

- **Sprache:** Kotlin 100%
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 36 (Android 15)
- **Build-System:** Gradle (Kotlin DSL)
- **UI:** Android Views mit ViewBinding
- **Architektur:** MVVM mit Kotlin Coroutines
- **HTML-Parsing:** Jsoup 1.17.2
- **Dependencies:** AndroidX Libraries (keine Google Play Services)

## 📦 Installation

### APK Download

1. Lade die neueste APK aus den [Releases](https://github.com/tux4us/HundeWildnisAndroidApp/releases) herunter
2. Aktiviere "Installation aus unbekannten Quellen" in den Android-Einstellungen
3. Installiere die APK auf deinem Gerät

### Offline-Fähigkeit
Geladene Inhalte werden im Speicher behalten und sind nach dem Laden ohne Internetverbindung verfügbar (bis zum Neustart der App).

## 📜 Lizenz
   
   Dieser Code ist **NUR ZUR ANSICHT** verfügbar.
   
   - ✅ Du darfst: Den Code lesen und studieren
   - ❌ Du darfst NICHT: Den Code nutzen, kopieren oder modifizieren. Verstöße werden rechtlich verfolgt und können Schadenersatzforderungen nach sich ziehen.
   
   Siehe [LICENSE](LICENSE) für Details.

## 🙏 Danksagungen

- [wildnislehrer.com](https://wildnislehrer.com) für die großartigen Inhalte
- [Jsoup](https://jsoup.org/) für das HTML-Parsing
- Die Android Community für AndroidX Libraries

## 🐛 Bekannte Probleme

- Blog-Parsing ist abhängig von der WordPress-Struktur (kann bei Theme-Änderungen angepasst werden müssen)
- Bilder werden aus dem Web geladen (keine Offline-Speicherung)

## 🗺️ Roadmap

- [ ] Offline-Speicherung von Artikeln
- [ ] Lesezeichen-Funktion
- [ ] Teilen-Funktion für Artikel
- [ ] Push-Benachrichtigungen für neue Blogbeiträge
- [ ] Download-Manager für Kursmaterialien
- [ ] Notizen-Funktion zu Artikeln
- [ ] Favoriten-System

## 💬 Support

Bei Fragen oder Problemen:
- Öffne ein [Issue](https://github.com/tux4us/HundeWildnisAndroidApp/issues)
- Kontaktiere mich über [eMail](tux4us@online.de)  

## 📧 Kontakt

**Entwickler:** tux4us  
**GitHub:** [@tux4us](https://github.com/tux4us)  
**Website:** [wildnislehrer.com](https://wildnislehrer.com) 

**Datenschutzerklärung:** (https://github.com/tux4us/HundeWildnisAndroidApp/blob/master/Datenschutzerklaerung)

---

**Hinweis:** Diese App ist ein offizielles Projekt von [wildnislehrer.com](https://wildnislehrer.com) und bietet mobilen Zugriff auf die öffentlich verfügbaren Inhalte von wildnislehrer.com.

Made with 🐕 and 🌲

## 🖼️ Screenshots

<img width="1080" height="2400" alt="Screenshot_20260116-152633_HundeWildnis" src="https://github.com/user-attachments/assets/7e08cff8-87af-440c-943c-4dcefb1ac0d6" />

<img width="1080" height="2400" alt="Screenshot_20260116-152648_HundeWildnis" src="https://github.com/user-attachments/assets/72b2fe5b-ec3b-4ca4-8a5a-756e17f460b3" />

<img width="1080" height="2400" alt="Screenshot_20260116-152702_HundeWildnis" src="https://github.com/user-attachments/assets/acfd3d3d-b6ec-4fdc-9e6b-094a3c7cb4d6" />

<img width="1080" height="2400" alt="Screenshot_20260116-152712_HundeWildnis" src="https://github.com/user-attachments/assets/c9042d4f-d279-42f1-814b-d097159f9e37" />

<img width="1080" height="2400" alt="Screenshot_20260116-152824_HundeWildnis" src="https://github.com/user-attachments/assets/14d0737d-527f-4a78-affd-8fbd67e61a99" />

<img width="1080" height="2400" alt="Screenshot_20260116-152856_HundeWildnis" src="https://github.com/user-attachments/assets/e4149eed-5568-4495-af85-ec3c98a8dd24" />

<img width="1080" height="2400" alt="Screenshot_20260116-152906_HundeWildnis" src="https://github.com/user-attachments/assets/6d8c5ae9-eb88-4341-bc6f-222e7891a0c0" />

<img width="1080" height="2400" alt="Screenshot_20260116-152920_HundeWildnis" src="https://github.com/user-attachments/assets/a4a9382f-6be7-4e74-a50d-0cd96f7469ce" />










