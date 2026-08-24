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
- `PFSENSE_CERT_PATH`/`PFSENSE_KEY_PATH` in `config.yaml` — auf dieser pfSense bereits bekannt
  (Dienste → ACME → General Settings → "Write ACME certificates to /conf/acme/" ist aktiv,
  Zertifikat heißt "Gateway-Cert"): `/conf/acme/Gateway-Cert.fullchain` (Leaf + Intermediate —
  robuster für Caddy als nur `.crt`) und `/conf/acme/Gateway-Cert.key`.

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
genau wie deine anderen Apps. Der Deploy-Workflow (`.github/workflows/deploy-gateway.yml`) baut
bei jedem Push auf `Gateway` (Pfad `gateway/**`) ein neues arm64-Image und schreibt den Tag
direkt in `gateway/k8s/deployment.yaml` zurück — ArgoCD synct diese Änderung dann wie jede andere.

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
