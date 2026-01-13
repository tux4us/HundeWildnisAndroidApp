# HundeWildnis App 🐕🌲

Eine native Android-App für [wildnislehrer.com](https://wildnislehrer.com) - Dein mobiler Begleiter für Hundeerziehung und Wildnispädagogik.

![Android](https://img.shields.io/badge/Android-24%2B-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-100%25-purple.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

## 📱 Über die App

HundeWildnis bietet direkten Zugriff auf alle Inhalte von wildnislehrer.com in einer übersichtlichen, mobiloptimierten App. Perfekt für unterwegs beim Training mit deinem Hund oder beim Lernen in der Natur.

## ✨ Features

- 📝 **Blog** - Die 20 neuesten Blogbeiträge mit "Weitere laden"-Funktion
- 📄 **Seiten** - Alle Seiten hierarchisch organisiert und übersichtlich dargestellt
- 🎓 **Onlinekurse** - Direkter Zugriff auf alle Kursinhalte (in Arbeit)
- 🔍 **Integrierte Suche** - Durchsuche alle Bereiche schnell und einfach
- 🌓 **Dark/Light Mode** - Wechsle zwischen heller und dunkler Darstellung
- 🔄 **Pull-to-Refresh** - Aktualisiere Inhalte durch Herunterziehen
- 📱 **Responsive WebView** - Mobile-optimierte Darstellung aller Inhalte
- ✅ **100% ohne Google-Dienste** - Funktioniert komplett offline-fähig

## 🖼️ Screenshots

<img width="1080" height="2400" alt="Screenshot_20260111-224109_HundeWildnis" src="https://github.com/user-attachments/assets/1ef3b084-51e8-4865-81cb-f78e2d54fbb2" />

<img width="1080" height="2400" alt="Screenshot_20260111-224153_HundeWildnis" src="https://github.com/user-attachments/assets/a57bf890-09c9-40d5-80d5-ff9b6f94c567" />

<img width="1080" height="2400" alt="Screenshot_20260111-224204_HundeWildnis" src="https://github.com/user-attachments/assets/c207d308-141d-4434-8008-024675c60848" />

<img width="1080" height="2400" alt="Screenshot_20260111-224323_HundeWildnis" src="https://github.com/user-attachments/assets/f9df627c-5635-4ffe-a091-d3301dc9152c" />

<img width="1080" height="2400" alt="Screenshot_20260111-224405_HundeWildnis" src="https://github.com/user-attachments/assets/bbba8a34-e2b5-4bd9-ad65-34c416b36823" />

<img width="1080" height="2400" alt="Screenshot_20260111-224423_HundeWildnis" src="https://github.com/user-attachments/assets/d0ff0ba2-20a5-4bb1-b03e-8e043a9d70ba" />

<img width="1080" height="2400" alt="Screenshot_20260111-224458_HundeWildnis" src="https://github.com/user-attachments/assets/2d3d3d46-874f-4f39-abaa-b8d3dd7c68a4" />

<img width="1080" height="2400" alt="Screenshot_20260111-224509_HundeWildnis" src="https://github.com/user-attachments/assets/97d9f7d6-f778-4df1-a6d5-61e7e1331f0d" />

<img width="1080" height="2400" alt="Screenshot_20260111-224520_HundeWildnis" src="https://github.com/user-attachments/assets/f47bc32e-c382-46d6-a8e5-c246b4d846a5" />

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
