# MediVault Secure Software Project

## Team Members

- **Bosschem Nicolas** - 000575046
- **Bui The** - 000546997
- **Fernandez Ojeda Franklin** - 000541971
- **Herbiet Dorian** - 000513131
- **Otto Aleksandra** - 000569128
- **Rafaat Moheeb Eskandar Abanoub** - 000567614

---

## Project Overview

MediVault is an end-to-end encrypted web application designed for sensitive medical data management.  
It implements passwordless authentication using Webauthn for patients, PKI-based doctor identity, audit logging, and bot protection.

This README provides a **global setup guide** and directs you to specific documentation for each security component.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Project Structure](#project-structure)
3. [Setup Guide](#setup-guide)
    - [1. PKI Infrastructure & Doctor Certificates](#1-pki-infrastructure--doctor-certificates)
    - [2. Server SSL Configuration](#2-server-ssl-configuration)
    - [3. hCaptcha Integration](#3-hcaptcha-integration)
    - [4. Audit Logging](#4-audit-logging)
    - [5. Elasticsearch & Kibana](#5-elasticsearch--kibana)
4. [Running the Project](#running-the-project)
5. [Environment Variables](#environment-variables)


---

## Prerequisites

- **Docker & Docker Compose** installed
- **Java 21** (for local builds)
- **OpenSSL** and **keytool** (for PKI operations)
- **Node.js**

---

## Project Structure

```
projectssd/
├── pki/                # PKI infrastructure (doctor certificates, CA)
├── server/             # Spring Boot backend
├── logserver/          # Audit log server
├── docker-compose.yml  # Multi-service orchestration
├── .env                # Environment variables
└── README.md           # global README
```

---

## Setup Guide

### 1. PKI Infrastructure & Doctor Certificates

- **Purpose:** Secure doctor authentication using short-lived certificates.
- **How to:**  
  See [documentation/README_pki.md](documentation/README_pki.md) for:
  - Initializing the PKI
  - Issuing new doctor certificates
  - Renewing certificates
  - Directory structure and security notes

**Quick Start:**
```sh
cd pki/scripts
./init-pki.sh                    # Initialize PKI (run once)
./issue-doctor-cert.sh "Dr. Name" "Hospital"   # Issue doctor certificate
```
See [documentation/README_pki.md](documentation/README_pki.md) for details.

---

### 2. Server SSL Configuration

- **Purpose:** Enable HTTPS and client certificate authentication.
- **How to:**  
  See [pki/README.md](pki/README.md) for:
  - Creating the server keystore (`medivault.p12`)
  - Creating the truststore (`truststore.p12`)
  - Importing CA certificates

**Quick Start:**
```sh
cd server/src/main/resources
keytool -genkeypair ...              # Create server certificate
keytool -importcert ...              # Import CA to truststore
```
See [documentation/README_pki](documentation/README_pki.md) for full commands.

---

### 3. hCaptcha Integration

- **Purpose:** Protect sensitive endpoints from bots and automated abuse.
- **How to:**  
  See [documentation/README_hcaptcha.md](documentation/README_hcaptcha.md) for:
  - How hCaptcha works
  - Key setup (development vs production)
  - Server-side verification workflow

**Quick Start:**
- Obtain hCaptcha keys from [hcaptcha.com](https://www.hcaptcha.com/)
- Set `HCAPTCHA_SECRET` in `.env`
- See [documentation/README_hcaptcha.md](documentation/README_hcaptcha.md) for integration details

---

### 4. Audit Logging

- **Purpose:** Record all sensitive actions for accountability and security monitoring.
- **How to:**  
  See [documentation/README_docker&Logs.md](documentation/README_docker&Logs.md) for:
  - Audit log format
  - HMAC signing
  - Log shipping to Elasticsearch

---

### 5. Elasticsearch & Kibana

- **Purpose:** Store and visualize audit logs and application events.
- **How to:**  
  See [documentation/README_docker&Logs.md](documentation/README_docker&Logs.md) for:
  - Service configuration
  - Accessing Kibana dashboard
  - Security notes

---

## Running the Project

1. **Set up PKI and certificates** as described above.
2. **Configure environment variables** in `.env` (see below).
3. **Build and start all services:**
   ```sh
   export HCAPTCHA_SECRET={HCAPTCHA_SECRET}
   docker compose build && docker compose up
   ```
4. **Access the application:**
   - Backend: https://localhost:8443
   - Kibana: http://localhost:5601
   - Logserver : http://localhost:5555
   - ElasticSearch : http://localhost:9200


---

## Environment Variables

Edit `.env` to configure secrets and paths:

| Variable                  | Description                          |
|---------------------------|--------------------------------------|
| SSL_KEYSTORE_LOCATION     | Path to server keystore (.p12)       |
| SSL_KEYSTORE_PASSWORD     | Keystore password                    |
| SSL_TRUSTSTORE_LOCATION   | Path to truststore (.p12)            |
| SSL_TRUSTSTORE_PASSWORD   | Truststore password                  |
| HCAPTCHA_SECRET           | hCaptcha secret key                  |
| ELASTIC_PASSWORD          | Elasticsearch password               |
| ELASTICSEARCH_USERNAME    | Elasticsearch username               |
| ELASTICSEARCH_PASSWORD    | Elasticsearch password               |
| AUDIT_HMAC_KEY            | Audit log HMAC signing key           |
| KIBANA_ENCRYPTION_KEY     | Kibana session encryption key        |

---

> **Note:**  
> Firefox does not support WebAuthn when using a self-signed certificate.  
> For testing WebAuthn features with self-signed certificates, it is recommended to use the Brave or Chrome browser instead.

**For any component-specific instructions, always refer to the README in the corresponding directory.**
