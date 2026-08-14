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

## Versionierung: versionCode ↔ Release-Tag

`versionCode` wird **nicht** manuell in `build.gradle` gepflegt, sondern beim CI-Build automatisch aus `github.run_number` gesetzt — dieselbe Zahl, die auch als Release-Tag verwendet wird. Damit sind Tag, `versionCode` und der Update-Vergleich in der App (siehe Hauptplan, „Self-Update über GitHub Releases", Schritt 2) immer zwangsläufig synchron, kein manuelles Nachpflegen nötig, kein Risiko einer vergessenen Erhöhung.

**Gradle-Seite (`app/build.gradle.kts`, später beim Scaffolding umzusetzen):**
```kotlin
val ciVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val ciVersionName = project.findProperty("versionName") as String? ?: "0.1.0-dev"

android {
    defaultConfig {
        versionCode = ciVersionCode
        versionName = ciVersionName
    }
}
```
Ohne die `-P`-Properties (lokaler Dev-Build) greifen sinnvolle Defaults (`versionCode = 1`, `versionName = "0.1.0-dev"`).

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
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - name: Build release APK
        run: ./gradlew assembleRelease -PversionCode=${{ github.run_number }} -PversionName=1.0.${{ github.run_number }}
      - uses: r0adkll/sign-android-release@v1
        with:
          releaseDirectory: app/build/outputs/apk/release
          signingKeyBase64: ${{ secrets.SIGNING_KEY }}
          alias: ${{ secrets.KEY_ALIAS }}
          keyStorePassword: ${{ secrets.KEYSTORE_PASSWORD }}
          keyPassword: ${{ secrets.KEY_PASSWORD }}
      - uses: softprops/action-gh-release@v2
        with:
          tag_name: v${{ github.run_number }}
          prerelease: ${{ github.ref_name == 'Experimental' }}
          files: app/build/outputs/apk/release/*.apk
```

**Verhalten**
- Push auf `Stable` → Workflow läuft automatisch → normales Release, `versionCode`/Tag = aktuelle `run_number`
- Auf `Experimental` → nichts passiert automatisch bei Push; manuell im Actions-Tab auslösen (Branch `Experimental` auswählen, „Run workflow" klicken) → Pre-Release wird erstellt
- Manueller Trigger von jedem anderen Branch → Job bricht sofort ab (Guard), kein Release
- `prerelease`-Flag entscheidet sich am `github.ref_name`, funktioniert für beide Auslöse-Arten gleich

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
