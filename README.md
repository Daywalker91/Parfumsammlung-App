# Aromathek — Parfüm-Datenbank App

*Private Android-App zum Verwalten einer Parfum-Sammlung — kein Play-Store-Release, Verteilung per Sideload.*

Native Android-App (Kotlin, Jetpack Compose, Room) zur Erfassung vorhandener Parfums inkl. Duftpyramide, mit Foto-basierter Erkennung neuer Parfums über die Gemini API, optionalem Barcode-Scan, getrennter Wunschliste und Self-Update über GitHub Releases. Kein eigenes Backend — alles läuft lokal auf dem Gerät.

## Dokumentation

- [Parfum-App_Plan.md](Parfum-App_Plan.md) — Hauptkonzept: Datenmodell, Gemini-Integration, Self-Update-Mechanismus, Entwickler-Optionen, Build-Voraussetzungen
- [Parfum-App_CICD_Plan.md](Parfum-App_CICD_Plan.md) — CI/CD-Pipeline (GitHub Actions): Branch-Modell, Signing, Release-Erstellung

## Branch-Modell

- `main` — Entwicklungsstand
- `Stable` — Push löst automatisch einen signierten Build + normales GitHub Release aus
- `Experimental` — Release nur per manuellem `workflow_dispatch`, erzeugt ein Pre-Release

## Build

Voraussetzungen: JDK 17, Android SDK (Command-Line Tools, Platform-Tools, Build-Tools, Platform android-36) — siehe Plan-Dokument, Kapitel „Voraussetzungen für Build & Test", für Details und Setup-Status.

```
./gradlew assembleDebug
```

APK landet unter `app/build/outputs/apk/debug/`.

## Lizenz

Siehe [LICENSE](LICENSE) — alle Rechte vorbehalten, Quellcode ist öffentlich einsehbar, aber nicht zur freien Nutzung freigegeben.
