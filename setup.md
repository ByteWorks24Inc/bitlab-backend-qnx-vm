# BitLab Backend — Server Setup Guide

> **Spring Boot 4 · Java 17 · PostgreSQL · iverilog · ghdl · AWS SQS**
> Production host: `bitlab.utej.me` | API root: `/api`

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [System Dependencies](#2-system-dependencies)
3. [PostgreSQL Setup](#3-postgresql-setup)
4. [Clone & Configure the Project](#4-clone--configure-the-project)
5. [Environment Variables](#5-environment-variables)
6. [Build the Application](#6-build-the-application)
7. [Run the Backend](#7-run-the-backend)
8. [Verify the Server is Running](#8-verify-the-server-is-running)
9. [Simulation Tool Setup (iverilog & ghdl)](#9-simulation-tool-setup-iverilog--ghdl)
10. [Execution Working Directory](#10-execution-working-directory)
11. [AWS SQS (QNX Mode Only)](#11-aws-sqs-qnx-mode-only)
12. [Running as a systemd Service (Production)](#12-running-as-a-systemd-service-production)
13. [Nginx Reverse Proxy (Production)](#13-nginx-reverse-proxy-production)
14. [Troubleshooting](#14-troubleshooting)

---

## 1. Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| OS | Ubuntu 22.04 LTS (recommended) | Any Debian-based Linux works |
| Java (JDK) | 17 | Eclipse Temurin or OpenJDK |
| Maven | 3.8+ | Or use the bundled `./mvnw` wrapper |
| PostgreSQL | 14+ | Must be running before the app starts |
| Git | Any | To clone the repository |
| iverilog | Latest | For Verilog/SystemVerilog simulation |
| ghdl | 2.x | For VHDL simulation |
| AWS CLI / credentials | — | Only needed for QNX mode |

---

## 2. System Dependencies

### Install Java 17 (Eclipse Temurin)

```bash
# Add Adoptium repository
wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo apt-key add -
echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" \
  | sudo tee /etc/apt/sources.list.d/adoptium.list

sudo apt update
sudo apt install -y temurin-17-jdk

# Verify
java -version
# Expected: openjdk version "17.x.x"
```

### Install Maven (optional — project includes `./mvnw`)

```bash
sudo apt install -y maven
mvn -version
```

### Install PostgreSQL

```bash
sudo apt install -y postgresql postgresql-contrib
sudo systemctl enable postgresql
sudo systemctl start postgresql
```

### Install Git

```bash
sudo apt install -y git
```

---

## 3. PostgreSQL Setup

```bash
# Switch to the postgres system user
sudo -u postgres psql

# Inside the psql shell — run these commands:
CREATE DATABASE myapp;
CREATE USER myuser WITH PASSWORD 'mypassword';
GRANT ALL PRIVILEGES ON DATABASE myapp TO myuser;
\q
```

> **Important:** If you change these credentials, update `application.properties` or your environment variables accordingly (see [Section 5](#5-environment-variables)).

### Verify Connection

```bash
psql -h localhost -U myuser -d myapp -c "\dt"
# Should connect and show "Did not find any relations." (empty DB — that's correct)
```

---

## 4. Clone & Configure the Project

```bash
# Clone the monorepo
git clone https://github.com/your-org/bitlab2.git
cd bitlab2/bitlab-backend-qnx-vm
```

### Review `application.properties`

Located at `src/main/resources/application.properties`. Key settings:

```properties
server.port=8080
server.address=0.0.0.0

spring.datasource.url=jdbc:postgresql://localhost:5432/myapp
spring.datasource.username=myuser
spring.datasource.password=mypassword

bitlab.workspace-root=./workspace
bitlab.execution-timeout-seconds=10

execution.workdir.base=/tmp/bitlab-jobs

qnx.worker.health-url=http://localhost:5050/health

spring.kafka.listener.auto-startup=false
spring.kafka.admin.fail-fast=false
```

> **Note on Kafka:** Kafka is listed as a dependency but the listener is **disabled by default** (`auto-startup=false`). You do **not** need a running Kafka broker for the backend to start.

---

## 5. Environment Variables

Set these on the server (either in your shell profile, a `.env` file sourced before startup, or the systemd unit file):

```bash
# Database
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/myapp
export SPRING_DATASOURCE_USERNAME=myuser
export SPRING_DATASOURCE_PASSWORD=mypassword

# JWT signing secret (generate a strong random value)
export JWT_SECRET=$(openssl rand -base64 32)

# Disable Kafka listener (no Kafka broker needed)
export KAFKA_LISTENER_ENABLED=false

# Execution working directory for HDL jobs
export EXECUTION_WORKDIR=/tmp/bitlab-jobs

# AWS (only required if using QNX mode)
export AWS_ACCESS_KEY_ID=your-access-key-id
export AWS_SECRET_ACCESS_KEY=your-secret-access-key
export AWS_REGION=us-east-2
```

> **Tip:** Generate a JWT secret you can reuse across restarts:
> ```bash
> openssl rand -base64 32
> # Copy the output and set it as JWT_SECRET
> ```

---

## 6. Build the Application

```bash
cd bitlab2/bitlab-backend-qnx-vm

# Option A — use Maven wrapper (recommended, no Maven install needed)
chmod +x mvnw
./mvnw clean package -DskipTests

# Option B — use system Maven
mvn clean package -DskipTests

# The built JAR will be at:
ls -lh target/app.jar
```

Expected output on success:
```
[INFO] BUILD SUCCESS
[INFO] Total time: ~30-60 seconds
```

---

## 7. Run the Backend

### Quick Start (foreground)

```bash
java -jar target/app.jar
```

### With explicit environment variables inline

```bash
SPRING_DATASOURCE_PASSWORD=mypassword \
JWT_SECRET=your-base64-secret \
KAFKA_LISTENER_ENABLED=false \
java -jar target/app.jar
```

### On a custom port

```bash
java -jar target/app.jar --server.port=9090
```

The backend starts on **`http://0.0.0.0:8080`** by default.

---

## 8. Verify the Server is Running

```bash
# Health check via Actuator
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP","components":{...}}
```

```bash
# Test auth endpoint
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test123"}'

# Expected: "User registered successfully" or similar
```

```bash
# Check worker health endpoint
curl http://localhost:8080/api/worker/qnx/status

# Expected:
# {"online":false,"lastSeenSeconds":-1,"message":"QNX worker is offline."}
```

---

## 9. Simulation Tool Setup (iverilog & ghdl)

These tools must be installed on the **same machine** running the Spring Boot backend, since `VerilogVhdlExecutor.java` calls them directly via `ProcessBuilder`.

### Install iverilog (Verilog / SystemVerilog)

```bash
sudo apt install -y iverilog

# Verify
iverilog -v
# Expected: Icarus Verilog version x.x.x
```

### Install ghdl (VHDL)

```bash
sudo apt install -y ghdl

# Verify
ghdl --version
# Expected: GHDL 2.x.x ...
```

### Confirm tools are on PATH

```bash
which iverilog   # Should output /usr/bin/iverilog
which ghdl       # Should output /usr/bin/ghdl
```

> **Important:** The backend process must have access to these binaries. If running via systemd, ensure the unit's `Environment=PATH` includes `/usr/bin`.

### Test a Verilog simulation manually

```bash
mkdir -p /tmp/test-sim && cd /tmp/test-sim

cat > design.v << 'EOF'
module adder(input a, input b, output sum);
  assign sum = a ^ b;
endmodule
EOF

cat > tb.v << 'EOF'
`timescale 1ns/1ps
module tb;
  reg a, b;
  wire sum;
  adder uut(.a(a),.b(b),.sum(sum));
  initial begin
    $dumpfile("demo.vcd");
    $dumpvars(0, tb);
    a=0; b=0; #10;
    a=1; b=0; #10;
    a=1; b=1; #10;
    $finish;
  end
endmodule
EOF

iverilog -o output.vvp design.v tb.v && vvp output.vvp
ls demo.vcd  # Should exist if successful
```

---

## 10. Execution Working Directory

The backend writes temporary HDL job files to a working directory. Create and set permissions:

```bash
sudo mkdir -p /tmp/bitlab-jobs
sudo chmod 777 /tmp/bitlab-jobs
```

> The path is controlled by `execution.workdir.base` in `application.properties` or the `EXECUTION_WORKDIR` env var.

---

## 11. AWS SQS (QNX Mode Only)

Skip this section if you are **not** using the QNX execution mode.

### Configure AWS credentials

```bash
# Install AWS CLI
sudo apt install -y awscli

# Configure credentials
aws configure
# Enter: Access Key ID, Secret Access Key, Region (us-east-2), output format (json)
```

### Verify SQS access

```bash
aws sqs list-queues --region us-east-2
# Should list: https://sqs.us-east-2.amazonaws.com/.../bitlab-qnx-queue
```

The backend's `SqsConfig.java` auto-picks credentials from the standard AWS credential chain (`~/.aws/credentials` or environment variables).

---

## 12. Running as a systemd Service (Production)

Create a service file to auto-start and manage the backend process:

```bash
sudo nano /etc/systemd/system/bitlab-backend.service
```

Paste the following (adjust paths as needed):

```ini
[Unit]
Description=BitLab Spring Boot Backend
After=network.target postgresql.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/bitlab2/bitlab-backend-qnx-vm
ExecStart=/usr/bin/java -jar /home/ubuntu/bitlab2/bitlab-backend-qnx-vm/target/app.jar
Restart=on-failure
RestartSec=10

# Environment variables
Environment="SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/myapp"
Environment="SPRING_DATASOURCE_USERNAME=myuser"
Environment="SPRING_DATASOURCE_PASSWORD=mypassword"
Environment="JWT_SECRET=your-base64-secret-here"
Environment="KAFKA_LISTENER_ENABLED=false"
Environment="EXECUTION_WORKDIR=/tmp/bitlab-jobs"
Environment="AWS_ACCESS_KEY_ID=your-key"
Environment="AWS_SECRET_ACCESS_KEY=your-secret"
Environment="AWS_REGION=us-east-2"
Environment="PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

StandardOutput=journal
StandardError=journal
SyslogIdentifier=bitlab-backend

[Install]
WantedBy=multi-user.target
```

```bash
# Enable and start the service
sudo systemctl daemon-reload
sudo systemctl enable bitlab-backend
sudo systemctl start bitlab-backend

# Check status
sudo systemctl status bitlab-backend

# View logs
sudo journalctl -u bitlab-backend -f
```

---

## 13. Nginx Reverse Proxy (Production)

To expose the backend on port 443 (HTTPS) behind a domain, install and configure Nginx:

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
```

Create a site config:

```bash
sudo nano /etc/nginx/sites-available/bitlab-backend
```

```nginx
server {
    listen 80;
    server_name bitlab.utej.me;

    location /api {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Required for long-polling result endpoints
        proxy_read_timeout 120s;
        proxy_send_timeout 120s;
    }

    location /actuator {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/bitlab-backend /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx

# Obtain SSL certificate
sudo certbot --nginx -d bitlab.utej.me
```

---

## 14. Troubleshooting

### Backend fails to start — "Unable to acquire JDBC Connection"

PostgreSQL is not running or credentials are wrong.
```bash
sudo systemctl status postgresql
psql -h localhost -U myuser -d myapp   # test login manually
```

### Port 8080 already in use

```bash
sudo lsof -i :8080
sudo kill -9 <PID>
```

### `iverilog: command not found` in backend logs

The tool isn't on the PATH used by the JVM process.
```bash
which iverilog    # Confirm installation
sudo apt install -y iverilog

# If using systemd, add to the unit file:
Environment="PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
```

### Kafka connection errors on startup

Kafka is not required. Ensure these are set:
```properties
spring.kafka.listener.auto-startup=false
spring.kafka.admin.fail-fast=false
```

Or set the env var:
```bash
export KAFKA_LISTENER_ENABLED=false
```

### JWT errors — "SignatureException" or "MalformedJwtException"

The `JWT_SECRET` changed between restarts. All existing tokens become invalid. Re-login with the frontend to get a fresh token.

### AWS SQS — "Unable to load credentials"

```bash
aws sts get-caller-identity   # Verify AWS CLI credentials
# Or set env vars explicitly:
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
```

### Check Actuator metrics

```bash
curl http://localhost:8080/actuator/prometheus  # Prometheus metrics
curl http://localhost:8080/actuator/info        # App info
```

---

## Quick Reference — Startup Checklist

```
[ ] Java 17 installed        (java -version)
[ ] PostgreSQL running        (systemctl status postgresql)
[ ] DB + user created         (psql -U myuser -d myapp)
[ ] iverilog installed        (which iverilog)
[ ] ghdl installed            (which ghdl)
[ ] /tmp/bitlab-jobs exists   (ls /tmp/bitlab-jobs)
[ ] app.jar built             (ls target/app.jar)
[ ] Environment variables set (JWT_SECRET, DB creds, AWS creds)
[ ] Backend started           (curl localhost:8080/actuator/health)
```

---

*BitLab™ — Laboratory Core. Built by [utej.me](https://utej.me)*
