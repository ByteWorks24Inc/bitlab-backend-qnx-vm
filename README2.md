# BitLab — Full-Stack Platform Documentation

> **A browser-based execution platform for Verilog, VHDL, and QNX x86 hardware/RTOS development.**
> Built by [utej.me](https://utej.me) | Live at `https://bitlab.utej.me`

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [System Architecture](#system-architecture)
3. [Repository Structure](#repository-structure)
4. [Backend — Spring Boot](#backend--spring-boot)
   - [Tech Stack](#backend-tech-stack)
   - [Package Structure](#package-structure)
   - [API Endpoints](#api-endpoints)
   - [Authentication & JWT](#authentication--jwt)
   - [Execution Engine](#execution-engine)
   - [QNX Worker Health System](#qnx-worker-health-system)
   - [AWS SQS Integration](#aws-sqs-integration)
   - [In-Memory Result Store](#in-memory-result-store)
   - [Configuration Files](#configuration-files)
5. [Frontend — React + Vite](#frontend--react--vite)
   - [Tech Stack](#frontend-tech-stack)
   - [Pages](#pages)
   - [Components](#components)
   - [Context & State](#context--state)
   - [API Service Layer](#api-service-layer)
6. [QNX Worker — Python](#qnx-worker--python)
7. [Execution Scripts](#execution-scripts)
8. [Docker (Reference Only)](#docker-reference-only)
9. [Running the Project](#running-the-project)
10. [Environment Variables & Config](#environment-variables--config)
11. [Data Flow Diagrams](#data-flow-diagrams)

---

## Project Overview

BitLab is a cloud-based IDE and simulation platform built specifically for hardware description languages (Verilog, VHDL) and real-time operating system (QNX x86) development. Users write code in their browser, submit it, and receive real-time simulation output including waveform data (VCD files) that can be visualized in the built-in Waveform Studio.

**Three execution modes:**

| Mode | Language | Execution Path |
|------|----------|---------------|
| Verilog | `.v` files | `iverilog` + `vvp` via Java `ProcessBuilder` |
| VHDL | `.vhd` files | `ghdl` (analyze → elaborate → run) via Java `ProcessBuilder` |
| QNX x86 | C code | AWS SQS → Python worker on QNX machine → result callback |

---

## System Architecture

```
Browser (React)
    │
    │  HTTPS REST API
    ▼
Spring Boot Backend (bitlab.utej.me/api)
    │
    ├── Verilog/VHDL ──► ProcessBuilder (iverilog / ghdl)
    │                         │
    │                         └──► VCD Base64 encoded → Frontend
    │
    └── QNX ──► AWS SQS Queue (us-east-2)
                    │
                    ▼
              Python Worker (worker.py) — runs on QNX machine
                    │
                    └──► POST /api/result → Backend stores result
                              │
                              ▼
                        Frontend polls /api/result/{jobId}
```

---

## Repository Structure

```
bitlab2/
├── bitlab-backend-qnx-vm/          ← Spring Boot backend (THIS FOLDER)
│   ├── src/main/java/com/example/backend/
│   │   ├── config/                 ← Security, JWT, CORS, Kafka, SQS config
│   │   ├── controller/             ← REST controllers
│   │   ├── dto/                    ← Data Transfer Objects
│   │   ├── entity/                 ← JPA entities
│   │   ├── repository/             ← Spring Data JPA repos
│   │   ├── service/                ← Business logic
│   │   └── util/                   ← Shell executor utility
│   ├── scripts/                    ← Bash execution scripts
│   ├── Dockerfile                  ← Backend Docker image (reference)
│   ├── Dockerfile.verilog          ← Verilog worker Docker image (reference)
│   ├── Dockerfile.vhdl             ← VHDL worker Docker image (reference)
│   ├── docker-compose.yml          ← Full stack compose (reference)
│   └── pom.xml                     ← Maven dependencies
│
├── frontend-verilog-qnx-fledged/   ← React + Vite frontend
│   ├── src/
│   │   ├── pages/                  ← Landing, Login, Register, Dashboard, CodeLab, WaveformStudio
│   │   ├── components/             ← Sidebar, CanvasWaveform, ProtectedRoute
│   │   ├── context/                ← AuthContext, ThemeContext
│   │   ├── services/               ← Axios API client
│   │   └── router/                 ← Route definitions
│   ├── package.json
│   └── vite.config.js
│
└── worker-aws-sqs-py/              ← Python QNX worker
    └── worker.py
```

---

## Backend — Spring Boot

### Backend Tech Stack

| Technology | Version | Purpose |
|-----------|---------|---------|
| Spring Boot | 4.0.3 | Core framework |
| Java | 17 | Runtime |
| Spring Security | — | JWT auth filter chain |
| Spring Data JPA | — | ORM / database layer |
| PostgreSQL | 14 | Primary database |
| JJWT (io.jsonwebtoken) | 0.11.5 | JWT token generation & validation |
| AWS SDK SQS | 2.25.43 | QNX job queuing |
| Spring Kafka | — | Dependency present (listener replaced by ProcessBuilder) |
| Micrometer / Actuator | — | Health checks & metrics |
| Lombok | 1.18.32 | Boilerplate reduction |
| Jackson | — | JSON serialization |
| Maven | — | Build tool |

---

### Package Structure

```
com.example.backend
├── BackendApplication.java          ← Spring Boot entry point
│
├── config/
│   ├── CorsConfig.java              ← Global CORS (all origins allowed)
│   ├── JwtFilter.java               ← HTTP request JWT interceptor
│   ├── JwtUtil.java                 ← Token generation & validation (24h expiry)
│   ├── SecurityConfig.java          ← Security filter chain, public routes
│   ├── KafkaConfig.java             ← Kafka config (retained, not actively used)
│   └── SqsConfig.java               ← AWS SQS client bean
│
├── controller/
│   ├── AuthController.java          ← /api/auth/** (register, login, list users)
│   ├── ExecutionController.java     ← /api/execute (submit code, fetch VCD)
│   ├── ExecutionResultController.java ← /api/result (poll job results)
│   ├── QuestionController.java      ← /api/questions (question & testcase CRUD)
│   └── WorkerHealthController.java  ← /api/worker/qnx/* (heartbeat, status)
│
├── dto/
│   ├── AuthRequest.java             ← { email, password }
│   ├── ExecutionRequest.java        ← { language, designCode, testbenchCode }
│   ├── ExecutionResponse.java       ← Sync response wrapper
│   ├── ExecutionResult.java         ← { status, logs, vcdBase64, errorLine }
│   ├── ExecutionResultRequest.java  ← Worker callback payload
│   ├── KafkaExecutionMessage.java   ← Kafka message schema (legacy)
│   └── QueueExecutionResponse.java  ← { status: "queued", jobId }
│
├── entity/
│   ├── User.java                    ← users table (id, email, password, createdAt, stats)
│   ├── Question.java                ← questions table (id, name, desc, tags, testCases)
│   ├── TestCase.java                ← test_cases table
│   └── Submission.java              ← submissions table
│
├── repository/
│   ├── UserRepository.java          ← findByEmail()
│   ├── QuestionRepository.java
│   └── TestCaseRepository.java
│
└── service/
    ├── AuthService.java             ← Register, login, JWT issuance
    ├── ExecutionService.java        ← Routes execution by language (QNX vs HDL)
    ├── VerilogVhdlExecutor.java     ← ProcessBuilder executor for iverilog/ghdl
    ├── ExecutionResultStore.java    ← ConcurrentHashMap job result cache
    ├── KafkaExecutionListener.java  ← Error line extractor (regex on compiler output)
    ├── SqsJobService.java           ← Sends jobs to AWS SQS queue
    ├── QuestionService.java
    └── TestCaseService.java
```

---

### API Endpoints

#### Auth — `/api/auth`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/register` | Public | Register new user. Returns success string. |
| `POST` | `/api/auth/login` | Public | Login. Returns JWT token string. |
| `GET` | `/api/auth/users` | Public* | List all users (debug endpoint). |

**Request body for register/login:**
```json
{ "email": "user@example.com", "password": "yourpassword" }
```

---

#### Execution — `/api/execute`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/execute` | Public | Submit code for execution. Returns `{ status, jobId }`. |
| `GET` | `/api/execute/graph?language=verilog` | Public | Fetch raw `.vcd` file from disk (legacy path). |

**Request body:**
```json
{
  "language": "verilog",
  "designCode": "module adder(...",
  "testbenchCode": "`timescale 1ns/1ps..."
}
```

**Response:**
```json
{ "status": "queued", "jobId": "uuid-string" }
```

Supported `language` values: `verilog`, `vhdl`, `systemverilog`, `qnx`

---

#### Result Polling — `/api/result`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/result` | Public | Worker callback — stores job result. |
| `GET` | `/api/result/{jobId}` | Public | Poll for job result. |

**Poll response (running):**
```json
{ "status": "RUNNING" }
```

**Poll response (done):**
```json
{
  "status": "DONE",
  "logs": "[STAGE:COMPILE] ...\n[STAGE:OK] ...",
  "vcdBase64": "base64encodedVCDstring...",
  "errorLine": "Error at line 5"
}
```

---

#### Worker Health — `/api/worker`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/worker/qnx/connect` | Public | Called by `worker.py` on startup → marks ONLINE. |
| `POST` | `/api/worker/qnx/heartbeat` | Public | Called every 15s → refreshes liveness timestamp. |
| `POST` | `/api/worker/qnx/disconnect` | Public | Called by `worker.py` on clean shutdown → marks OFFLINE. |
| `GET` | `/api/worker/qnx/status` | Public | Frontend polls this to show worker status badge. |

**Status response:**
```json
{
  "online": true,
  "lastSeenSeconds": 8,
  "message": "QNX worker is online."
}
```

Worker is considered offline if heartbeat hasn't arrived within **30 seconds**.

---

#### Questions — `/api/questions`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/questions/question` | JWT | List all questions. |
| `GET` | `/api/questions/question/{id}` | JWT | Get question by ID. |
| `GET` | `/api/questions/testcase` | JWT | List all test cases. |
| `GET` | `/api/questions/testcase/{id}` | JWT | Get test case by ID. |

---

### Authentication & JWT

- **Algorithm:** HMAC-SHA (via JJWT 0.11.5)
- **Token expiry:** 24 hours (86,400,000 ms)
- **Secret:** Base64-encoded key configurable via `jwt.secret` property
- **Filter:** `JwtFilter` intercepts every request, extracts `Authorization: Bearer <token>`, validates and sets Spring Security context

**Public routes (no JWT required):**
- `OPTIONS /**`
- `/api/auth/**`
- `/api/execute`, `/api/execute/**`
- `/api/result`, `/api/result/**`
- `/api/worker/**`
- `/actuator/**`

**Note:** Passwords are stored in plaintext — no BCrypt hashing is currently applied.

---

### Execution Engine

`ExecutionService` routes by language:

**Verilog / VHDL / SystemVerilog:**
1. A UUID `jobId` is generated
2. Result store is seeded with `{ status: "RUNNING" }`
3. `CompletableFuture.runAsync()` launches `VerilogVhdlExecutor`
4. HTTP response returns immediately with `{ status: "queued", jobId }`
5. Frontend polls `/api/result/{jobId}` every 2 seconds (max 60 attempts = 2 min)

**VerilogVhdlExecutor stages:**

*Verilog/SystemVerilog:*
```
[STAGE:COMPILE]  iverilog -o output.vvp design.v tb.v
[STAGE:OK]       Compilation successful
[STAGE:SIM]      vvp output.vvp  (10s timeout)
[STAGE:DONE]     Waveform saved
```

*VHDL:*
```
[STAGE:ANALYZE]  ghdl -a --std=08 design.vhd
[STAGE:OK]       Design analyzed
[STAGE:ANALYZE]  ghdl -a --std=08 tb.vhd
[STAGE:OK]       Testbench analyzed
[STAGE:ELAB]     ghdl -e --std=08 tb
[STAGE:OK]       Elaboration complete
[STAGE:SIM]      ghdl -r --std=08 tb --vcd=demo.vcd --stop-time=1000ns
[STAGE:DONE]     Simulation complete
```

After simulation, the `demo.vcd` file is Base64-encoded and attached to the result. The frontend decodes it and passes it to the Waveform Studio via `window.postMessage`.

**Error line detection:** `KafkaExecutionListener` (now a plain `@Service`) uses regex to extract line numbers from `iverilog`/`ghdl` compiler output and sends `errorLine` back in the result.

---

### QNX Worker Health System

`WorkerHealthController` uses two `AtomicLong`/`AtomicBoolean` fields:
- `lastHeartbeat` — epoch ms of last heartbeat POST
- `explicitlyOnline` — set `true` on `/connect`, `false` on `/disconnect`

**Logic:**
- Explicit disconnect → immediately OFFLINE
- Last heartbeat > 30s ago → OFFLINE (crash detection)
- Both conditions pass → ONLINE with `lastSeenSeconds` reporting

---

### AWS SQS Integration

Used exclusively for **QNX** code execution:

- **Queue URL:** `https://sqs.us-east-2.amazonaws.com/180273188019/bitlab-qnx-queue`
- **Region:** `us-east-2`
- **Flow:** Backend sends `{ jobId, language, code }` JSON → Queue → `worker.py` picks up → executes on QNX machine → POSTs result back to `/api/result`

`SqsConfig.java` creates the `SqsClient` bean using AWS credentials from environment/properties.

---

### In-Memory Result Store

`ExecutionResultStore` is a Spring `@Service` backed by a `ConcurrentHashMap<String, ExecutionResult>`. It is **not persisted** — results are lost on backend restart. This is intentional for the current stateless design.

```java
// Stores:  { status, logs, vcdBase64, errorLine }
// Key:     UUID jobId
// Thread:  ConcurrentHashMap (safe for async writes)
```

---

### Configuration Files

Key `application.properties` / environment variables:

| Property | Default | Description |
|---------|---------|-------------|
| `spring.datasource.url` | — | PostgreSQL JDBC URL |
| `spring.datasource.username` | — | DB username |
| `spring.datasource.password` | — | DB password |
| `jwt.secret` | Base64 default | JWT signing secret |
| `execution.workdir.base` | `~/bitlab-jobs` | Temp job working directory |
| `KAFKA_LISTENER_ENABLED` | `false` | Disables Kafka consumer |
| `KAFKA_BOOTSTRAP_SERVERS` | — | Kafka broker address |

---

## Frontend — React + Vite

### Frontend Tech Stack

| Technology | Version | Purpose |
|-----------|---------|---------|
| React | 19.2.0 | UI framework |
| Vite | 7.x | Build tool & dev server |
| React Router DOM | 7.x | Client-side routing |
| Axios | 1.x | HTTP client |
| Framer Motion | 12.x | Animations & transitions |
| Monaco Editor (`@monaco-editor/react`) | 4.7.0 | VS Code-grade code editor |
| Lucide React | 0.575.0 | Icon library |
| TailwindCSS | 4.x | Utility CSS (with custom CSS tokens) |
| Wavedrom | 3.5.0 | Waveform rendering library |

---

### Pages

#### `Landing.jsx` — `/`
Public marketing page. Features hero section with animated rotating circles, three feature cards (Verilog, VHDL, QNX x86), "Why We Built BitLab" section, and footer with YouTube tutorial and GitHub links.

#### `Login.jsx` — `/login`
Authenticates users. Posts to `/api/auth/login`, stores JWT token in `localStorage` as `authToken`, redirects to `/dashboard`.

#### `Register.jsx` — `/register`
Registration form. Posts to `/api/auth/register`. Matches the BitLab design system with gradient backgrounds and animated form fields.

#### `Dashboard.jsx` — `/dashboard`
Protected page. Shows three lab cards: **Verilog**, **VHDL**, and **QNX**. Each card navigates to `/editor/:lang`. Animated with Framer Motion stagger effects and hover lift (`y: -8`).

#### `CodeLab.jsx` — `/editor/:lang`
The core IDE page. Features:
- **Dual Monaco Editor panels** — Design editor (left/top) + Testbench editor (right, HDL only)
- **Stage-tagged terminal** — Logs rendered per `[STAGE:XXX]` tag with color-coded badges
- **Error line highlighting** — Monaco `deltaDecorations` highlights error lines red
- **QNX Worker Banner** — Green/red status banner shown when `lang === 'qnx'`
- **Waveform Studio launcher** — Appears after successful simulation; opens `/waveform` in a popup window and syncs VCD via `window.postMessage`
- **VCD download** — Downloads raw `.vcd` waveform file
- **Keyboard shortcut** — `Ctrl+Enter` triggers execution
- **LocalStorage persistence** — Code is auto-saved per language key

**Polling logic:** After submitting, polls `/api/result/{jobId}` every 2 seconds. Stops on `status === "DONE"` or after 60 attempts (2 min timeout).

#### `WaveformStudio.jsx` — `/waveform`
Opened as a popup window. Receives VCD data from parent CodeLab window via `postMessage({ type: 'load_vcd', vcd, lang })`. Renders interactive waveforms using the `CanvasWaveform` component.

---

### Components

#### `Sidebar.jsx`
Collapsible navigation sidebar with:
- Logo + "BITLAB / Laboratory Core" branding
- Nav items: Dashboard, Verilog Lab, VHDL Lab, QNX OS Lab
- Active route indicator (animated underline via Framer Motion `layoutId`)
- Light/Dark theme toggle
- User email display (from `AuthContext`)
- Logout button
- Mobile responsive: collapses to icon-only on screens < 768px; overlays full-width when expanded on mobile

#### `CanvasWaveform.jsx`
Canvas-based waveform renderer. Parses raw VCD text and draws signal waveforms with time axis. Supports zooming and signal labeling.

#### `ProtectedRoute.jsx`
Wraps routes requiring authentication. Reads JWT from `localStorage`, redirects to `/login` if missing or expired.

---

### Context & State

#### `AuthContext.jsx`
Provides `user`, `login()`, `logout()` across the app:
- `login(token)` — stores token in `localStorage`, decodes email from JWT payload
- `logout()` — clears `localStorage`
- `user` — `{ email }` derived from token

#### `ThemeContext.jsx`
Provides `isDarkMode` and `toggleTheme()`. Persists preference to `localStorage`. Adds/removes a `dark` class on `<html>` to drive CSS variable switching.

---

### API Service Layer

**`services/api.js`** — Axios instance:
```js
baseURL: 'https://bitlab.utej.me/api'
```

**Auto-attaches JWT:** Request interceptor reads `authToken` from `localStorage` and adds `Authorization: Bearer <token>` header to every request.

**`services/auth.js`** — Auth-specific helpers wrapping `api.js` for login and register calls.

---

## QNX Worker — Python

**File:** `worker-aws-sqs-py/worker.py`

The Python worker runs **on the physical QNX machine** (not in the cloud). It bridges AWS SQS with the local QNX execution environment.

**Lifecycle:**

| Event | Action |
|-------|--------|
| Startup | Purges SQS queue, POSTs to `/api/worker/qnx/connect` |
| Running | Long-polls SQS every 10s (`WaitTimeSeconds=10`) |
| Heartbeat | Background thread POSTs to `/api/worker/qnx/heartbeat` every **15 seconds** |
| Job received | Writes C code to `/tmp/qnx_jobs/{jobId}.c`, runs `run_qnx.sh` |
| Job done | POSTs `{ jobId, logs }` to `/api/result`, deletes SQS message |
| Ctrl+C / SIGTERM | POSTs to `/api/worker/qnx/disconnect` then exits |

**Crash detection:** If worker crashes without sending `/disconnect`, the backend detects the missing heartbeat after 30s and marks worker OFFLINE.

---

## Execution Scripts

Located in `bitlab-backend-qnx-vm/scripts/`:

| Script | Purpose |
|--------|---------|
| `run_verilog.sh` | Compile with `iverilog`, simulate with `vvp`, capture VCD |
| `run_vhdl.sh` | Analyze, elaborate, and run with `ghdl`, capture VCD |
| `run_sverilog.sh` | SystemVerilog variant (same flow as Verilog) |
| `run_qnx.sh` | Push a C file to AWS SQS queue (manual CLI helper) |
| `generate_waveform.sh` | Convert VCD to PNG via Python |
| `vcd_to_png.py` | Python script for VCD → PNG conversion |

> **Note:** These bash scripts are reference/manual-use helpers. The backend uses `VerilogVhdlExecutor.java` with direct `ProcessBuilder` calls for Verilog/VHDL. The `run_qnx.sh` script is used manually to push jobs via AWS CLI; programmatic QNX dispatch uses `SqsJobService.java`.

---

## Docker (Reference Only)

Dockerfiles exist in the repository but **are not used in the current deployment**. The application runs directly on the host machine.

| File | Description |
|------|-------------|
| `Dockerfile` | Backend image — Eclipse Temurin JDK 17, builds `app.jar` |
| `Dockerfile.verilog` | Backend + `iverilog` installed — Verilog worker image |
| `Dockerfile.vhdl` | Backend + `ghdl` installed — VHDL worker image |
| `docker-compose.yml` | Orchestrates: Zookeeper, Kafka, PostgreSQL, backend, verilog-worker, vhdl-worker, frontend |

The `docker-compose.yml` represents the **intended containerized architecture** where Kafka-based worker nodes handle HDL execution. The current production setup uses direct `ProcessBuilder` execution instead.

---

## Running the Project

### Backend (Local Development)

**Prerequisites:** Java 17, Maven, PostgreSQL running

```bash
cd bitlab-backend-qnx-vm

# Set up PostgreSQL
# Create DB: myapp, user: myuser, password: mypassword

./mvnw spring-boot:run
# Starts on http://localhost:8080
```

### Frontend (Local Development)

**Prerequisites:** Node.js 18+

```bash
cd frontend-verilog-qnx-fledged
npm install
npm run dev
# Starts on http://localhost:5173
```

> **API target:** The frontend `api.js` points to `https://bitlab.utej.me/api` (production). For local dev, change `baseURL` to `http://localhost:8080/api`.

### QNX Worker

**Prerequisites:** Python 3, `boto3`, `requests`, AWS credentials configured, physical QNX machine

```bash
cd worker-aws-sqs-py
pip install boto3 requests
python3 worker.py
```

---

## Environment Variables & Config

### Backend
```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/myapp
SPRING_DATASOURCE_USERNAME=myuser
SPRING_DATASOURCE_PASSWORD=mypassword
JWT_SECRET=<base64-encoded-256bit-secret>
KAFKA_LISTENER_ENABLED=false
AWS_ACCESS_KEY_ID=<your-key>
AWS_SECRET_ACCESS_KEY=<your-secret>
```

### Frontend
No `.env` configuration needed. API base URL is hardcoded in `src/services/api.js`.

### Worker
AWS credentials are embedded in `worker.py` (should be moved to env vars for production):
```
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
AWS_REGION=us-east-2
SQS_QUEUE_URL=https://sqs.us-east-2.amazonaws.com/180273188019/bitlab-qnx-queue
BACKEND_BASE_URL=https://bitlab.utej.me
```

---

## Data Flow Diagrams

### Verilog / VHDL Execution Flow
```
User writes code in CodeLab (Monaco Editor)
    │
    │ POST /api/execute { language, designCode, testbenchCode }
    ▼
ExecutionService → generates jobId, saves RUNNING status
    │
    │ CompletableFuture.runAsync()
    ▼
VerilogVhdlExecutor
    ├── writes design.v / tb.v  OR  design.vhd / tb.vhd to /tmp/bitlab-jobs/{jobId}/
    ├── runs iverilog / ghdl via ProcessBuilder
    ├── captures stdout/stderr as stage-tagged logs
    └── reads demo.vcd → Base64 encodes it
    │
    ▼
ExecutionResultStore.save(jobId, { DONE, logs, vcdBase64, errorLine })
    │
    │ Frontend polls GET /api/result/{jobId} every 2s
    ▼
CodeLab receives result
    ├── Renders stage logs in terminal panel
    ├── Highlights error line in Monaco editor (if errorLine present)
    └── Decodes Base64 VCD → enables Waveform Studio button
```

### QNX Execution Flow
```
User writes C code in CodeLab (QNX mode)
    │
    │ POST /api/execute { language: "qnx", designCode }
    ▼
ExecutionService → generates jobId, sends to SqsJobService
    │
    │ AWS SQS Queue (bitlab-qnx-queue, us-east-2)
    ▼
worker.py (on QNX machine) — long-polling SQS
    ├── receives { jobId, code }
    ├── writes .c file to /tmp/qnx_jobs/
    └── runs run_qnx.sh (compile + execute on QNX)
    │
    │ POST /api/result { jobId, logs }
    ▼
ExecutionResultController → stores in ExecutionResultStore
    │
    │ Frontend polls GET /api/result/{jobId}
    ▼
CodeLab renders terminal output
```

---

*BitLab™ — Laboratory Core. Built by [utej.me](https://utej.me)*
