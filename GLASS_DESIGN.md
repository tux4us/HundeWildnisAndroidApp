# Glas-Design (nur Dark)

Die App hat ein einziges Theme (`Theme.HundeWildnis`, Basis `Theme.Material3.Dark.NoActionBar`).
Hell/Dunkel-Umschaltung, `values-night/`, `color-night/` und alle `_light`-Farben sind entfernt.

## Aufbau

| Ebene | Datei | Inhalt |
|---|---|---|
| Hintergrund | `res/drawable/bg_app_gradient.xml` | dunkler Verlauf mit drei weichen Farbflächen (Gold, Grün, Blau); als `windowBackground` gesetzt |
| Tokens | `res/values/colors_glass.xml` | Glasflächen `gl_fill*`, Ränder `gl_stroke*`, Leisten `gl_bar`, Fenster `gl_dialog`/`gl_sheet`, Rollenfarben `gl_<rolle>[_fill/_stroke/_ripple]`, Gruppenfarben `rd_groupN_accent/_chip/_stroke` |
| Basisfarben | `res/values/colors_redesign.xml` | `rd_bg`, `rd_card` (opak für Popups), Text, Notfall |
| Theme | `res/values/themes.xml` | Farbrollen, globale Widget-Styles, Dialog- und Bottom-Sheet-Theme |
| Blur ab API 31 | `res/values-v31/themes.xml`, `res/drawable-v31/bg_glass_dialog.xml` | Fenster-Blur für Dialoge und Bottom Sheets |
| Styles | `res/values/styles_responsive.xml` | Buttons, Karten, Eingabefelder, Chips, Leisten |
| Zustandsfarben | `res/color/gl_btn_*` | Füllung, Rand, Text je Rolle (inkl. deaktiviert) |

## Button-Rollen

Die Farbe eines Buttons bezeichnet seinen Zweck. Statt Hex-Werten im Layout wird ein Style gesetzt:

| Rolle | Farbe | Verwendung |
|---|---|---|
| (neutral) | Weiß-Glas | Standardaktionen |
| `Primary` | Gold | Hauptaktion |
| `Success` | Grün | Speichern, Starten, bestätigen |
| `Warning` | Orange | Vorsicht |
| `Danger` | Rot | Löschen, Stoppen, Notfall |
| `Info` | Blau | Hinweise, Teilen |

Varianten: `Widget.App.Button.<Rolle>` (gefülltes Glas), `Widget.App.Button.Outlined[.<Rolle>]` (nur Rand),
`Widget.App.Button.Text[.<Rolle>]` (nur Text), `Widget.App.Button.Fixed[.<Rolle>]` (feste Größe, ohne Auto-Skalierung).

## Karten

`MaterialCardView` ist global auf `Widget.App.Card` gesetzt (transparente Füllung, 1 dp Rand, keine Elevation).
Karte in Karte: `style="@style/Widget.App.Card.Inner"`. Kacheln der Startseite färbt `StartTileAdapter`
mit den Gruppenfarben (`StartGroupColors`).

## Regeln für neue Layouts

- Keinen eigenen `android:background` auf Wurzel-Layouts setzen; der Fensterhintergrund liefert den Verlauf.
- Keine Hex-Farben; Tokens aus `colors_glass.xml` oder `rd_*` verwenden.
- Keine Schatten (`cardElevation`, `elevation`) unter transparenten Flächen.
- Popups, Menüs und Auswahllisten nutzen `colorSurface*` (opak), nicht `gl_fill`.
- Buttons nur über Rollen-Styles färben. Text auf getöntem Glas bleibt `rd_text_primary` (Kontrast ≥ 9:1).

## Update: neutrale Füllung, farbiger Rand
Alle Buttons, Karten, Kacheln, Badges und FABs nutzen jetzt dieselbe neutrale Glasfüllung (`gl_fill`).
Die Rollen-/Gruppenfarbe steckt nur noch im Rand (`gl_<rolle>_stroke` bzw. `rd_groupN_stroke`, 90 % Deckkraft,
1,5 dp bei Buttons/Karten, 2 dp bei Start-Kacheln) sowie in Text und Icon.
Die Tokens `gl_<rolle>_fill` und `rd_groupN_chip` bleiben als Alias erhalten, zeigen aber alle auf das neutrale Glas.

## Update: Lesbarkeit (Testrunde)
- Giftigkeitsstufen (`PlantToxicityLevel.color`), Dringlichkeitsstufen, Wetter-/Gelände-/Gewitter-Ampeln und Status-Texte nutzen helle Farben (Kontrast ≥ 4,5:1 auf dem Glas-Grau).
- Karten mit Statusfarbe (Pflanzen-Detail, Erste Hilfe, Intervall-Timer, Trainingsempfehlung, Steigungsbadge) nutzen `gl_fill` + farbigen Rand (2–2,5 dp).
- Textfelder zeigen den Rahmen dauerhaft (`gl_stroke_strong`, 1,5 dp; fokussiert 2 dp Gold).
- Dialoge sind dichter (Alpha ≈ 96 %), damit Listeneinträge lesbar bleiben.

## Update: Glas-Stil des MiriquidiLauncher (Variante C)
- `GlassCardView` (statt `MaterialCardView`) und `GlassButton` (statt `MaterialButton`) in allen Layouts. `GlassPainter` zeichnet
  Glanzverlauf (oben 12 % Weiß, ab 45 % transparent, unten 12 % Schwarz) und Randverlauf (oben kräftig, nach unten ausgeblendet:
  neutral weiß 60 % → 20 %, Rollenfarbe 100 % → 45 %).
- `strokeColor`/`strokeWidth` werden weiter wie bisher gesetzt (XML und Code); die Views übernehmen sie und zeichnen den Rand selbst.
  Der Material-Standardrand ist abgeschaltet.
- Füllung dunkles Glas `#14181F` mit 60 % Deckkraft (`gl_fill` = `#9914181F`), Glanz oben 18 % / unten 16 % (Variante B). Kein Schatten (Elevation bleibt 0).
- Drawables mit Glanz-Ebene (layer-list): FABs, Badges, Metrik-Kachel, Gefahr-Banner, POI-Button. Der Rand dort bleibt einfarbig.
- Bewusst flach: Eingabefelder, Dialoge, Bottom-Sheets, Toolbars, Listenzeilen.
- Neue Cards/Buttons im Code: `GlassCardView(context)` bzw. `GlassButton(context)` verwenden.

## Update: dunkleres Glas (mehr Kontrast)
Die Kartenfüllung ist jetzt dunkel statt hell (`gl_fill` = `#9914181F`, alle Rollen-/Gruppen-Fills identisch). Glanz wieder 18 % / 16 %,
Hintergrundverlauf und Glows etwas dunkler (`gl_bg_*`, `rd_bg` = `#0D0E10`), Eingabefelder `gl_fill_subtle` = `#66101419`.
