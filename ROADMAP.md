# Aromathek — Implementierungs-Roadmap

Reihenfolge, mit der wir am schnellsten zu einer wirklich nutzbaren App kommen. Jede Phase baut auf der vorherigen auf — nichts davon ist parallel gedacht. Details zu den einzelnen Features stehen in `Parfum-App_Plan.md`, hier geht's nur um die Reihenfolge.

## Phase 1 — Kern-CRUD (MVP, komplett ohne Gemini nutzbar) ✅ fertig (2026-08-15)
- [x] Repository-Schicht über den bestehenden Room-DAOs
- [x] Compose-Navigation-Grundgerüst
- [x] Sammlung-Liste (Besitzt/Wunschliste getrennt)
- [x] Parfum-Detail-Ansicht (inkl. Duftpyramide)
- [x] Hinzufügen/Bearbeiten-Formular (manuell, inkl. Noten + Position)
- [x] Löschen
- [ ] Duplikat-Check beim Hinzufügen (Name+Marke) — Logik implementiert, aber nicht end-to-end im Emulator durchgeklickt (siehe unten)

Auf dem Emulator durchgeklickt und verifiziert: Anlegen (inkl. Note mit Position), Listen-Reaktivität, Detail-Ansicht mit korrekt gruppierter Duftpyramide, Bearbeiten-Vorbelegung, Löschen mit Bestätigungsdialog. Der Duplikat-Check-Dialog selbst wurde nicht manuell durchgeklickt — Code ist vorhanden und dieselben Muster (State-Flow, AlertDialog) sind an anderer Stelle bereits bestätigt funktionsfähig.

**Warum zuerst:** Ergibt eine komplett benutzbare App, bevor überhaupt ein Gemini-Key existiert. Der manuelle Eintrag ist laut Plan ohnehin Pflicht-Fallback (offline, unzureichende Erkennung) — wird so oder so gebraucht. Etabliert Repository/Navigation/ViewModel-Architektur, an die alles Weitere nur andockt.

## Phase 2 — Bildverwaltung ✅ fertig (2026-08-15)
- [x] Foto aufnehmen/aus Galerie wählen
- [x] Lokale Speicherung/Kompression (`bild_pfad_eigen` / `bild_pfad_stock`)
- [x] `aktives_bild`-Umschalter in Detail/Bearbeiten

Auf dem Emulator verifiziert: Kamera-Aufnahme-Flow (FileProvider), Galerie-Picker öffnet und zeigt echte Bilder, Vorschau im Editor, kein Crash. Die aktives_bild-Umschaltung selbst wurde nicht mit zwei echten Bildern durchgeklickt (dafür bräuchte es ein Gemini-gefundenes Stock-Bild aus Phase 4), Logik/Verdrahtung sind aber vorhanden und folgen denselben bereits bestätigten Mustern.

**Warum jetzt:** Wird von Phase 1 (eigenes Foto beim manuellen Anlegen) UND von Phase 4 (Gemini-Flow braucht dieselbe Zwei-Bild-Logik) gebraucht — einmal bauen, zweimal nutzen.

## Phase 3 — Barcode-Scan ✅ fertig (2026-08-15)
- [x] ML-Kit-Scan-Screen (Google Code Scanner, kein eigenes Kamera-UI nötig)
- [x] EAN-Feld-Integration ins Formular (fließt sowohl als Gemini-Kontext als auch ins gespeicherte Feld)

Nicht live durchscannbar in diesem Environment — der Emulator (ohne Play Store) kann das ML-Kit-Barcode-Modul nicht nachladen. Fehlerfall abgefangen (Toast statt stillem Nichtstun), kein Crash. Sollte auf einem echten Gerät mit Play Store normal funktionieren.

**Warum jetzt:** Unabhängiger, in sich abgeschlossener Quick-Win — kein API-Key nötig, kein Netz nötig, blockiert nichts danach.

## Phase 4 — Gemini-Integration (Kernfeature) ✅ fertig (2026-08-15)
- [x] Settings: API-Key-Eingabe (`EncryptedSharedPreferences`)
- [x] Gemini-Service (`generateContent`, Structured Output, Search Grounding)
- [x] „Per Foto hinzufügen"-Flow — nutzt das Formular aus Phase 1 als Korrektur-UI, die Bildlogik aus Phase 2, optional die EAN aus Phase 3
- [x] Fehlerfälle: offline-Hinweis, „nicht genug Daten gefunden"

