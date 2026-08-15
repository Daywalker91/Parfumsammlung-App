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

Modell: `gemini-3.5-flash`. Settings-Screen (API-Key speichern/lesen über EncryptedSharedPreferences) und der komplette Auswahl-Screen live verifiziert, kein Crash. Der eigentliche Gemini-Netzwerk-Call selbst ist in diesem Environment nicht end-to-end testbar (kein echter API-Key vorhanden) — Code folgt der Doku, Fehlerfälle (offline/kein Key/nicht genug Daten/sonstiger Fehler) sind implementiert und fallen alle auf manuelle Eingabe zurück, inkl. Erhalt des bereits aufgenommenen Fotos.

**Warum erst jetzt:** Komplexestes und riskantestes Stück (externe API, JSON-Schema-Design, Netzwerk-Fehlerfälle) — auf einem bereits laufenden, getesteten Fundament aufsetzen statt gleichzeitig UI und Gemini-Integration zu debuggen.

## Phase 5 — Distribution (teilweise fertig, 2026-08-15)
- [x] Datenschutz-Hinweis (Erststart + Settings) — vorgezogen, kam natürlich mit dem Settings-Screen aus Phase 4 mit
- [ ] Signing-Keystore erzeugen + als GitHub Secrets hinterlegen — **noch offen, mit Nutzer abzustimmen** (sicherheitskritisch: Verlust = App kann sich nie wieder selbst updaten)
- [x] Self-Update-Mechanismus (Versions-Check, Download, Install-Intent)
- [x] Entwickler-Optionen (10×-Tap, Stable/Experimental-Umschalter)

Self-Update und Entwickler-Optionen live gegen die echte GitHub-API des Repos verifiziert (Stable- und Experimental-Kanal, beide korrekt „kein Update" bei noch nicht existierenden Releases, kein Crash). Download/Installations-Pfad selbst ungetestet, da noch kein Release zum Herunterladen existiert — das braucht sowieso erst den Signing-Keystore, um über die CI-Pipeline etwas zu veröffentlichen.

**Warum jetzt:** Erst sinnvoll, wenn es eine Version gibt, die den Namen „Release" verdient — vorher wäre es Distributions-Infrastruktur für eine leere App.

## Phase 6 — Komfort
- [ ] DB-Export/Backup als ZIP

**Warum zuletzt:** Reines Sicherheits-/Komfort-Feature, blockiert nichts anderes und betrifft niemanden, bis tatsächlich Daten da sind, die es wert sind, gesichert zu werden.
