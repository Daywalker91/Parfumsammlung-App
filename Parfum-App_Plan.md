# Parfum-Datenbank App — Konzept & Plan

*Private Android-App für einen Freund, kein Play-Store-Release*

---

## Ziel

Native Android-App zum Verwalten einer Parfum-Sammlung:
- Erfassung vorhandener Parfums inkl. Duftpyramide (Kopf-/Herz-/Basisnote)
- Neue Parfums per Foto hinzufügen (Erkennung via Gemini API, kein Google Lens/Scraping nötig)
- Barcode-Scan als optionale Zusatzinfo
- Wunschliste getrennt von der eigenen Sammlung
- Kein eigenes Backend — alles lokal auf dem Gerät

---

## Tech-Stack

| Komponente | Technologie | Grund |
|---|---|---|
| Sprache | Kotlin | Standard für native Android-Apps |
| UI | Jetpack Compose | Modern, kein XML-Layout-Overhead |
| Lokale DB | Room (SQLite) | Kein Server nötig, offline-fähig |
| Bilderkennung | Gemini API (multimodal + Google Search Grounding) | Ersetzt Google Lens, liefert strukturierte Daten inkl. aktueller Web-Infos |
| Barcode | ML Kit Barcode Scanning (on-device) | Kostenlos, kein Cloud-Call, läuft offline |
| Build | Gradle (Kotlin DSL) | Standard, per CLI baubar ohne Android Studio GUI |

---

## Datenmodell (Room)

**Perfume**
- id (PK)
- name
- marke
- beschreibung (optional, kurze Duftbeschreibung — von Gemini mitgeliefert)
- uvp (optional, unverbindliche Preisempfehlung)
- status (`besitzt` / `wunschliste`)
- flakongroesse (optional)
- ean (optional, aus Barcode-Scan)
- verfuegbare_groessen (optional, Freitext/Liste, z. B. "50ml, 100ml, 200ml" — vom Hersteller angebotene Flakongrößen, sofern über Gemini/Websuche auffindbar)
- bild_pfad_stock (lokaler Cache, automatisch beim Hinzufügen besorgt)
- bild_pfad_eigen (optional, lokaler Pfad zum eigenen Foto der Flasche)
- aktives_bild (`eigen` / `stock` — steuert, welches Bild angezeigt wird)
- notiz (optional, freier Text, eigene Anmerkungen)
- bewertung (optional, z. B. 1-5 Sterne)
- erstellt_am

**Note**
- id (PK)
- name
- kategorie (optional, z. B. "Frucht", "Holz", "Amber")

**PerfumeNote** (m:n-Verknüpfung)
- perfume_id (FK)
- note_id (FK)
- position (`kopf` / `herz` / `basis`)

---

## User-Flow: Parfum hinzufügen

1. Foto aufnehmen oder aus Galerie teilen (App als Share-Target registriert) — oder Erfassung startet ohne Foto (nur Barcode/manuelle Eingabe)
2. Falls Foto vorhanden: Bild wird an Gemini API geschickt (mit Structured-Output-Schema als Prompt-Vorgabe)
3. Gemini liefert JSON: Marke, Name, Kopf-/Herz-/Basisnoten, kurze Duftbeschreibung, UVP (falls über Google Search Grounding auffindbar); zusätzlich wird im Hintergrund ein Stock-Bild-Vorschlag besorgt
4. Ergebnis wird als Vorschlag in der App angezeigt
5. User prüft/korrigiert die Felder
6. **Bild-Auswahl:** App fragt aktiv, welches Bild hinterlegt werden soll — eigenes Foto (falls im Schritt 1 eines gemacht wurde) oder das automatisch gefundene Stock-Bild. Beide werden lokal gecacht, unabhängig von der Auswahl bleibt das jeweils andere im Hintergrund verfügbar und die Anzeige lässt sich später jederzeit umschalten.
7. Bestätigung → Insert in Room-DB

Optional davor: Barcode scannen (ML Kit) → EAN wird als zusätzlicher Kontext im Gemini-Prompt mitgeschickt, falls die Bilderkennung allein unsicher ist.

