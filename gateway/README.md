# Lizenz-Gateway — Einrichtung

Siehe Haupt-Plan (`Parfum-App_CICD_Plan.md`-Nachbar-Dokumentation im Chat-Verlauf) für den
architektonischen Hintergrund. Diese Datei ist die konkrete Schritt-für-Schritt-Anleitung für
die einmalige Einrichtung auf deiner Infrastruktur.

## 1. MariaDB vorbereiten

Auf deiner bestehenden MariaDB-Instanz:

```sql
CREATE DATABASE parfumsammlung_gateway CHARACTER SET utf8mb4;
CREATE USER 'gateway'@'%' IDENTIFIED BY '<sicheres-passwort>';
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

## 3. Kubernetes-Secrets anlegen

Vier Secrets, keines davon im Git — alle einmalig manuell setzen (`-n aromathek-gateway` nicht vergessen):

```bash
# DB-Zugangsdaten
kubectl create secret generic gateway-db-credentials -n aromathek-gateway \
  --from-literal=user=gateway \
  --from-literal=password='<das-passwort-von-oben>'

# Admin-UI-Login (frei wählbar, langer zufälliger String empfohlen)
kubectl create secret generic gateway-admin-credentials -n aromathek-gateway \
  --from-literal=user=admin \
  --from-literal=password="$(openssl rand -base64 24)"

# AES-256-Schlüssel für die Anthropic-Key-Verschlüsselung in der DB
kubectl create secret generic gateway-enc-key -n aromathek-gateway \
  --from-literal=key="$(openssl rand -hex 32)"

# SSH-Key-Paar für den Cert-Sync (nur-lesender Zugriff auf die pfSense-Zertifikatsdateien)
ssh-keygen -t ed25519 -f /tmp/gateway-pfsense-key -N ""
kubectl create secret generic gateway-pfsense-ssh-key -n aromathek-gateway \
  --from-file=id_ed25519=/tmp/gateway-pfsense-key
# Den zugehörigen PUBLIC Key (/tmp/gateway-pfsense-key.pub) auf der pfSense unter
# System > User Manager als "Authorized Key" für einen eigens angelegten,
# eingeschränkten Nur-Lese-Nutzer hinterlegen.

# Verbindungsdaten für den Cert-Sync (Host + Nutzername + Dateipfade)
kubectl create secret generic gateway-pfsense-sftp -n aromathek-gateway \
  --from-literal=PFSENSE_HOST='<interne-oder-externe-pfSense-IP>' \
  --from-literal=PFSENSE_USER='<der-eben-angelegte-nutzer>' \
  --from-literal=PFSENSE_CERT_PATH='/conf/acme/Gateway-Cert.fullchain' \
  --from-literal=PFSENSE_KEY_PATH='/conf/acme/Gateway-Cert.key'
```

Die Pfade sind auf dieser pfSense bereits bekannt (Dienste → ACME → General Settings →
"Write ACME certificates to /conf/acme/" ist aktiv, Zertifikat heißt "Gateway-Cert"):
`ls -la /conf/acme/` zeigt `Gateway-Cert.crt` (nur Leaf-Zertifikat), `Gateway-Cert.fullchain`
(Leaf + Intermediate — das nehmen wir für Caddy, robuster gegenüber Clients, die die
Zwischenzertifikate nicht selbst nachladen) und `Gateway-Cert.key` (Private Key). Falls du das
Zertifikat mal umbenennst, ändern sich diese Pfade entsprechend (`<neuer-name>.fullchain`/`.key`).

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

**Wichtig — Bootstrap-Reihenfolge:** Namespace (Schritt 2) und die vier Secrets (Schritt 3)
liegen bewusst NICHT in Git (siehe dort) und müssen deshalb einmalig manuell gesetzt sein,
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
