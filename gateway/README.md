# Lizenz-Gateway — Einrichtung

Siehe Haupt-Plan (`Parfum-App_CICD_Plan.md`-Nachbar-Dokumentation im Chat-Verlauf) für den
architektonischen Hintergrund. Diese Datei ist die konkrete Schritt-für-Schritt-Anleitung für
die einmalige Einrichtung auf deiner Infrastruktur.

## 1. MariaDB vorbereiten

Auf deiner bestehenden MariaDB-Instanz:

```sql
CREATE DATABASE parfumsammlung_gateway CHARACTER SET utf8mb4;
CREATE USER 'gateway'@'%' IDENTIFIED BY '<dasselbe-passwort-wie-DB_PASSWORD-in-secret.yaml>';
GRANT ALL PRIVILEGES ON parfumsammlung_gateway.* TO 'gateway'@'%';
FLUSH PRIVILEGES;
```

Danach das Schema einspielen:

```bash
mysql -h <mariadb-host> -u gateway -p parfumsammlung_gateway < schema.sql
```

## 2. Namespace anlegen

Eigener Namespace statt `default`, isoliert den Gateway (Secrets, RBAC-Scope) von deinen anderen
Workloads:

```bash
kubectl apply -f k8s/namespace.yaml
```

Alle Manifeste in `gateway/k8s/` haben `namespace: aromathek-gateway` bereits fest gesetzt.

## 3. Secrets/Config befüllen

Drei Vorlagen liegen in `gateway/k8s/` (Platzhalter-Werte, **niemals hier mit echten Werten
committen** — dieses Repo ist öffentlich):

- `config.yaml` — ConfigMap `gateway-config`, alles was kein Geheimnis ist (Port, DB-Host/-Name,
  pfSense-Host + Zertifikatspfade fürs Cert-Sync).
- `secret.yaml` — `type: Opaque`, DB-/Admin-Zugangsdaten, der AES-Schlüssel für die Anthropic-
  Key-Verschlüsselung und der pfSense-SFTP-Nutzername.
- `secret-ssh.yaml` — `type: kubernetes.io/ssh-auth`, der private SSH-Key für den nur-lesenden
  Cert-Sync-Zugriff auf die pfSense (Schlüsselname `ssh-privatekey` ist von Kubernetes für
  diesen Secret-Typ vorgeschrieben).

Echte Werte kommen in deine private Kopie dieser drei Dateien in `Repository` (dort liegen z. B.
schon `Smart Home/InfluxDB/config.yaml`/`secret.yaml` nach demselben Muster) — Struktur/Keys
1:1 übernehmen, nur `data`/`stringData` befüllen:

- `DB_USER`/`DB_PASSWORD` — dasselbe Passwort wie in Schritt 1 bei `CREATE USER`.
- `ADMIN_USER`/`ADMIN_PASSWORD` — Login fürs `/admin`-Dashboard, langer zufälliger String empfohlen
  (`openssl rand -base64 24`).
- `GATEWAY_ENC_KEY` — `openssl rand -hex 32`.
- `PFSENSE_HOST`/`PFSENSE_USER` — IP/Hostname + ein eigens angelegter, eingeschränkter
  Nur-Lese-Nutzer auf der pfSense (System > User Manager).
- `ssh-privatekey` in `secret-ssh.yaml` — `ssh-keygen -t ed25519 -f gateway-pfsense-key -N ""`,
  den Inhalt der privaten Schlüsseldatei rein, den zugehörigen PUBLIC Key (`.pub`) auf der
  pfSense beim eben angelegten Nutzer als "Authorized Key" hinterlegen.
- `PFSENSE_CERT_PATH`/`PFSENSE_KEY_PATH` in `config.yaml` — **relativ zum Jail-Root** des
  chrooteten scp-Nutzers (siehe unten), also `Gateway-Cert.fullchain`/`Gateway-Cert.key`, NICHT
  der absolute `/conf/acme/...`-Pfad (da kommt der Nutzer nicht raus).

### SSH-Nutzer für den Cert-Sync (live eingerichtet, hier dokumentiert)

