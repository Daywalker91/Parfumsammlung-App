# Parfum-App — CI/CD Plan (GitHub Actions)

*Ergänzung zu `Parfum-App_Plan.md`*

---

## Branch-Modell

`Stable` und `Experimental` als Git-Branches — Branches selbst sind reiner Entwicklungs-Workflow, entkoppelt von den tatsächlichen GitHub Releases, die die App über den Self-Update-Mechanismus abfragt (siehe Hauptplan, Kapitel „Self-Update über GitHub Releases").

---

## Auslöse-Logik

- **`Stable`:** Workflow läuft **automatisch** bei jedem Push → baut, signiert, erstellt normales GitHub Release
- **`Experimental`:** **kein** automatischer Trigger bei Push — Workflow wird bewusst manuell über `workflow_dispatch` (Actions-Tab → „Run workflow") ausgelöst → erstellt Pre-Release
- **Guard:** Der Job läuft nur, wenn der auslösende Branch `Stable` oder `Experimental` ist — verhindert einen versehentlichen (Pre-)Release, falls `workflow_dispatch` manuell von einem anderen Branch (z. B. einem Feature-Branch mit halbfertigem Code) gestartet wird

---

## Versionierung: X.Y-Schema ↔ Release-Tag ↔ versionCode

`versionCode` wird **nicht** manuell in `build.gradle` gepflegt. Stattdessen gilt ein zweiteiliges Versionsschema `X.Y`:

- **X** (Major) zählt bei jedem **Stable**-Release hoch
- **Y** (Minor) zählt bei jedem **Experimental**-Release hoch — **und wird bei jedem Stable-Release auf 0 zurückgesetzt**

Beispiel: `1.0` (erster Stable) → `1.1`, `1.2` (zwei Experimental-Builds danach) → `2.0` (nächster Stable, Y resettet) → `2.1` …

Damit zeigt die Versionsnummer auf einen Blick, wie viele Experimental-Builds seit dem letzten Stable-Release existieren. Quelle der Wahrheit sind Git-Tags im Format `vX.Y` — der Workflow ermittelt den letzten Tag, berechnet X/Y für den neuen Release und leitet daraus **Tag**, **`versionName`** (`"X.Y"`) und **`versionCode`** (`X * 10000 + Y`, als einzelne monoton steigende Ganzzahl für Android) ab. Damit sind Tag, `versionCode` und der Update-Vergleich in der App (siehe Hauptplan, „Self-Update über GitHub Releases", Schritt 2) immer zwangsläufig synchron.

**Gradle-Seite (`app/build.gradle.kts`):**
```kotlin
val ciVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val ciVersionName = project.findProperty("versionName") as String? ?: "0.0-dev"

android {
    defaultConfig {
        versionCode = ciVersionCode
        versionName = ciVersionName
    }
}
```
Ohne die `-P`-Properties (lokaler Dev-Build) greifen sinnvolle Defaults (`versionCode = 1`, `versionName = "0.0-dev"`).

---

## Workflow-Grundgerüst

`.github/workflows/build-release.yml`

```yaml
name: Build & Release

on:
  push:
    branches: [Stable]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    if: github.ref_name == 'Stable' || github.ref_name == 'Experimental'
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0   # volle Tag-Historie nötig, um den letzten vX.Y-Tag zu finden

      - name: Determine version (X.Y scheme)
        id: version
        run: |
          LAST=$(git tag -l 'v*.*' | sed 's/^v//' | sort -t. -k1,1n -k2,2n | tail -1)
          if [ -z "$LAST" ]; then X=0; Y=0; else X=${LAST%%.*}; Y=${LAST##*.}; fi

          if [ "${{ github.ref_name }}" = "Stable" ]; then
            X=$((X + 1)); Y=0
          else
            Y=$((Y + 1))
          fi

          echo "versionName=$X.$Y" >> "$GITHUB_OUTPUT"
          echo "versionCode=$((X * 10000 + Y))" >> "$GITHUB_OUTPUT"
          echo "tag=v$X.$Y" >> "$GITHUB_OUTPUT"

      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - name: Build release APK
        run: ./gradlew assembleRelease -PversionCode=${{ steps.version.outputs.versionCode }} -PversionName=${{ steps.version.outputs.versionName }}
      - uses: r0adkll/sign-android-release@v1
        with:
          releaseDirectory: app/build/outputs/apk/release
          signingKeyBase64: ${{ secrets.SIGNING_KEY }}
          alias: ${{ secrets.KEY_ALIAS }}
          keyStorePassword: ${{ secrets.KEYSTORE_PASSWORD }}
          keyPassword: ${{ secrets.KEY_PASSWORD }}
      - uses: softprops/action-gh-release@v2
        with:
          tag_name: ${{ steps.version.outputs.tag }}
          prerelease: ${{ github.ref_name == 'Experimental' }}
          files: app/build/outputs/apk/release/*.apk
```

**Verhalten**
- Push auf `Stable` → Workflow läuft automatisch → normales Release, X zählt hoch, Y resettet auf 0 (z. B. `2.3` → `3.0`)
- Auf `Experimental` → nichts passiert automatisch bei Push; manuell im Actions-Tab auslösen (Branch `Experimental` auswählen, „Run workflow" klicken) → Pre-Release, Y zählt hoch (z. B. `2.3` → `2.4`)
- Manueller Trigger von jedem anderen Branch → Job bricht sofort ab (Guard), kein Release
- `prerelease`-Flag entscheidet sich am `github.ref_name`, funktioniert für beide Auslöse-Arten gleich
- `versionCode` (`X * 10000 + Y`) steigt bei jedem Release strikt monoton — Voraussetzung dafür, dass Android das Update überhaupt installiert (siehe Hauptplan)

---

## Vorbereitung nötig

- **Signing-Keystore** als Base64-kodiertes GitHub Secret hinterlegen (`SIGNING_KEY`), plus `KEY_ALIAS`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD` als weitere Secrets — Keystore-Datei selbst landet nie im Repo
- ~~Versionierung aktuell über `github.run_number`~~ → geklärt, siehe Abschnitt „Versionierung" oben

---

## Zusammenhang mit Update-Kanal in der App

Der In-App-Umschalter (Einstellungen → Entwickler-Optionen → Stable/Experimental, siehe Hauptplan) bestimmt, welche Art von GitHub Release die App beim Selbst-Update abfragt:

- **Stable-Kanal** → nur normale Releases (`/releases/latest`)
- **Experimental-Kanal** → inkl. Pre-Releases (`/releases`, gefiltert auf `prerelease: true`)

Damit spiegelt der App-seitige Kanal-Umschalter genau die hier beschriebene Branch-/Release-Logik wider.

---

## Offene Punkte / spätere Entscheidungen

- Initiale Erzeugung des Signing-Keystores (`keytool`) und dessen sichere Aufbewahrung (Verlust = App kann sich nie wieder selbst updaten, siehe Hauptplan) — muss vor dem ersten Release geklärt sein, keine Eile für den Projektstart
