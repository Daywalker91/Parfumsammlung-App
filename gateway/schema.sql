-- Lizenz-Gateway-Schema (siehe Plan "Lizenz-Gateway für geteilten Claude-API-Zugang").
-- Einmalig auf der bestehenden MariaDB anzulegen, z. B.:
--   CREATE DATABASE parfumsammlung_gateway CHARACTER SET utf8mb4;
--   CREATE USER 'gateway'@'%' IDENTIFIED BY '<sicheres-passwort>';
--   GRANT ALL PRIVILEGES ON parfumsammlung_gateway.* TO 'gateway'@'%';
--   FLUSH PRIVILEGES;
-- Danach: mysql -u gateway -p parfumsammlung_gateway < schema.sql
--
-- MIGRATION (bestehende DB, "unlimitiert"-Feature): CREATE TABLE IF NOT EXISTS
-- aendert die Spalte auf einer bereits existierenden Tabelle nicht -- einmalig
-- manuell nachziehen:
--   ALTER TABLE access_codes MODIFY tageslimit_microcent BIGINT NULL DEFAULT 500000000;

CREATE TABLE IF NOT EXISTS api_keys (
  id INT AUTO_INCREMENT PRIMARY KEY,
  -- AES-256-GCM (IV(12) + Ciphertext + AuthTag(16)), Klartext existiert nur
  -- kurz im Gateway-Prozessspeicher direkt vor dem Forward-Call an Anthropic.
  key_verschluesselt VARBINARY(512) NOT NULL,
  label VARCHAR(255) NOT NULL,
  aktiv BOOLEAN NOT NULL DEFAULT TRUE,
  ist_standard BOOLEAN NOT NULL DEFAULT FALSE,
  erstellt_am DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS access_codes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(64) UNIQUE NOT NULL,
  label VARCHAR(255),
  aktiv BOOLEAN NOT NULL DEFAULT TRUE,
  -- Tagesbudget in Micro-Cent (1 Cent = 1.000.000 Microcent), Default 5,00 €.
  -- NULL = unlimitiert (Budget-Pruefung wird uebersprungen, siehe server.js).
  tageslimit_microcent BIGINT NULL DEFAULT 500000000,
  api_key_id INT NULL,
  erstellt_am DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_access_codes_api_key FOREIGN KEY (api_key_id) REFERENCES api_keys(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS nutzung (
  code_id INT NOT NULL,
  datum DATE NOT NULL,
  anzahl INT NOT NULL DEFAULT 0,
  kosten_microcent BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (code_id, datum),
  CONSTRAINT fk_nutzung_access_code FOREIGN KEY (code_id) REFERENCES access_codes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Singleton-Zeile (id immer 1) für globale, über /admin editierbare
-- Gateway-Einstellungen -- aktuell nur der Spenden-Link. Wird über
-- /v1/status an Clients ausgeliefert, die per Lizenzschlüssel laufen (siehe
-- Plan "PayPal-Link zentral am Gateway").
CREATE TABLE IF NOT EXISTS einstellungen (
  id INT PRIMARY KEY,
  spenden_link VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO einstellungen (id, spenden_link) VALUES (1, NULL);
