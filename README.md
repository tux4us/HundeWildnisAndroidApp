# HundeWildnis App 🐕🌲

Eine native Android-App für [wildnislehrer.com](https://wildnislehrer.com) - Dein mobiler Begleiter für Hundeerziehung und Wildnispädagogik.

![Android](https://img.shields.io/badge/Android-24%2B-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-100%25-purple.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>HundeWildnisApp - Dein mobiler Begleiter</title>
    <style>
        /* === FARBPALETTE === */
        :root {
            --primary-green: #8fad6f;
            --dark-green: #2d5016;
            --bg-dark: #1a1a1a;
            --card-bg: #2d2d2d;
            --text-light: #ffffff;
            --text-muted: #999999;
            --accent: #ff9800;
        }

        /* === CONTAINER === */
        .hundewildnis-app-container {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            max-width: 1000px;
            margin: 0 auto;
            padding: 20px;
            background: linear-gradient(135deg, var(--bg-dark) 0%, #2a2a2a 100%);
            color: var(--text-light);
            line-height: 1.6;
        }

        /* === HEADER === */
        .app-header {
            text-align: center;
            padding: 50px 20px;
            background: linear-gradient(135deg, var(--dark-green) 0%, var(--primary-green) 100%);
            border-radius: 20px;
            margin-bottom: 40px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.3);
        }

        .app-header h1 {
            font-size: 2.8em;
            margin: 10px 0;
            color: var(--text-light);
            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
        }

        .app-header .paw-emoji {
            font-size: 3.5em;
            margin-bottom: 10px;
            display: block;
            animation: bounce 2s infinite;
        }

        @keyframes bounce {
            0%, 100% { transform: translateY(0); }
            50% { transform: translateY(-10px); }
        }

        .app-header .subtitle {
            font-size: 1.3em;
            opacity: 0.95;
            margin-top: 15px;
            font-weight: 300;
        }

        /* === BADGES === */
        .info-badges {
            display: flex;
            gap: 12px;
            flex-wrap: wrap;
            justify-content: center;
            margin-top: 25px;
        }

        .badge {
            background: rgba(255, 255, 255, 0.25);
            backdrop-filter: blur(10px);
            color: white;
            padding: 10px 20px;
            border-radius: 25px;
            font-size: 0.95em;
            border: 1px solid rgba(255, 255, 255, 0.3);
            font-weight: 500;
        }

        /* === INTRO TEXT === */
        .intro-text {
            text-align: center;
            font-size: 1.15em;
            color: #cccccc;
            max-width: 800px;
            margin: 0 auto 50px;
            line-height: 1.8;
        }

        /* === FEATURE CARDS === */
        .features-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 25px;
            margin-bottom: 50px;
        }

        .feature-card {
            background: var(--card-bg);
            border-radius: 15px;
            padding: 35px;
            box-shadow: 0 5px 15px rgba(0,0,0,0.2);
            transition: all 0.3s ease;
            border: 2px solid transparent;
        }

        .feature-card:hover {
            transform: translateY(-8px);
            box-shadow: 0 15px 35px rgba(143, 173, 111, 0.4);
            border-color: var(--primary-green);
        }

        .feature-card .icon {
            font-size: 3.5em;
            margin-bottom: 20px;
            display: block;
        }

        .feature-card h3 {
            color: var(--primary-green);
            margin-top: 0;
            font-size: 1.6em;
            margin-bottom: 18px;
        }

        .feature-list {
            list-style: none;
            padding: 0;
            margin: 0;
        }

        .feature-list li {
            padding: 10px 0;
            padding-left: 30px;
            position: relative;
            font-size: 1.05em;
        }

        .feature-list li::before {
            content: "✓";
            position: absolute;
            left: 0;
            color: var(--primary-green);
            font-weight: bold;
            font-size: 1.3em;
        }

        /* === HIGHLIGHTS SECTION === */
        .highlights {
            background: linear-gradient(135deg, var(--card-bg) 0%, #3a3a3a 100%);
            border-radius: 15px;
            padding: 40px;
            margin-bottom: 40px;
            border-left: 6px solid var(--primary-green);
        }

        .highlights h2 {
            color: var(--primary-green);
            margin-top: 0;
            font-size: 2em;
            margin-bottom: 30px;
        }

        .highlights-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
            gap: 20px;
            margin-top: 25px;
        }

        .highlight-item {
            background: rgba(143, 173, 111, 0.15);
            padding: 25px;
            border-radius: 12px;
            text-align: center;
            border: 1px solid rgba(143, 173, 111, 0.3);
            transition: all 0.3s ease;
        }

        .highlight-item:hover {
            background: rgba(143, 173, 111, 0.25);
            transform: translateY(-3px);
        }

        .highlight-item .emoji {
            font-size: 2.5em;
            display: block;
            margin-bottom: 15px;
        }

        .highlight-item strong {
            display: block;
            margin-bottom: 8px;
            font-size: 1.1em;
        }

        .highlight-item p {
            margin: 0;
            color: var(--text-muted);
            font-size: 0.95em;
        }

        /* === TECH SECTION === */
        .tech-section {
            background: linear-gradient(135deg, #1a1a1a 0%, #2d2d2d 100%);
            border-radius: 15px;
            padding: 40px;
            margin-bottom: 40px;
            border: 1px solid rgba(143, 173, 111, 0.2);
        }

        .tech-section h2 {
            color: var(--primary-green);
            margin-top: 0;
            font-size: 2em;
            margin-bottom: 25px;
        }

        .tech-badges {
            display: flex;
            gap: 15px;
            flex-wrap: wrap;
            margin-top: 20px;
        }

        .tech-badge {
            background: rgba(143, 173, 111, 0.2);
            color: var(--primary-green);
            padding: 10px 18px;
            border-radius: 8px;
            font-size: 0.9em;
            border: 1px solid var(--primary-green);
            font-family: 'Courier New', monospace;
        }

        /* === CTA SECTION === */
        .cta-section {
            text-align: center;
            padding: 50px 30px;
            background: linear-gradient(135deg, var(--dark-green) 0%, var(--primary-green) 100%);
            border-radius: 20px;
            margin-top: 50px;
            box-shadow: 0 15px 40px rgba(0,0,0,0.4);
        }

        .cta-section h2 {
            color: white;
            margin-top: 0;
            font-size: 2.2em;
            margin-bottom: 20px;
        }

        .cta-section p {
            color: rgba(255,255,255,0.95);
            font-size: 1.2em;
            margin-bottom: 30px;
            max-width: 700px;
            margin-left: auto;
            margin-right: auto;
        }

        .cta-button {
            display: inline-block;
            padding: 18px 45px;
            background: white;
            color: var(--dark-green);
            text-decoration: none;
            border-radius: 50px;
            font-weight: bold;
            font-size: 1.15em;
            transition: all 0.3s ease;
            box-shadow: 0 5px 15px rgba(0,0,0,0.2);
        }

        .cta-button:hover {
            transform: scale(1.08);
            box-shadow: 0 8px 25px rgba(0,0,0,0.3);
            background: #f5f5f5;
        }

        /* === FOOTER === */
        .app-footer {
            text-align: center;
            padding: 40px 20px;
            color: var(--text-muted);
            border-top: 1px solid rgba(143, 173, 111, 0.2);
            margin-top: 50px;
        }

        .app-footer a {
            color: var(--primary-green);
            text-decoration: none;
            transition: color 0.3s;
        }

        .app-footer a:hover {
            color: var(--text-light);
        }

        /* === RESPONSIVE === */
        @media (max-width: 768px) {
            .app-header h1 {
                font-size: 2.2em;
            }
            
            .app-header .paw-emoji {
                font-size: 2.8em;
            }
            
            .features-grid {
                grid-template-columns: 1fr;
            }
            
            .highlights-grid {
                grid-template-columns: 1fr;
            }
            
            .cta-section h2 {
                font-size: 1.8em;
            }
        }
    </style>
</head>
<body>

<div class="hundewildnis-app-container">

    <!-- HEADER -->
    <div class="app-header">
        <span class="paw-emoji">🐾</span>
        <h1>HundeWildnisApp</h1>
        <p class="subtitle">Dein mobiler Begleiter für Hundeerziehung und Wildnispädagogik</p>
        <div class="info-badges">
            <span class="badge">📱 Android 7.0+</span>
            <span class="badge">🔓 Open Source</span>
            <span class="badge">🔒 100% ohne Google-Dienste</span>
            <span class="badge">🌍 Deutsch</span>
            <span class="badge">🆓 Kostenlos</span>
        </div>
    </div>

    <!-- INTRO TEXT -->
    <p class="intro-text">
        Die <strong>HundeWildnisApp</strong> bietet direkten Zugriff auf alle Inhalte von <strong>wildnislehrer.com</strong> 
        in einer übersichtlichen, mobiloptimierten App. Perfekt für unterwegs beim Training mit deinem Hund oder 
        beim Lernen in der Natur.
    </p>

    <!-- FEATURES GRID -->
    <div class="features-grid">
        
        <!-- Content & Community -->
        <div class="feature-card">
            <span class="icon">📰</span>
            <h3>Inhalte &amp; Community</h3>
            <ul class="feature-list">
                <li>HundeWildnis-Blogeinträge</li>
                <li>HundeWildnis-Webseiten</li>
                <li>Onlinekurse direkt abrufbar</li>
                <li>YouTube Channel</li>
                <li>X-Channel (Twitter)</li>
                <li>Signal 1:1 Trainer-Chat</li>
            </ul>
        </div>

        <!-- Navigation & Orientierung -->
        <div class="feature-card">
            <span class="icon">🗺️</span>
            <h3>Navigation &amp; Orientierung</h3>
            <ul class="feature-list">
                <li>GPS-Standortermittlung</li>
                <li>OpenStreetMap Karte</li>
                <li>Koordinaten anzeigen &amp; teilen</li>
                <li>Digitaler Kompass</li>
                <li>Genauigkeitsanzeige</li>
                <li>Barometrischer Höhenmesser</li>
            </ul>
        </div>

        <!-- Wetter & Outdoor Tools -->
        <div class="feature-card">
            <span class="icon">🌤️</span>
            <h3>Wetter &amp; Outdoor</h3>
            <ul class="feature-list">
                <li>Standortgenaue Wettervorhersage</li>
                <li>24h-Vorschau (alle 3h)</li>
                <li>Niederschlagsradar (Echtzeit)</li>
                <li>Temperatur, Luftfeuchtigkeit, Wind</li>
                <li>Luftdruck &amp; UV-Index</li>
                <li>Wetter-Icons &amp; Emojis</li>
            </ul>
        </div>

    </div>

    <!-- APP FEATURES -->
    <div class="highlights">
        <h2>🌟 Warum HundeWildnisApp?</h2>
        <div class="highlights-grid">
            <div class="highlight-item">
                <span class="emoji">🎯</span>
                <strong>Alles in einer App</strong>
                <p>Keine Navigation zwischen verschiedenen Tools mehr</p>
            </div>
            <div class="highlight-item">
                <span class="emoji">🐕</span>
                <strong>Für Hundebesitzer</strong>
                <p>Speziell für Outdoor-Aktivitäten mit Hund entwickelt</p>
            </div>
            <div class="highlight-item">
                <span class="emoji">💬</span>
                <strong>Direkter Support</strong>
                <p>1:1 Signal-Chat mit Wildnislehrern</p>
            </div>
            <div class="highlight-item">
                <span class="emoji">🔒</span>
                <strong>Datenschutz</strong>
                <p>Deine Daten bleiben bei dir - keine Google-Dienste</p>
            </div>
            <div class="highlight-item">
                <span class="emoji">🔄</span>
                <strong>Regelmäßige Updates</strong>
                <p>Ständig neue Features und Verbesserungen</p>
            </div>
            <div class="highlight-item">
                <span class="emoji">🌓</span>
                <strong>Dark/Light Mode</strong>
                <p>Augenschonendes dunkles Design optional</p>
            </div>
        </div>
    </div>

    <!-- DETAILED FEATURES -->
    <div class="highlights">
        <h2>📱 App-Bereiche im Detail</h2>
        <div class="feature-list" style="columns: 2; column-gap: 30px;">
            <li><strong>Blog:</strong> 20 neueste Beiträge, "Weitere laden"-Funktion, durchsuchbar</li>
            <li><strong>Seiten:</strong> Hierarchische Darstellung aller Webseiten mit Einrückung</li>
            <li><strong>Onlinekurse:</strong> Dedizierter Bereich mit strukturierten Kursinhalten</li>
            <li><strong>Videos:</strong> YouTube-Kanal-Integration, datenschutzfreundlich</li>
            <li><strong>Suche:</strong> Durchsuche alle Bereiche schnell und einfach</li>
            <li><strong>Tap-to-Refresh:</strong> Alle Inhalte über Aktualisierungsbutton immer auf dem Neuesten halten</li>
            <li><strong>X-Kanal:</strong> Folge uns auf X für interessante Inhalte</li>
            <li><strong>SIGNAL-Chat:</strong> Fragen, Hinweise, Hilfestellungen benötigt? Kontaktiere uns datenschutzkonform und privatsphäregeschützt über SIGNAL</li>
        </div>
    </div>

    <!-- COMING SOON -->
    <div class="highlights">
        <h2>🆕 Kommende Features</h2>
        <ul class="feature-list">
            <li>🏞️ <strong>POI-Finder</strong> für hundefreundliche Orte (Freilaufflächen, Wasserquellen, Tierärzte)</li>
            <li>🚫 <strong>Warnung</strong> vor Schutz- und Verbotsgebieten (wichtig für Bußgelder!)</li>
            <li>🗺️ <strong>Tracking</strong> absolvierte Touren archivieren und teilen</li>
            <li>📝 <strong>Touren-Tagebuch</strong> zum Festhalten eurer Abenteuer</li>
    </div>

    <!-- TECH STACK -->
    <div class="tech-section">
        <h2>🛠️ Technologie</h2>
        <p style="margin-bottom: 20px;">Modern, sicher und performant - die App setzt auf bewährte Android-Technologien:</p>
        <div class="tech-badges">
            <span class="tech-badge">Kotlin 100%</span>
            <span class="tech-badge">Android 7.0 - 15</span>
            <span class="tech-badge">AndroidX</span>
            <span class="tech-badge">MVVM + Coroutines</span>
            <span class="tech-badge">ViewBinding</span>
            <span class="tech-badge">Jsoup HTML Parser</span>
            <span class="tech-badge">OSMDroid Maps</span>
            <span class="tech-badge">Open-Meteo API</span>
        </div>
        <p style="margin-top: 25px; color: var(--primary-green);">
            ✅ Keine Google Play Services • ✅ Kein Tracking • ✅ Open Source
        </p>
    </div>

    <!-- CTA -->
    <div class="cta-section">
        <h2>Sei immer gut informiert und sicher unterwegs!</h2>
        <p>
            Lade die HundeWildnisApp herunter und erlebe die gemeinsame Zeit mit deinem Hund wie nie zuvor.
        </p>
        <a href="https://play.google.com/store/apps/details?id=com.hundewildnis.app" class="cta-button">
            📲 Jetzt kostenlos herunterladen
        </a>
        <p style="font-size: 0.9em; margin-top: 20px; opacity: 0.8;">
            Verfügbar im Google Play Store • Keine In-App-Käufe • Keine Werbung
        </p>
    </div>

    <!-- FOOTER -->
    <div class="app-footer">
        <p><strong>Die HundeWildnisApp ist eine 100%ige Eigenentwicklung von HundeWildnisSchule Manuela Ulbricht und Peter Jörg Ulbricht GbR</strong></p>
        <p style="margin-top: 15px;">
            🐙 <a href="https://github.com/tux4us/HundeWildnisAndroidApp">GitHub Repository</a> • 
            🌐 <a href="https://wildnislehrer.com">wildnislehrer.com</a>
        </p>
        <p style="margin-top: 25px; font-style: italic; font-size: 1.1em;">
            Entwickelt mit 💚 für Hunde und ihre Menschen
        </p>
        <p style="font-size: 0.85em; margin-top: 20px; color: #666;">
            Kartendaten © <a href="https://www.openstreetmap.org/copyright">OpenStreetMap Contributors</a><br>
            Wetterdaten © <a href="https://open-meteo.com/">Open-Meteo</a> (CC BY 4.0)
        </p>
    </div>

</div>

</body>
</html>






## 📱 Über die App

HundeWildnis bietet direkten Zugriff auf alle Inhalte von wildnislehrer.com in einer übersichtlichen, mobiloptimierten App. Perfekt für unterwegs beim Training mit deinem Hund oder beim Lernen in der Natur.

## ✨ Features

- 📝 **Blog** - Die neuesten Blogbeiträge mit "Weitere laden"-Funktion
- 📄 **Seiten** - Alle Seiten hierarchisch organisiert und übersichtlich dargestellt
- 🎓 **Onlinekurse** - Direkter Zugriff auf alle Kursinhalte (in Arbeit)
- 🎥 HundeWildnis YoutubeKanal - alle Videos in einer übersichtlichen Liste
- 📜 HundeWildnis X-Kanal - direkter Aufruf unseres Kanals
- 🔍 **Integrierte Suche** - Durchsuche alle Bereiche schnell und einfach
- 🧭 Integrierter Kompass - unterwegs immer die Orientierung behalten
- 📐 Integrierter Höhenmesser - immer wissen wie hoch man unterwegs ist
- 🌐 Standortbestimmung per GPS und der Möglichkeit den Standort unmittelbar über "Teilen" an Apps, Navigationsanwendungen und andere Empfänger zu übermitteln
- ☀️🌧️ standortgenaue aktuelle Wettersituation mit 24h Vorhersage:
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

### 4. Videos
- Übersichtliche Liste aller Videobeiträge unseres YouTube Kanals
- Videos werden datenschutzfreundlich im Browser bzw. vorhandener externer YoutubeApp geöffnet

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










