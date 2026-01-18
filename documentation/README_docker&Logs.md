# MediVault - Secure Medical Records Platform

[![Docker](https://img.shields.io/badge/Docker-20.10+-blue.svg)](https://www.docker.com/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green.svg)](https://spring.io/projects/spring-boot)
[![Elasticsearch](https://img.shields.io/badge/Elasticsearch-8.11-orange.svg)](https://www.elastic.co/)

Complete infrastructure setup with audit logging, anomaly detection

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     MediVault Infrastructure                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐      ┌──────────────┐      ┌──────────────┐   │
│  │   Browser    │─────▶│  MediVault   │─────▶│  LogServer   │   │
│  │  (HTTPS +    │      │    Server    │      │   (TCP)      │   │
│  │ Client Cert) │      │  (Port 8443) │      │  (Port 5555) │   │
│  └──────────────┘      └──────────────┘      └──────┬───────┘   │
│         │                      │                     │          │
│         │                      │                     ▼          │
│         │                      │              ┌──────────────┐  │
│         │                      │              │  audit.log   │  │
│         │                      │              │  (pipe-      │  │
│         │                      │              │  delimited)  │  │
│         │                      │              └──────┬───────┘  │
│         │                      │                     │          │
│         │                      │                     ▼          │
│         │                      │              ┌──────────────┐  │
│         │                      │              │   Filebeat   │  │
│         │                      │              │  (Parser +   │  │
│         │                      │              │   Shipper)   │  │
│         │                      │              └──────┬───────┘  │
│         │                      │                     │          │
│         │                      │                     ▼          │
│         │                      │              ┌──────────────┐  │
│         │                      └─────────────▶│Elasticsearch │  │
│         │                                     │ (Port 9200)  │  │
│         │                                     └──────┬───────┘  │
│         │                                            │          │
│         │                                            ▼          │
│         │                                     ┌──────────────┐  │
│         └────────────────────────────────────▶│    Kibana    │  │
│                                               │ (Port 5601)  │  │
│                                               │ - Dashboards │  │
│                                               │ - Alerts     │  │
│                                               │ - ML Jobs    │  │
│                                               └──────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Data Flow

1. **User → MediVault Server**: HTTPS with mutual TLS (client certificate authentication)
2. **MediVault → LogServer**: TCP socket with structured audit logs
3. **LogServer → audit.log**: Pipe-delimited format with blockchain hash chain
4. **Filebeat → Elasticsearch**: Parses and indexes logs in real-time
5. **Kibana**: Visualization, alerting, and ML-based anomaly detection

---

## Features

### Audit Logging
- **Comprehensive Logging**: All actions tracked with HMAC-SHA256
- **Real-time Processing**: Filebeat ships logs to Elasticsearch
- **Advanced Search**: Full-text search and aggregations
- **Dashboards**: Pre-built security dashboards in Kibana
---

### Why did we choose this architecture?

We separated logging infrastructure from the application server to:
- **Isolate failures**: If the app crashes, logs remain intact
- **Scalability**: LogServer can handle multiple applications
- **Security**: Audit logs cannot be tampered by compromised app code
- **Performance**: Async TCP logging doesn't block application requests

#### Elastic stack
| Feature | Benefit | Use Case |
|---------|---------|----------|
| **Full-text search** | Find logs in milliseconds | Search for patient ID across 1M logs |
| **Aggregations** | Statistical analysis | "Top 10 users by failed logins" |
| **Real-time alerts** | Instant threat detection | Brute force attack in progress |
| **Machine Learning** | Anomaly detection | Unusual IP address for user |
| **Dashboards** | Visual security posture | Executive security reports |
| **Scalability** | Handle millions of logs/day | Production-ready |

#### Filebeat
We use Filebeat as a Log shipper, there are several benefits by using it:
- File acts as persistent buffer
- Filebeat handles retries automatically
- Parsing logic centralized in Filebeat
- Can replay logs if ES fails


---
### Install Docker & Docker Compose

```bash
# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Add user to docker group
sudo usermod -aG docker $USER
newgrp docker

# Verify installation
docker --version
docker compose version
```

---

### Requirement before building
There are two setup that you need to do before building and launching the services:
- **HTTPS Certificate** : Link to HTTPS Certificate readMe
-  **PKI Setup** : Link to PKI readME

You also need to set your environnement variable in the file `env.`


### Build and Start Services

```bash
cd ~/projectssd

# Build all services
sudo docker compose build

# Start all services
sudo docker compose up -d

# Check status
sudo docker compose ps
```

### Verify Installation

```bash
# Check Elasticsearch
curl http://localhost:9200/_cluster/health?pretty

# Check Kibana (wait ~60s for startup)
curl http://localhost:5601/api/status

# Check MediVault server
curl -k https://localhost:8443/actuator/health
```

### Access Applications

| Service | URL | Credentials |
|---------|-----|-------------|
| **MediVault** | https://localhost:8443 | Client certificate |
| **Kibana** | http://localhost:5601 | yourname / changeme |
| **Elasticsearch** | http://localhost:9200 | yourname / changeme |

---

## Service Configuration

**Dockerfile**:
```dockerfile
# Build
FROM gradle:8.10.2-jdk21 AS build

WORKDIR /app

# Copy Gradle files
COPY build.gradle settings.gradle gradlew ./
COPY gradle gradle/

# Copy source code
COPY src src/

# Build Spring Boot application
RUN ./gradlew clean bootJar -x test

# Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the Spring Boot JAR
COPY --from=build /app/build/libs/*.jar app.jar

# CORRECTION: Copier le certificat depuis le contexte (pas depuis build stage)
COPY src/main/resources/medivault.p12 /app/medivault.p12
COPY src/main/resources/pki/ /app/pki/

# Expose HTTPS port
EXPOSE 8443

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Docker Compose Configuration**:
```yaml
  medivault-server:
    build:
      context: ./server
      dockerfile: Dockerfile
    ports:
      - "8443:8443"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - HCAPTCHA_SECRET=${HCAPTCHA_SECRET}
      - LOG_SERVER_HOST=logserver
      - LOG_SERVER_PORT=5555
      - SSL_KEYSTORE_LOCATION=${SSL_KEYSTORE_LOCATION}
      - SSL_KEYSTORE_PASSWORD=${SSL_KEYSTORE_PASSWORD}
      - SSL_TRUSTSTORE_LOCATION=${SSL_TRUSTSTORE_LOCATION}
      - SSL_TRUSTSTORE_PASSWORD=${SSL_TRUSTSTORE_PASSWORD}
    depends_on:
      - logserver
    volumes:
      - ./server/demo:/app/demo
    networks:
      - medivault-network
    restart: unless-stopped
```
**Environment Variables**:

| Variable | Purpose | Example |
|----------|---------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring Boot profile | `production` |
| `HCAPTCHA_SECRET` | CAPTCHA validation key | `ES_c9ae3ffa...` |
| `LOG_SERVER_HOST` | LogServer hostname | `logserver` |
| `LOG_SERVER_PORT` | LogServer TCP port | `5555` |
| `SSL_KEYSTORE_LOCATION` | Server certificate path | `medivault.p12` |
| `SSL_KEYSTORE_PASSWORD` | Keystore password | `password` |
| `SSL_TRUSTSTORE_LOCATION` | Trusted CA certificates | `pki/truststore.p12` |
| `SSL_TRUSTSTORE_PASSWORD` | Truststore password | `changeit` |
| `AUDIT_HMAC_KEY` | Hash chain secret key | `supersecurekey` |
**Key Features**:
- TLS 1.3 with client certificate authentication
- H2 embedded database
- Real-time audit logging to LogServer

---

### LogServer

**Purpose**: Receives audit logs via TCP and writes to file with hash chain

**Dockerfile**:
```dockerfile
# Build
FROM gradle:8.10.2-jdk21 AS build

WORKDIR /app

# Copy Gradle files
COPY build.gradle settings.gradle gradlew ./
COPY gradle gradle/

# Copy source code
COPY src src/

# Build fat JAR
RUN ./gradlew clean fatJar -x test

# Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the fat JAR
COPY --from=build /app/build/libs/*-all.jar ./logserver.jar

# Expose port
EXPOSE 5555

# Run the application
ENTRYPOINT ["java", "-jar", "logserver.jar"]
```

**Log Format**:
```
timestamp|severity|ip|actor|action|target|extra_data|previous_hash|hash
```

**Example Entry**:
```
1768513669027|INFO|172.23.0.1|Patient:ad92551f|PATIENT_START_REGISTRATION|-|-|GENESIS|VbGa2Ona...
```

---

### Filebeat

**Purpose**: Parse and ship audit logs to Elasticsearch

**Configuration** (`logs/filebeat/filebeat.yml`):
```yaml
filebeat.inputs:
  - type: filestream
    id: audit-log
    enabled: true
    paths:
      - /usr/share/filebeat/logs/audit.log
    fields:
      log_type: audit
      application: medivault
    fields_under_root: true

processors:
  - dissect:
      tokenizer: "%{audit.timestamp}|%{audit.severity_level}|%{audit.remote_ip}|%{audit.actor}|%{audit.action}|%{audit.target}|%{audit.extra_data}|%{audit.previous_hash}|%{audit.hash}"
      field: "message"
      target_prefix: ""
  
  - timestamp:
      field: audit.timestamp
      layouts:
        - UNIX_MS
      target_field: "@timestamp"
  
  - drop_fields:
      fields: ["agent.ephemeral_id", "agent.id", "ecs.version"]
      ignore_missing: true

output.elasticsearch:
  hosts: ["http://elasticsearch:9200"]
  username: "${ELASTICSEARCH_USERNAME}"
  password: "${ELASTICSEARCH_PASSWORD}"
  index: "audit-logs-%{+yyyy.MM.dd}"

setup.ilm.enabled: false
setup.template.enabled: false
```

**Parsed Output**:
```json
{
  "@timestamp": "2026-01-17T10:05:30.000Z",
  "audit": {
    "timestamp": "1768513530000",
    "severity_level": "INFO",
    "remote_ip": "172.23.0.1",
    "actor": "Patient:ad92551f",
    "action": "PATIENT_FINISH_REGISTRATION",
    "target": "-",
    "extra_data": "-",
    "previous_hash": "VbGa2Onaxt4e...",
    "hash": "SnychPDiKFAb..."
  }
}
```
---

### Elasticsearch

**Purpose**: Store and index audit logs

**Configuration**:
```yaml
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:8.11.1
  environment:
    - discovery.type=single-node
    - xpack.security.enabled=false
    - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    - ELASTIC_PASSWORD=${ELASTIC_PASSWORD}
  volumes:
    - es_data:/usr/share/elasticsearch/data
  ports:
    - "9200:9200"
  networks:
    - medivault-network
  deploy:
    resources:
      limits:
        memory: 1G
```

**Environment Variables**:
- `discovery.type=single-node` : cluster-mode
- `xpack.security.enabled`: Authentification
- `ES_JAVA_OPTS` : JVM heap size here it's set at 512 which is good to balance between performance and resource usage
- `Memory limit 1GB`: Prevent ES from consuming all server RAM
**Health Check**:
```bash
curl http://localhost:9200/_cluster/health?pretty
```

---

### Kibana

**Purpose**: Visualization, dashboards, and alerting

**Configuration**:
```yaml
  kibana:
    image: docker.elastic.co/kibana/kibana:8.11.1
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
      - ELASTICSEARCH_USERNAME=${ELASTICSEARCH_USERNAME}
      - ELASTICSEARCH_PASSWORD=${ELASTICSEARCH_PASSWORD}
      - XPACK_SECURITY_ENABLED=true
      - XPACK_ENCRYPTEDSAVEDOBJECTS_ENCRYPTIONKEY=${KIBANA_ENCRYPTION_KEY}
    ports:
      - "5601:5601"
    depends_on:
      elasticsearch:
        condition: service_healthy
    networks:
      - medivault-network
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:5601/api/status || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    restart: unless-stopped
```
**Environment Variables**:
- `XPACK_SECURITY_ENABLED` : Enable authentication to access Kibana UI.
- `XPACK_ENCRYPTEDSAVEDOBJECTS_ENCRYPTIONKEY`: Encrypt saved dashboards/alerts, where dashboards/alerts contains sensitive data.

**First Time Setup**:
1. Access: http://localhost:5601
2. Login: `yourname` / `changeme`
3. Create Data View:
   - Management → Data Views → Create
   - Name: `Audit Logs`
   - Index pattern: `audit-logs-*`
   - Time field: `@timestamp`

---

## Audit Logging

### Log Format

```
timestamp|severity|ip|actor|action|target|extra_data|previous_hash|hash
```

### Fields

| Field | Description | Example |
|-------|-------------|---------|
| **timestamp** | Unix milliseconds | `1768513669027` |
| **severity** | Log level | `INFO`, `ERROR`, `CRITICAL` |
| **ip** | Client IP address | `172.23.0.1` |
| **actor** | User identifier | `Patient:ad92551f` |
| **action** | Action performed | `PATIENT_FINISH_REGISTRATION` |
| **target** | Target resource | `patient_123.pdf` |
| **extra_data** | Additional info | `{"size": 1024}` |
| **previous_hash** | Previous log hash | `VbGa2Onaxt4e...` |
| **hash** | Current log hash | `SnychPDiKFAb...` |


---

## Anomaly Detection

### Configured Anomalies

| # | Name | Type | Priority | Detection Criteria |
|---|------|------|----------|-------------------|
| 1 | **Brute Force** | Threshold | Critical | >5 failed logins in 5min |
| 2 | **Hash Chain Integrity** | Script | Critical | Blockchain validation |
| 3 | **Unauthorized Admin** | Query | Critical | Admin actions by non-admins |
| 4 | **Mass Data Access** | Threshold | High | >20 downloads in 10min |
| 5 | **After-Hours Access** | Query |  High | Access 22h-6h |
| 6 | **Unusual IP per User** | ML | Medium | Rare IP addresses |
| 7 | **Patient Record Deletion** | Query | Medium | DELETE actions on patients |
| 8 | **Rare Actions** | ML | Medium | Unusual actions globally |

---

### Setup Guide

#### Anomaly #1: Brute Force Detection

```
1. Kibana → Menu ☰ → Stack Management → Rules and Connectors
2. Click "Create rule"
3. Configuration:
   - Name: Brute Force Detection
   - Tags: security, authentication, critical
   - Rule type: Elasticsearch query
   
4. Define the query:
   - Index: audit-logs-*
   - Time field: @timestamp
   - Query (KQL): audit.severity_level:ERROR AND audit.action:*LOGIN*
   
5. Set threshold:
   - When: count()
   - IS ABOVE: 5
   - FOR THE LAST: 5 minutes
   - GROUP BY: audit.actor.keyword (top 10)
   
6. Configure action:
   - Action: Server log
   - Message:
     BRUTE FORCE DETECTED
     User: {{context.group}}
     Failed attempts: {{context.hits}}
     Time: {{context.date}}
   
7. Schedule:
   - Check every: 1 minute
   - Notify: Every time alert is active
   
8. Save
```

**Test the rule**:
```bash
# Generate failed login attempts
for i in {1..6}; do
  curl -k https://localhost:8443/login -X POST \
    -H "Content-Type: application/json" \
    -d '{"username":"test","password":"wrong"}'
  sleep 1
done

# Check alert in Kibana → Stack Management → Rules → Brute Force Detection
```

---

#### Anomaly #3: Unauthorized Admin Action

```
1. Create rule
2. Configuration:
   - Name: Unauthorized Admin Action
   - Tags: security, privilege-escalation, critical
   - Rule type: Elasticsearch query
   
3. Query (DSL):
   {
     "query": {
       "bool": {
         "must": [
           { "wildcard": { "audit.action": "*ADMIN*" } }
         ],
         "must_not": [
           { "prefix": { "audit.actor": "Admin:" } },
           { "prefix": { "audit.actor": "System:" } }
         ]
       }
     }
   }
   
4. Threshold: IS ABOVE 0
5. FOR THE LAST: 1 minute
6. GROUP BY: audit.actor.keyword
7. Action: Server log with message
8. Save
```

---

#### Anomaly #4: Mass Data Access

```
1. Create rule
2. Configuration:
   - Name: Mass Data Access - Possible Exfiltration
   - Tags: security, data-exfiltration, high
   - Rule type: Elasticsearch query
   
3. Query (KQL):
   audit.action:(*VIEW* OR *DOWNLOAD* OR *ACCESS*)
   
4. Threshold:
   - IS ABOVE: 20
   - FOR THE LAST: 10 minutes
   - GROUP BY: audit.actor.keyword
   
5. Action: Server log
6. Save
```

---

#### Anomaly #6: Unusual IP per User (Machine Learning)

```
1. Kibana → Menu ☰ → Machine Learning → Anomaly Detection
2. Click "Create job" → "Use wizard"

3. Select data:
   - Data view: audit-logs-*
   - Time field: @timestamp
   - Click "Use full data"

4. Pick fields:
   - Job type: Rare
   - Function: rare
   - Field: audit.remote_ip.keyword
   - Split data: audit.actor.keyword
   - Bucket span: 30m

5. Job details:
   - Job ID: unusual-ip-per-user
   - Job description: Detects rare IP addresses per user
   - Job groups: security, ml

6. Validation → Create job

7. Start job:
   - Start time: now-7d
   - End time: now (continuous)
   - Click "Start"

8. View results:
   - Anomaly Explorer
   - Filter by severity > 50
```

---

#### Anomaly #8: Rare Actions (Machine Learning)

```
1. Machine Learning → Create job → Use wizard
2. Select data: audit-logs-*
3. Pick fields:
   - Job type: Rare
   - Function: rare
   - Field: audit.action.keyword
   - Bucket span: 1h
4. Job ID: rare-actions
5. Create and start
```

---

### Security Dashboard

Create a comprehensive dashboard:

```
1. Kibana → Menu ☰ → Analytics → Dashboard
2. Click "Create dashboard"
3. Name: "MediVault Security Dashboard"

Add visualizations:

Panel 1: Failed Logins Timeline
  - Type: Line chart
  - Data: audit-logs-*
  - Y-axis: Count
  - X-axis: @timestamp (5m intervals)
  - Filter: audit.severity_level:ERROR AND audit.action:*LOGIN*
  - Color: Red

Panel 2: Top Active Users
  - Type: Pie chart
  - Metric: Count
  - Breakdown: audit.actor.keyword (top 10)

Panel 3: Action Distribution
  - Type: Bar chart (horizontal)
  - X-axis: Count
  - Y-axis: audit.action.keyword (top 20)

Panel 4: Recent Critical Events
  - Type: Table
  - Columns:
    * @timestamp
    * audit.actor
    * audit.action
    * audit.target
    * audit.severity_level
  - Filter: audit.severity_level:(ERROR OR CRITICAL)
  - Sort: @timestamp desc
  - Rows: 20

Panel 5: Anomaly Scores
  - Type: Line chart
  - Data: .ml-anomalies-*
  - Y-axis: anomaly_score
  - X-axis: timestamp
  - Split: job_id

Panel 6: Geographic Distribution (if GeoIP enabled)
  - Type: Map
  - Field: geo.location
  - Metric: Count

4. Save dashboard
5. Set as home dashboard (optional)
```
---