**Duplikat-Check:** Vor dem finalen Insert wird geprüft, ob Name+Marke bereits in der DB existieren. Falls ja: Hinweis anzeigen, Nutzer entscheidet zwischen „vorhandenen Eintrag aktualisieren" oder „trotzdem als neuen Eintrag anlegen" (z. B. für Zweitflakon).

**Offline-Verhalten:** Barcode-Scan (ML Kit) funktioniert ohne Internet. Die Gemini-Bilderkennung braucht eine Verbindung — ohne Netz zeigt die App einen klaren Hinweis und bietet direkt die manuelle Eingabe der Felder als Alternative an.

**Unzureichende Erkennung:** Findet Gemini kein oder nur unzureichendes Material zu einem Parfum (z. B. seltenes Nischenprodukt), zeigt die App eine explizite Meldung („Nicht genug Daten gefunden") statt leere/geratene Felder unkommentiert anzuzeigen. Als Alternativen werden direkt angeboten: manuelle Eingabe der Felder, oder — falls noch nicht geschehen — ein Versuch über die EAN (Barcode-Scan) als zweite Erkennungsquelle.

---

## Bildspeicherung

Zwei-Bild-Konzept: **Beide Varianten werden lokal gecacht, Nutzer wählt beim Hinzufügen aktiv, welche als Anzeige-Bild dient — Wechsel später jederzeit möglich.**

**Stock-Bild (automatisch besorgt)**
1. Beim Hinzufügen liefert Gemini (via Google Search Grounding) zusätzlich zu den Textdaten ein passendes Produktbild/Quelle
2. Bild wird einmalig heruntergeladen, komprimiert und lokal als `bild_pfad_stock` gecacht

**Eigenes Bild (falls beim Hinzufügen ein Foto gemacht/geteilt wurde)**
1. Das für die Erkennung genutzte Foto (oder ein separat aufgenommenes) wird komprimiert lokal gespeichert als `bild_pfad_eigen`

**Auswahl**
- Beim Hinzufügen fragt die App aktiv, welches der beiden Bilder als Anzeige dienen soll (`aktives_bild`-Flag auf dem Eintrag: `eigen` / `stock`)
- Beide Varianten bleiben im Hintergrund erhalten, unabhängig von der Wahl — Umschalten ist jederzeit später möglich, ohne erneuten Download
- Fehlt eine der beiden Varianten (z. B. kein eigenes Foto gemacht), entfällt die Auswahl entsprechend
- **Nachträglicher Bildaustausch:** Das eigene Bild lässt sich jederzeit über Bearbeiten ersetzen (z. B. Erst-Foto war nur für die Erkennung gedacht, später folgt ein besseres) — überschreibt `bild_pfad_eigen`, `aktives_bild` kann danach auf das neue eigene Bild umgestellt werden

**Backup-Relevanz**
- **Eigenes Bild** ist unersetzbar (persönliches Foto) → zwingender Bestandteil jedes Backups
- **Stock-Bild** ist ersetzbar → muss nicht zwingend im Backup enthalten sein, kann bei Bedarf (z. B. nach Wiederherstellung) erneut über Gemini/Websuche geholt werden → hält Backups klein

Kein Netzwerk-Zugriff nötig für die normale Anzeige — beide Bildtypen liegen nach dem einmaligen Abruf rein lokal im Cache.

---

## Gemini-Integration — Details

- **API-Key-Quelle:** Google AI Studio (aistudio.google.com), kostenloses Kontingent
- **Speicherort (Entwicklung):** `local.properties` (lokal, nicht in Git) → wird via `BuildConfig` als Default in die App injiziert
- **Speicherort (Laufzeit):** Einstellungs-Screen mit Eingabefeld für den API-Key — Nutzer kann den Key direkt in der App hinterlegen/ändern, gespeichert über `EncryptedSharedPreferences` (Android Jetpack Security, verschlüsselt auf dem Gerät)
- **Vorteil dieses Ansatzes:**
  - Kein Key mehr fest im APK eingebaut — beim Sideload-Verteilen der APK an den Freund gibt jeder seinen eigenen Key ein (BYOK-Prinzip)
  - Key lässt sich jederzeit ändern/rotieren, ohne neuen Build
  - Falls kein Key hinterlegt ist: App zeigt Hinweis, dass Foto-Erkennung ohne Key nicht funktioniert (manuelle Eingabe der Parfum-Daten bleibt trotzdem möglich)
- **Call-Typ:** `generateContent` mit Bild + Text-Prompt, Google Search Grounding aktiviert, Antwort als JSON erzwungen (Structured Output)
- **Kein Backend nötig**, da Call direkt aus der App an `generativelanguage.googleapis.com` geht

---

## Self-Update über GitHub Releases

Da kein Play Store genutzt wird, aktualisiert sich die App selbst über GitHub — ab der ersten Version mit dabei.

**Ablauf**
1. App fragt bei Start (oder periodisch) `GET /repos/{owner}/{repo}/releases/latest` ab (GitHub Releases API, öffentlich, kein Auth nötig für öffentliche Repos)
2. Vergleich der Release-Tag-Version mit installierter `versionCode` (`BuildConfig`)
3. Falls neuer: APK-Asset im Hintergrund herunterladen (`DownloadManager` oder OkHttp)
4. Installations-Dialog anstoßen (nächster Start oder sofort)

**Technisch nötig**
- Permission `REQUEST_INSTALL_PACKAGES`
- `FileProvider` für sichere `content://`-Übergabe der APK an den PackageInstaller
- **Stabiler Signing-Keystore** — jede neue Version muss mit demselben Key signiert sein wie die installierte, sonst verweigert Android das Update

**Einmalige Nutzer-Aktion**
Beim allerersten Sideload muss „Installation aus dieser Quelle erlauben" einmalig manuell in den Android-Einstellungen freigegeben werden (nicht automatisierbar, Sicherheitsmechanismus). Bei jedem Update erscheint danach der normale Install-Bestätigungsdialog — ebenfalls nicht automatisierbar ohne Root/Device-Owner.

**Entwickler-Workflow**
Neue Version bauen → mit stabilem Keystore signieren → GitHub Release mit Tag erstellen, APK als Asset hochladen → fertig, App holt sich den Rest selbst.

**Backend-Entscheidung:** Kein separates Backend/Proxy — Update-Mechanismus läuft komplett über GitHub (Hosting + API), kein eigener Server nötig.

---

## Entwickler-Optionen & Update-Kanal (Stable/Experimental)

Nach Android-Vorbild: versteckter Zugang zu erweiterten Einstellungen, gekoppelt an den Update-Kanal.

**Aktivierung**
- Ganz unten in den Einstellungen wird die aktuell installierte App-Version angezeigt (`versionName`/`versionCode` aus `BuildConfig`)
- 10x Antippen der Versionszeile → Toast/Meldung „Entwickler-Optionen aktiviert."
- Danach erscheint ein zusätzlicher Einstellungs-Unterpunkt „Entwickler-Optionen"

**Entwickler-Optionen-Unterseite**
- Umschalter zwischen **Stable** und **Experimental** als Update-Kanal
- Wirkt sich auf den Self-Update-Mechanismus aus: Stable prüft nur reguläre GitHub Releases, Experimental bezieht zusätzlich Pre-Releases mit ein (`prerelease: true` in der GitHub Releases API)

**Persistenz-Verhalten (wichtig, zwei getrennte Zustände)**
- **Sichtbarkeit des Entwickler-Menüs:** wird beim Schließen der App wieder zurückgesetzt (versteckt) — erneutes 10x-Tippen nötig, um es wieder einzublenden
- **Gewählter Update-Kanal (Stable/Experimental):** bleibt dauerhaft gespeichert, unabhängig davon, ob das Entwickler-Menü gerade sichtbar ist oder nicht — ein einmal gewählter Experimental-Kanal bleibt also aktiv, auch wenn das Menü wieder „gesperrt" ist

**Technisch**
- Zwei getrennte Speicherwerte nötig: `entwickleroptionen_sichtbar` (nur In-Memory/Session-State, kein Neustart-Persist) und `update_kanal` (persistent, z. B. via DataStore/SharedPreferences)

---

## Voraussetzungen für Build & Test mit Claude Code in VSCode

Da ohne Android Studio GUI gearbeitet werden soll, folgende Tools lokal nötig:

**Zwingend erforderlich**
- **JDK 17** (Android Gradle Plugin verlangt aktuelle JDK-Version)
- **Android SDK Command-Line Tools** (`sdkmanager`, `platform-tools`, mind. eine `build-tools`-Version, `platforms;android-XX`)
- Umgebungsvariable `ANDROID_HOME` bzw. `ANDROID_SDK_ROOT` gesetzt
- **Gradle Wrapper** (kommt mit dem Projekt, kein separates Gradle-Setup nötig)

**Für Testing**
- Entweder:
  - **Physisches Android-Gerät** mit aktiviertem USB-Debugging + `adb` installiert (Teil der Platform-Tools) → `adb install app-debug.apk`
  - oder **Android Emulator** via `avdmanager` + `emulator`-CLI (benötigt zusätzlich System-Image, Virtualisierung/KVM unter Linux)
- Für Android-Studio-freien Workflow ist ein echtes Testgerät meist der pragmatischere Weg als ein CLI-Emulator

**Optional, aber hilfreich**
- VSCode-Erweiterung „Kotlin Language" für Syntax-Highlighting (nicht zwingend, da Claude Code die Dateien direkt schreibt/editiert)
- `keytool` (Teil des JDK) für einen Debug-/Signing-Keystore, falls über reines Debug-Signing hinaus benötigt

**Sicherheitshinweis**
- Da der Key über die Einstellungen eingegeben wird (statt fest im APK), ist das ursprüngliche Extraktionsrisiko entschärft — jeder Nutzer verwendet seinen eigenen Key, verschlüsselt lokal gespeichert (`EncryptedSharedPreferences`). Key ist in Google AI Studio jederzeit rotierbar/löschbar.

**Hinweis für den Projektstart**
Claude Code soll zu Beginn (erste Session im neuen Workspace) selbstständig prüfen bzw. abfragen, ob die oben genannten Voraussetzungen erfüllt sind — JDK-Version, Android SDK Command-Line Tools inkl. `ANDROID_HOME`/`ANDROID_SDK_ROOT`, sowie ob ein physisches Testgerät (USB-Debugging) oder ein Emulator zur Verfügung steht. Fehlende Tools sollen benannt und wenn möglich direkt zur Installation vorgeschlagen werden, statt stillschweigend vorauszusetzen, dass alles vorhanden ist.

---

## Weitere Kernfunktionen

- **Bearbeiten/Löschen:** Bestehende Parfum-Einträge müssen sich nachträglich korrigieren oder entfernen lassen (nicht nur Hinzufügen) — Grundfunktion, kein Nice-to-have
- **Datenschutz-Hinweis:** Da Fotos beim Scan an Google/Gemini übertragen werden, zeigt die App beim ersten Start bzw. in den Einstellungen einen kurzen, klaren Hinweis darauf. Auch bei rein privater Nutzung sinnvoll, damit beide Nutzer wissen, was passiert.
- **DB-Export/Backup:** Export als **ZIP-Datei**, die die rohe Room-DB-Kopie plus alle eigenen Bilder (`bild_pfad_eigen`) enthält — einfach zu erstellen, einfach zu teilen/sichern (z. B. bei Gerätewechsel), ein einzelner Datei-Anhang. Stock-Bilder werden nicht mit eingepackt, da jederzeit neu abrufbar (siehe Bildspeicherung).
- **Verfügbare Größen (`verfuegbare_groessen`):** Informativ, keine Nutzer-Auswahl — Gemini liefert beim Hinzufügen (via Websuche) mit, in welchen Flakongrößen das Parfum vom Hersteller angeboten wird (z. B. "30ml, 50ml, 100ml"), sofern auffindbar. Reine Zusatzinfo zur Orientierung, kein Pflichtfeld.

---

## Offene Punkte / spätere Entscheidungen

- Genaues Prompt-Design für Gemini (welche Felder, wie strikt das JSON-Schema)
- Ob die eigene Abfüllgröße/Füllstand (z. B. bei Sample-Zerstäuber-Umfüllung, unabhängig von `verfuegbare_groessen`) separat getrackt werden soll — bewusst vertagt, keine Eile
