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

## Phase 2 — Bildverwaltung
- [ ] Foto aufnehmen/aus Galerie wählen
- [ ] Lokale Speicherung/Kompression (`bild_pfad_eigen` / `bild_pfad_stock`)
- [ ] `aktives_bild`-Umschalter in Detail/Bearbeiten

**Warum jetzt:** Wird von Phase 1 (eigenes Foto beim manuellen Anlegen) UND von Phase 4 (Gemini-Flow braucht dieselbe Zwei-Bild-Logik) gebraucht — einmal bauen, zweimal nutzen.

## Phase 3 — Barcode-Scan
- [ ] ML-Kit-Scan-Screen
- [ ] EAN-Feld-Integration ins Formular

**Warum jetzt:** Unabhängiger, in sich abgeschlossener Quick-Win — kein API-Key nötig, kein Netz nötig, blockiert nichts danach.

## Phase 4 — Gemini-Integration (Kernfeature)
- [ ] Settings: API-Key-Eingabe (`EncryptedSharedPreferences`)
- [ ] Gemini-Service (`generateContent`, Structured Output, Search Grounding)
- [ ] „Per Foto hinzufügen"-Flow — nutzt das Formular aus Phase 1 als Korrektur-UI, die Bildlogik aus Phase 2, optional die EAN aus Phase 3
- [ ] Fehlerfälle: offline-Hinweis, „nicht genug Daten gefunden"

**Warum erst jetzt:** Komplexestes und riskantestes Stück (externe API, JSON-Schema-Design, Netzwerk-Fehlerfälle) — auf einem bereits laufenden, getesteten Fundament aufsetzen statt gleichzeitig UI und Gemini-Integration zu debuggen.

## Phase 5 — Distribution
- [ ] Datenschutz-Hinweis (Erststart + Settings)
- [ ] Signing-Keystore erzeugen + als GitHub Secrets hinterlegen
- [ ] Self-Update-Mechanismus (Versions-Check, Download, Install-Intent)
- [ ] Entwickler-Optionen (10×-Tap, Stable/Experimental-Umschalter)

**Warum jetzt:** Erst sinnvoll, wenn es eine Version gibt, die den Namen „Release" verdient — vorher wäre es Distributions-Infrastruktur für eine leere App.

## Phase 6 — Komfort
- [ ] DB-Export/Backup als ZIP

**Warum zuletzt:** Reines Sicherheits-/Komfort-Feature, blockiert nichts anderes und betrifft niemanden, bis tatsächlich Daten da sind, die es wert sind, gesichert zu werden.
