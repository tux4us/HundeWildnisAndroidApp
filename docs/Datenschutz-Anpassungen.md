# Datenschutzerklärung: Stand nach dem Umbau

Die Datenschutzerklärung ist jetzt in der App enthalten (Menü „Datenschutzerklärung“, Deutsch und Englisch, ohne Netzwerkzugriff).

- Quelle in der App: `privacy_content` in `app/src/main/res/values/strings.xml` (DE) und `values-en/strings.xml` (EN), HTML-Format für `Html.fromHtml`.
- Klartext-Fassungen im Repository: `Datenschutzerklaerung` (DE) und `PrivacyPolicy.en.txt` (EN). Bei Änderungen alle vier Stellen gleich halten.
- Anzeige: `PrivacyActivity.kt`.

## Gegenüber dem früheren Entwurf entfernt

WebView/ContentActivity/Inhalts-Cache (2.2, 2.4), Abruf von Inhalten von wildnislehrer.com (2.3), WordPress-API (3.1), YouTube (3.2), Twitter/X (3.3), Signal (3.7),
Nutzerkonto auf der Webseite (4), Verweise auf die Webseiten-Datenschutzerklärung, Steuernummer.

## Neu aufgenommen

Lokale Kurse, Berechtigungen (2.4), WebDAV-Synchronisation (3.4), PayPal-Spendenlink (3.5), Teilen und Export (3.6), Hinweis auf die Android-Datensicherung (2.3), Beschwerderecht bei der Aufsichtsbehörde (Art. 77 DSGVO).

## Vor der Veröffentlichung prüfen

- Inhaltliche und rechtliche Prüfung des Texts durch den Anbieter.
- Aussagen zu Mikrofon (keine Übertragung von Audiodaten), Speicherzugriff und zum Umfang der WebDAV-Synchronisation (Datendateien und Fotos) mit dem tatsächlichen Verhalten der App abgleichen.
- Kontakt-E-Mail: die Erklärung nennt `tux4us@online.de` (wie der frühere Entwurf), das Impressum `info[at]wildnislehrer.de`.
- Das Impressum nennt noch § 5 TMG und § 55 Abs. 2 RStV. Seit 2024 gelten § 5 DDG und § 18 Abs. 2 MStV. Der Text wurde unverändert übernommen und ins Englische übersetzt.

## Play Console

- Datenschutzerklärung: Die Play Console verlangt eine öffentlich erreichbare URL. Die In-App-Fassung ersetzt sie nicht.
- „Datensicherheit“: Abruf von wildnislehrer.com entfällt; angegeben bleiben nur die tatsächlich genutzten Dienste.
- Store-Beschreibung und Screenshots: Blog, Seiten, Mehr-Menü (YouTube, Podcast, Signal) und die Zahlungsoptionen Wero, Banküberweisung und Zahlungslink entfernen.