Modell: `gemini-2.5-flash` (zunächst `gemini-3.5-flash`, siehe unten warum umgestellt). Settings-Screen (API-Key speichern/lesen über EncryptedSharedPreferences) und der komplette Auswahl-Screen live verifiziert, kein Crash. Der eigentliche Gemini-Netzwerk-Call selbst ist in diesem Environment nicht end-to-end testbar (kein echter API-Key vorhanden) — Code folgt der Doku, Fehlerfälle (offline/kein Key/nicht genug Daten/sonstiger Fehler) sind implementiert und fallen alle auf manuelle Eingabe zurück, inkl. Erhalt des bereits aufgenommenen Fotos.

**Kein festes Timeout mehr, dafür manueller Abbrechen-Button (2026-08-15):** Auch 30/60/90s reichten teilweise nicht — ein Grounded-Request kann je nach Google-Antwortzeit unterschiedlich lange dauern. Statt eines weiteren (zwangsläufig geratenen) Zeitlimits: `GeminiService` hat jetzt gar kein Timeout mehr (`callTimeout(0, ...)`), der Netzwerk-Call läuft über einen echten abbrechbaren `enqueue`-Callback (`ausfuehrenAbbrechbar`) statt über blockierendes `execute()`, und `AddChoiceScreen` zeigt während des Ladens einen "Abbrechen"-Button, der den laufenden Vorgang per `AddChoiceViewModel.abbrechen()` sofort stoppt (inkl. echtem Canceln des OkHttp-Calls, nicht nur der Coroutine).

**Modellwechsel 2026-08-15 (Hin und zurück):** Mit echtem API-Key trat bei jedem Versuch ein 429-Fehler auf. Ursache: Grounding mit Google-Suche (die App schickt bei jedem Request unbedingt `tools: [{"google_search": {}}]` mit, siehe `GeminiService.kt`) ist bei den Gemini-3.x-Modellen seit 5.1.2026 nur noch im bezahlten Tarif enthalten. Zunächst auf `gemini-2.5-flash` umgestellt (dort wäre Grounding noch kostenlos bis 500 Anfragen/Tag) — das schlug aber mit 404 fehl, weil Google die komplette 2.5er-Reihe inzwischen für neu erstellte API-Keys sperrt (vorgezogen vor dem offiziellen Abschaltdatum 16.10.2026). Zurück auf `gemini-3.5-flash`, stattdessen Billing auf dem Google-Cloud-Projekt aktiviert (5.000 grounded Anfragen/Monat weiterhin gratis inklusive — bei privater Nutzung real 0€/Monat). Vor der Entscheidung wurde der komplette App-Code auf mögliche Loop-/Mehrfachaufruf-Risiken geprüft (keine gefunden, siehe `AddChoiceViewModel.kt`/`AddChoiceScreen.kt`).

**Warum erst jetzt:** Komplexestes und riskantestes Stück (externe API, JSON-Schema-Design, Netzwerk-Fehlerfälle) — auf einem bereits laufenden, getesteten Fundament aufsetzen statt gleichzeitig UI und Gemini-Integration zu debuggen.

