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
- 📐 Integrierter Höhenmesser - unterwegs immer wissen wie hoch man unterwegs ist
- 🌐 Standortbestimmung per GPS und der Möglichkeit den Standort unmittelbar über "Teilen" an Apps, Navigationsanwendungen und andere Empfänger zu übermitteln
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

<img width="1080" height="2400" alt="Screenshot_20260114-221558_HundeWildnis" src="https://github.com/user-attachments/assets/bbd5ced7-b174-4fb5-84c8-24ed2bd822b9" />

<img width="1080" height="2400" alt="Screenshot_20260114-221750_HundeWildnis" src="https://github.com/user-attachments/assets/bb7ab4b8-b1bb-4376-8928-d31e13f91a2f" />

<img width="1080" height="2400" alt="Screenshot_20260114-221802_HundeWildnis" src="https://github.com/user-attachments/assets/0c1e1ad6-baf3-42d4-b136-2f3241596275" />

<img width="1080" height="2400" alt="Screenshot_20260114-221812_HundeWildnis" src="https://github.com/user-attachments/assets/e36a2b6e-b185-4ba0-9dab-bf6bae8582a8" />

<img width="1080" height="2400" alt="Screenshot_20260114-221827_HundeWildnis" src="https://github.com/user-attachments/assets/beeac958-b4b2-4ab1-a46b-fe38a412de3f" />

<img width="1080" height="2400" alt="Screenshot_20260114-221916_HundeWildnis" src="https://github.com/user-attachments/assets/d827de2f-8b26-4441-bd42-852af6902e87" />

<img width="1080" height="2400" alt="Screenshot_20260114-221930_HundeWildnis" src="https://github.com/user-attachments/assets/8cae2bc6-9dc1-4349-81c9-db4c0547215f" />

<img width="1080" height="2400" alt="Screenshot_20260114-222300_HundeWildnis" src="https://github.com/user-attachments/assets/d26929f2-904b-4018-8458-3e2f21b984b1" />

<img width="1080" height="2400" alt="Screenshot_20260114-222318_HundeWildnis" src="https://github.com/user-attachments/assets/d3352961-883b-4d7b-9d21-42a140bbbfc5" />

<img width="1080" height="2400" alt="Screenshot_20260114-222353_HundeWildnis" src="https://github.com/user-attachments/assets/3a396fa6-9d97-4d8f-b38a-7d589bfd162e" />

<img width="1080" height="2400" alt="Screenshot_20260114-222402_HundeWildnis" src="https://github.com/user-attachments/assets/782adc7e-4255-408c-8bf9-8a26f9da1c3c" />

<img width="1080" height="2400" alt="Screenshot_20260114-222413_HundeWildnis" src="https://github.com/user-attachments/assets/e0529fcd-484f-415d-ac43-ecbc7015f22f" />

<img width="1080" height="2400" alt="Screenshot_20260114-222427_HundeWildnis" src="https://github.com/user-attachments/assets/adaacfba-d9c5-47bf-99ec-350d0ac8b9e5" />