Live getestet und funktionsfähig:

1. System → User Manager → neuen Nutzer anlegen (kein Passwort, keine `admins`-Gruppe), Public
   Key ins Feld "Authorized SSH Keys".
2. Effective Privileges → **"User - System: Copy files to home directory (chrooted scp)"**
   hinzufügen — **nicht** "Copy files (scp)" (das ist laut pfSense selbst ein
   Administratorrecht, volles Dateisystem statt Jail).
3. Chroot einmalig aufbauen (Diagnose → Kommandozeile):
   ```
   /usr/local/etc/rc.d/scponlyc enable
   /usr/local/etc/rc.d/scponlyc start
   ```
   Kommt beim ersten `start` ein Fehler wie `rmdir: /home/<user>/dev: No such file or directory`,
   den fehlenden Ordner einmalig manuell anlegen (`mkdir -p /home/<user>/dev`) und `start` erneut
   ausführen — danach läuft's.
4. **Wichtig — nur `scp`, kein `sftp`:** live getestet, der SFTP-Subsystem-Aufruf scheitert im
   Jail sofort mit `Exit status 1` (`scponly` unterstützt standardmäßig kein SFTP, nur SCP). Das
   Sync-Script (`configmap-cert-sync-script.yaml`) nutzt deshalb `scp`, nicht `sftp`.
5. Da der Nutzer im Jail nicht an `/conf/acme/` rankommt: in der ACME-Zertifikatskonfiguration
   (Dienste → ACME → dein Zertifikat → Actions/Automations) eine **Post-Renewal-Aktion**
   einrichten, die `Gateway-Cert.fullchain`/`Gateway-Cert.key` nach jeder Erneuerung ins
   Home-Verzeichnis dieses Nutzers kopiert (Jail-Root).

Danach `kubectl apply -f config.yaml -f secret.yaml -f secret-ssh.yaml -n aromathek-gateway`
aus deiner privaten Kopie — oder, sobald die ArgoCD-Application dafür existiert, einfach committen
und syncen lassen.

## 4. Wildcard-Zertifikat in der pfSense beantragen

System > ACME Certificates > neues Zertifikat für `*.dornbirn.ipv64.net`, DNS-01-Validierung
über die ipv64.net-API (Zugangsdaten dafür hast du schon, siehe Dynamic-DNS-Konfiguration).
Kein Port 80 nötig.

## 5. Manifeste ausrollen — über ArgoCD (empfohlen, passend zu deinem Setup)

Da du Deployments schon über ArgoCD aus Git-Pfaden synct: eine neue Application anlegen, die
auf `gateway/k8s/` in diesem Repo zeigt, **Branch `Gateway`** — eigener Branch statt Stable/
Experimental, weil App-Releases und Gateway-Deploys unterschiedliche Lebenszyklen haben (ein
App-Release soll nicht durch eine reine Gateway-Änderung ausgelöst werden und umgekehrt).
Gateway-Entwicklung läuft weiter ganz normal auf `Experimental`; ein Merge nach `Gateway` ist
der eigentliche "Jetzt deployen"-Schritt (gleiches Prinzip wie Experimental → Stable bei der App).
ArgoCD zieht sich dann RBAC, ConfigMaps, CronJob, Deployment und Service von dort automatisch,
genau wie deine anderen Apps.

**Versionierung — dasselbe X.Y-Schema wie bei der App** (siehe `Parfum-App_CICD_Plan.md`), eigener
Tag-Namensraum `gateway-vX.Y` (kollidiert nicht mit den App-eigenen `vX.Y`-Git-Tags im selben
Repo):
- Push auf `Gateway` → **automatisch** (wie `Stable` bei der App): X hoch, Y auf 0, Image-Tags
  `stable`, `latest`, `<X>.<Y>` (+ immer der Commit-SHA).
