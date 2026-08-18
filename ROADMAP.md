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

**Nachbesserung 2026-08-18 (Stock-Bild-Qualität + Wiederholbarkeit):** Nutzer-Feedback: Stock-Bilder waren nicht überzeugend und wiederholte Anfragen für dasselbe Parfum (z. B. über den „Daten aktualisieren"-Button aus dem Editor) lieferten zu unterschiedliche Ergebnisse. Zwei Ursachen behoben, beide in `GeminiService.kt`:
- Request hatte gar keine `generationConfig` gesetzt, lief also auf Geminis Default-Temperature (deutlich über 0) — für eine Erkennungs-/Recherche-Aufgabe mit festem JSON-Format unnötig kreativ. Jetzt `temperature: 0.1`, reduziert Varianz zwischen Wiederholungen desselben Requests spürbar.
- Prompt sagte nur, dass die `stockBildUrl` real existieren muss, aber nicht, welches von mehreren gefundenen Bildern zu bevorzugen ist. Jetzt explizite Prioritätsreihenfolge (offizielle Marke → großer Händler → Duft-Datenbank) plus Präferenz für klassische Packshots (neutraler Hintergrund) vor Lifestyle-/Werbebildern mit Wasserzeichen.

Nur per `./gradlew compileDebugKotlin` verifiziert (kompiliert), nicht mit echtem API-Key gegen die echte Gemini-API getestet — die eigentliche Bildqualität/Konsistenz lässt sich nur mit echten Requests beurteilen, das war in diesem Environment nicht möglich.

## Phase 5 — Distribution ✅ fertig (2026-08-15)
- [x] Datenschutz-Hinweis (Erststart + Settings) — vorgezogen, kam natürlich mit dem Settings-Screen aus Phase 4 mit
- [x] Signing-Keystore erzeugen + als GitHub Secrets hinterlegt (RSA 4096, gültig bis 2056; lokale Kopie unter `E:\day_w\Android\keystore\`, dort unbedingt Backup-Hinweise in `WICHTIG-BACKUP-LESEN.txt` beachten)
- [x] Self-Update-Mechanismus (Versions-Check, Download, Install-Intent)
- [x] Entwickler-Optionen (10×-Tap, Stable/Experimental-Umschalter)

Self-Update und Entwickler-Optionen live gegen die echte GitHub-API des Repos verifiziert (Stable- und Experimental-Kanal, beide korrekt „kein Update" bei noch nicht existierenden Releases, kein Crash). Download/Installations-Pfad selbst ungetestet, da noch kein Release zum Herunterladen existiert — das braucht sowieso erst den Signing-Keystore, um über die CI-Pipeline etwas zu veröffentlichen.

**Wichtige Randnotiz (2026-08-15):** `Stable` ist der tatsächliche Default-Branch dieses Repos (nicht `main`) — bis hierhin lief die gesamte Arbeit nur auf `Experimental`, `Stable` hatte nie den Workflow oder den App-Code gesehen, weshalb GitHub Actions „keine Workflows" anzeigte. Gelöst durch Merge `Experimental` → `Stable`. Der Push-Trigger auf `Stable` war dabei kurz (2026-08-15, ca. 08:26–10:13 Uhr) während der ersten CI-Einrichtung absichtlich auskommentiert — **ist seitdem aber wieder aktiv**: jeder Push auf `Stable` (i. d. R. per Merge aus `Experimental`) löst automatisch einen echten, öffentlichen, signierten Release aus, kein manuelles „Run workflow" nötig. Erster echter Stable-Release: `v1.0` am 15.08., seither laufend (zuletzt `v4.0` am 18.08. mit Phase 8).

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

## Phase 8 — Erweiterungen (Shop-Suche, manuelle Suche, Sortierung/Filter)

Konzept-Vorlage: `Parfum-App_Erweiterungen.md` (lokal beim Nutzer, nicht im Repo). Status: **Planung**, noch nicht umgesetzt. Empfohlene Reihenfolge (aufsteigender Aufwand):

- [x] **8a — Sortierung & Filter** ✅ fertig (2026-08-18)
  - Sortier-Einstellung (Name/Marke/UVP) für die Übersichtsliste, global in den Einstellungen (`SortPreferenceStore`, `SettingsScreen`)
  - Such-/Filterleiste in der Listenansicht (Volltext über Name, Filter nach Marke, Filter nach Saison), automatisch kontextabhängig auf den gerade aktiven Tab (Sammlung/Wunschliste) — Filterung/Sortierung bewusst client-seitig in `CollectionViewModel` statt per SQL, da für eine private Sammlung ausreichend und ohne `@RawQuery` auskommend
  - Neues optionales Feld `saison` auf `Perfume` (Frühling/Sommer, Herbst/Winter, Ganzjährig) — von Gemini mitgeliefert (Prompt-Erweiterung in `GeminiService.kt`), sonst manuell im Editor nachtragbar (3 `FilterChip`s)
  - Erste echte Room-`Migration` dieser App (`MIGRATION_1_2`, `version = 1 → 2`, `ALTER TABLE perfume ADD COLUMN saison TEXT`) statt `fallbackToDestructiveMigration` — bestehende Sammlungen bleiben beim Update erhalten. Nach dem Build verifiziert: `app/schemas/.../2.json` entsteht korrekt neu.
  - Backup-Export/Import (`BackupManager`) um `saison` ergänzt, alte Backups ohne dieses Feld bleiben importierbar

  Nur per `./gradlew compileDebugKotlin` verifiziert, nicht live auf einem Gerät durchgeklickt (kein Test-Setup in diesem Projekt, siehe bisherige Phasen).

- [x] **8b — Manuelle Suche** ✅ fertig (2026-08-18)
  - Dritter Erfassungsweg neben Foto und Barcode-Scan („Nach Name suchen"-Button in `AddChoiceScreen`, eigener `ManuelleSucheScreen`/`ManuelleSucheViewModel`): Nutzer tippt nur den Namen, Gemini+Grounding sucht danach
  - Zweistufig in `GeminiService.kt`: `sucheKandidaten()` (Text-only, liefert `PerfumeKandidat`-Liste) → bei genau einem Treffer Rückfrage „Meinten Sie [Name] von [Marke]?", bei mehreren eine Auswahlliste (Auswahl selbst ist die Bestätigung, kein zusätzlicher Dialog) → `erkennePerfumNachNameUndMarke()` für die volle Datenübernahme, landet über dieselbe `PerfumeSuggestionBridge`/`EDITOR_VON_VORSCHLAG`-Route wie der Foto-Flow
  - `GeminiService` intern aufgeräumt: gemeinsamer `geminiAnfrage()`-Helper für alle drei Request-Typen (Foto-Erkennung, Kandidatensuche, Namens-Vollabruf) statt dreifach dupliziertem Request-/Fehlerbehandlungs-Code

  Nur per `./gradlew compileDebugKotlin` verifiziert. Die eigentliche Trefferqualität der neuen Prompts ist ohne echten API-Key hier nicht testbar (wie schon bei Phase 4).

- [x] **8c — Shop-Suche** ✅ fertig (2026-08-18)
  - Eigener Bereich im Preis-Tab der Detailansicht (`ShopSucheSektion` in `DetailScreen.kt`, direkt unter der UVP): Liste aktuell verfügbarer Online-Shops mit Preis und, sofern ermittelbar, Verfügbarkeit, antippbar (öffnet den Link im Browser)
  - `GeminiService.sucheShops()` (Text-only, gleicher `geminiAnfrage()`-Helper wie 8b), strukturiert als `ShopAngebot{shopName, link, preis, verfuegbarkeit}`
  - Bewusst reine Momentaufnahme zum Abrufzeitpunkt, **kein** automatischer Abruf beim Öffnen des Tabs — erst expliziter „Shops suchen"-Button, danach ein Refresh-Icon für erneutes Abfragen (`DetailViewModel.shopSucheStarten()`)
  - Kein dauerhaftes DB-Feld — Ergebnisse leben nur transient in `DetailUiState`/`DetailViewModel`, nirgends persistiert (kein `repository.update(...)`)

  Nur per `./gradlew compileDebugKotlin` verifiziert. Trefferqualität/Zuverlässigkeit der Preis-/Verfügbarkeits-Daten (erfahrungsgemäß der unzuverlässigste Teil solcher Prompts) lässt sich nur mit echtem API-Key auf dem Gerät beurteilen.

**Warum in dieser Reihenfolge:** 8a ist reine Compose/Room-Arbeit ohne Gemini-Abhängigkeit und unabhängig nutzbar. 8b baut direkt auf der in Phase 4 etablierten Suggestion-Pipeline auf. 8c ist der aufwändigste Teil (neues Antwortformat, Preis-/Verfügbarkeits-Daten sind erfahrungsgemäß am unzuverlässigsten) und zieht daher am ehesten Prompt-Nacharbeit nach sich, ähnlich wie bei der bestehenden Bild-/Konsistenz-Verfeinerung in Phase 4 (siehe dort).

**Gesamtstatus Phase 8 (2026-08-18):** Alle drei Sub-Phasen (8a/8b/8c) umgesetzt und live auf einem echten Gerät (Samsung SM-G991B, per adb) durchgeklickt.

- 8a ohne API-Key vollständig durchgeklickt: Suche/Marke-/Saison-Filter funktionieren gegen echte Room-Daten in beide Richtungen, Sortier-Picker persistiert, Erstinstallation lief anstandslos durch (Migration `v1→v2` griff, kein Crash).
- 8b und 8c mit echtem Gemini-API-Key live getestet: Namenssuche „Sauvage Dior" lieferte korrekt drei plausible, klar unterscheidbare Kandidaten (moderne Sauvage EDT / klassisches Eau Sauvage von 1966 / Sauvage Elixir) — nach Auswahl vollständige, inhaltlich zutreffende Daten (Beschreibung, UVP, Flakongrößen, komplette Duftpyramide, `saison` korrekt als „Ganzjährig" übernommen). Shop-Suche lieferte sechs reale, plausible deutsche Händler (Parfumdreams, Douglas, Flaconi, Marionnaud, easycosmetic, Engels Parfümerie) mit stimmigen Preisen.
- Ein Bug im Testlauf gefunden und gefixt: „Kein API-Key"-Hinweis in der Namenssuche zeigte fälschlich den Foto-Flow-Text („Foto-Erkennung", „das Foto wird übernommen") — eigener Text `manuelle_suche_hinweis_kein_api_key_text` ergänzt.
- Logcat blieb über die gesamte Testsession fehlerfrei (kein einziger Crash).
