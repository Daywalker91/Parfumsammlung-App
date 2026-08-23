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

## 2. Kubernetes-Secrets anlegen

Vier Secrets, keines davon im Git — alle einmalig manuell setzen:

```bash
# DB-Zugangsdaten
kubectl create secret generic gateway-db-credentials \
  --from-literal=user=gateway \
  --from-literal=password='<das-passwort-von-oben>'

# Admin-UI-Login (frei wählbar, langer zufälliger String empfohlen)
kubectl create secret generic gateway-admin-credentials \
  --from-literal=user=admin \
  --from-literal=password="$(openssl rand -base64 24)"

# AES-256-Schlüssel für die Anthropic-Key-Verschlüsselung in der DB
kubectl create secret generic gateway-enc-key \
  --from-literal=key="$(openssl rand -hex 32)"

# SSH-Key-Paar für den Cert-Sync (nur-lesender Zugriff auf die pfSense-Zertifikatsdateien)
ssh-keygen -t ed25519 -f /tmp/gateway-pfsense-key -N ""
kubectl create secret generic gateway-pfsense-ssh-key \
  --from-file=id_ed25519=/tmp/gateway-pfsense-key
# Den zugehörigen PUBLIC Key (/tmp/gateway-pfsense-key.pub) auf der pfSense unter
# System > User Manager als "Authorized Key" für einen eigens angelegten,
# eingeschränkten Nur-Lese-Nutzer hinterlegen.

# Verbindungsdaten für den Cert-Sync (Host + Nutzername + Dateipfade)
kubectl create secret generic gateway-pfsense-sftp \
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

## 3. Wildcard-Zertifikat in der pfSense beantragen

System > ACME Certificates > neues Zertifikat für `*.dornbirn.ipv64.net`, DNS-01-Validierung
über die ipv64.net-API (Zugangsdaten dafür hast du schon, siehe Dynamic-DNS-Konfiguration).
Kein Port 80 nötig.

## 4. RBAC + Cert-Sync-CronJob zuerst anwenden

```bash
kubectl apply -f k8s/rbac-cert-sync.yaml
kubectl apply -f k8s/configmap-cert-sync-script.yaml
kubectl apply -f k8s/cronjob-cert-sync.yaml
# Einmal manuell anstoßen, statt auf den nächtlichen Zeitplan zu warten:
kubectl create job --from=cronjob/gateway-cert-sync gateway-cert-sync-manuell
kubectl logs -f job/gateway-cert-sync-manuell
```

Danach muss `kubectl get secret gateway-tls` existieren, bevor die Gateway-Pod startet
(sie mountet dieses Secret für Caddy).

## 5. Restliche Manifeste anwenden

```bash
kubectl apply -f k8s/configmap-caddyfile.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/deployment.yaml
kubectl get svc gateway   # MetalLB-IP notieren
```

NAT-Portweiterleitung 443 → diese IP in der pfSense ergänzen (neue, separate Regel).

## 6. Erste Einträge über die Admin-Oberfläche

`https://gateway.dornbirn.ipv64.net/admin` öffnen (Basic-Auth-Login aus Schritt 2), dort:

1. Deinen echten Anthropic-Key eintragen (wird sofort verschlüsselt gespeichert) und über
   "Als Standard" markieren.
2. Einen ersten Zugangs-Code erzeugen (z. B. Label "Test") und persönlich weitergeben bzw.
   selbst in der App unter Einstellungen → Lizenzschlüssel eintragen.

## 7. App-seitige Build-Property

`GATEWAY_BASE_URL` als GitHub Secret im App-Repo hinterlegen (Wert: `https://gateway.dornbirn.ipv64.net`),
`build-release.yml` liest es automatisch (siehe `-PgatewayBaseUrl`).