- `Experimental` → **nur manuell** per `workflow_dispatch` (wie bei der App, Actions-Tab → "Run
  workflow" → Branch `Experimental` auswählen): Y hoch, Image-Tag `unstable` + `<X>.<Y>`, rührt
  `stable`/`latest` nicht an.

Das Deployment referenziert unabhängig davon dauerhaft `:latest` (`imagePullPolicy: Always`) —
das Manifest ändert sich dadurch nie, kein Zurückschreiben in Git nötig, kein Auseinanderlaufen
zwischen dieser Vorlage und einer privaten Kopie davon mehr möglich (genau das ist uns beim
ersten Rollout live passiert, siehe Fix-Commit). Die `stable`/`unstable`/`<X>.<Y>`-Tags sind fürs
manuelle Nachvollziehen/Pinnen/Rollback da, nicht für den automatischen Rollout.

**Kehrseite:** ArgoCD sieht bei einem neuen `:latest`-Build keine Text-Änderung im Manifest und
synct deshalb nichts automatisch neu aus. Nach einem Gateway-Code-Update muss der Pod manuell
neu gestartet werden, damit er das frische Image zieht:
```bash
kubectl rollout restart deployment/gateway -n aromathek-gateway
```

**Wichtig — Bootstrap-Reihenfolge:** Namespace (Schritt 2) und die echt befüllten Config-/
Secret-Dateien (Schritt 3) liegen bewusst NICHT in diesem Repo (siehe dort) und müssen deshalb
einmalig manuell gesetzt sein,
*bevor* ArgoCD synct, sonst startet die Gateway-Pod nicht (fehlende Env-Vars) bzw. Caddy findet
kein Zertifikat. Der Cert-Sync-CronJob braucht außerdem einen ersten manuellen Lauf, damit das
`gateway-tls`-Secret überhaupt existiert:

```bash
kubectl create job --from=cronjob/gateway-cert-sync gateway-cert-sync-manuell -n aromathek-gateway
kubectl logs -f job/gateway-cert-sync-manuell -n aromathek-gateway
kubectl get secret gateway-tls -n aromathek-gateway   # muss jetzt existieren
```

Bis dahin zeigt ArgoCD die Gateway-Application ggf. als "Degraded" (Pod pending/crashloop wegen
fehlendem Secret) — das legt sich von selbst, sobald der CronJob einmal durchgelaufen ist.

**Alternative ohne ArgoCD** (falls du es doch lieber klassisch von Hand machen willst):

```bash
kubectl apply -f k8s/rbac-cert-sync.yaml
kubectl apply -f k8s/configmap-cert-sync-script.yaml
kubectl apply -f k8s/cronjob-cert-sync.yaml
kubectl create job --from=cronjob/gateway-cert-sync gateway-cert-sync-manuell -n aromathek-gateway
kubectl logs -f job/gateway-cert-sync-manuell -n aromathek-gateway
kubectl apply -f k8s/configmap-caddyfile.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/deployment.yaml
```
(`config.yaml`/`secret.yaml`/`secret-ssh.yaml` aus deiner privaten, echt befüllten Kopie kommen
vor `deployment.yaml`/`cronjob-cert-sync.yaml` dran, sonst fehlen den Pods die Env-Vars.)

## 6. Netzwerk

```bash
kubectl get svc gateway -n aromathek-gateway   # MetalLB-IP notieren
```

NAT-Portweiterleitung 443 → diese IP in der pfSense ergänzen (neue, separate Regel).

## 7. Erste Einträge über die Admin-Oberfläche

`https://gateway.dornbirn.ipv64.net/admin` öffnen (Basic-Auth-Login aus Schritt 3), dort:

1. Deinen echten Anthropic-Key eintragen (wird sofort verschlüsselt gespeichert) und über
   "Als Standard" markieren.
2. Einen ersten Zugangs-Code erzeugen (z. B. Label "Test") und persönlich weitergeben bzw.
   selbst in der App unter Einstellungen → Lizenzschlüssel eintragen.

## 8. App-seitige Build-Property

`GATEWAY_BASE_URL` als GitHub Secret im App-Repo hinterlegen (Wert: `https://gateway.dornbirn.ipv64.net`),
`build-release.yml` liest es automatisch (siehe `-PgatewayBaseUrl`).