## Phase 5 — Distribution ✅ fertig (2026-08-15)
- [x] Datenschutz-Hinweis (Erststart + Settings) — vorgezogen, kam natürlich mit dem Settings-Screen aus Phase 4 mit
- [x] Signing-Keystore erzeugen + als GitHub Secrets hinterlegt (RSA 4096, gültig bis 2056; lokale Kopie unter `E:\day_w\Android\keystore\`, dort unbedingt Backup-Hinweise in `WICHTIG-BACKUP-LESEN.txt` beachten)
- [x] Self-Update-Mechanismus (Versions-Check, Download, Install-Intent)
- [x] Entwickler-Optionen (10×-Tap, Stable/Experimental-Umschalter)

Self-Update und Entwickler-Optionen live gegen die echte GitHub-API des Repos verifiziert (Stable- und Experimental-Kanal, beide korrekt „kein Update" bei noch nicht existierenden Releases, kein Crash). Download/Installations-Pfad selbst ungetestet, da noch kein Release zum Herunterladen existiert — das braucht sowieso erst den Signing-Keystore, um über die CI-Pipeline etwas zu veröffentlichen.

**Wichtige Randnotiz (2026-08-15):** `Stable` ist der tatsächliche Default-Branch dieses Repos (nicht `main`) — bis hierhin lief die gesamte Arbeit nur auf `Experimental`, `Stable` hatte nie den Workflow oder den App-Code gesehen, weshalb GitHub Actions „keine Workflows" anzeigte. Gelöst durch Merge `Experimental` → `Stable`. Der Push-Trigger auf `Stable` ist aktuell **absichtlich auskommentiert** (nur `workflow_dispatch` aktiv) — der erste echte Stable-Release passiert erst, wenn der Push-Trigger in `.github/workflows/build-release.yml` wieder aktiviert wird oder der Workflow manuell über „Run workflow" gestartet wird (beides erzeugt einen echten, öffentlichen, signierten Release).

**Warum jetzt:** Erst sinnvoll, wenn es eine Version gibt, die den Namen „Release" verdient — vorher wäre es Distributions-Infrastruktur für eine leere App.

## Phase 6 — Komfort ✅ fertig (2026-08-15)
- [x] DB-Export/Backup als ZIP

Neuer `BackupManager` (`data/backup/`): Export schreibt ein `daten.json`-Manifest (alle Parfums+Noten, Bildfelder als reine Dateinamen statt absoluter Pfade) plus die referenzierten Bilddateien unter `images/` in eine ZIP — bewusst kein Kopieren der rohen Room-DB-Datei, das JSON-Format ist robust gegenüber Pfadänderungen (z. B. nach Neuinstallation) und braucht keine Rücksicht auf offene DB-Connections. Speicherort per Storage-Access-Framework frei wählbar (`CreateDocument`/`OpenDocument`, keine zusätzliche Berechtigung nötig). Import überspringt bereits vorhandene Einträge (gleicher Name+Marke, derselbe Duplikat-Check wie beim manuellen Anlegen) — wiederholtes Einspielen ist damit gefahrlos. Buttons + Fortschritts-/Ergebnis-Toast im Settings-Screen. Lokal per `./gradlew compileDebugKotlin` verifiziert, nicht live auf einem Gerät durchgeklickt.

**Warum zuletzt:** Reines Sicherheits-/Komfort-Feature, blockiert nichts anderes und betrifft niemanden, bis tatsächlich Daten da sind, die es wert sind, gesichert zu werden.

## Phase 7 — Design-Überarbeitung ✅ fertig (2026-08-15)

Nutzer-Feedback 2026-08-15 zum `DetailScreen`: wirkte "alles auf einen Haufen". Umgesetzt:

- **Immer sichtbar (Header, kein Tab):** Bild + Umschalter eigenes Foto/Stock-Bild, Marke (Name steht schon in der TopAppBar).
- **Tab „Info":** Beschreibung + Duftpyramide.
- **Tab „Bewertung":** Sterne-Bewertung + eigene Notiz.
- **Tab „Preis":** UVP, Flakongröße, verfügbare Flakongrößen, EAN (passte inhaltlich am besten hierher, war im ursprünglichen Plan nicht explizit einsortiert).
- **Duftpyramide — kleine Symbole pro Note:** Emoji-basiertes Stichwort-Mapping (`noteEmoji()` in `DetailScreen.kt`) statt eines eigenen Icon-Sets — bewusst keine `material-icons-extended`-Abhängigkeit aufgemacht (die ist ohne aktivierte Minifizierung, `isMinifyEnabled = false`, spürbar APK-Größe wert). Deckt gängige Duftfamilien ab (Zitrus, Blumig, Aquatisch, Würzig, Holzig, Ambriert, Gourmand, Fruchtig, Grün/Kräuter), neutrales Fallback-Symbol (✨) für alles andere.
- Editor-Screen (`PerfumeEditorScreen`) bewusst NICHT umgebaut — bleibt Formular, wie zuvor besprochen nur der reine Anzeige-Screen betroffen.

`TabRow` (deprecated) durch `SecondaryTabRow` ersetzt. Lokal per `./gradlew compileDebugKotlin` verifiziert, nicht live auf einem Gerät durchgeklickt.
