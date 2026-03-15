# bitlab-main-springboot
# 🚀 BitLab Backend – Multi-Language Execution Engine

BitLab Backend is a Spring Boot–based execution service that allows users to:

- 🔐 Register & Login
- 🧠 Submit Verilog, VHDL, or QNX C code
- ⚙️ Execute simulations or cross-compiled programs
- 📦 Retrieve logs and waveform files (VCD)
- 🖥 Run QNX programs on a remote VM via SSH

This backend integrates system-level scripting with modern REST APIs.

---

# 🏗 Architecture Overview

Frontend (React/Vite)
        ↓
Spring Boot REST API
        ↓
ExecutionService
        ↓
Shell Scripts
        ↓
• Verilog (Icarus Verilog)
• VHDL (GHDL)
• QNX (Windows QNX Toolchain + SSH VM)
```

---

# 📁 Project Structure

```
src/main/java/com/example/backend
│
├── config/
│   └── CorsConfig.java
│
├── controller/
│   ├── AuthController.java
│   └── ExecutionController.java
│
├── dto/
│   ├── AuthRequest.java
│   ├── ExecutionRequest.java
│   └── ExecutionResponse.java
│
├── entity/
│   └── User.java
│
├── repository/
│   └── UserRepository.java
│
├── service/
│   ├── AuthService.java
│   └── ExecutionService.java
│
├── util/
│   └── ShellExecutor.java
│
└── BackendApplication.java
```

Scripts:

```
scripts/
├── run_verilog.sh
├── run_vhdl.sh
└── run_qnx.sh
```

---

# 🔐 Authentication Module

## Register

```
POST /api/auth/register
```

Request:
```json
{
  "email": "user@example.com",
  "password": "1234"
}
```

## Login

```
POST /api/auth/login
```

Returns simple success/failure message.

I didnt use JWT tokenization, just a simulation for testing purpose

---

# ⚙️ Execution API

## Endpoint

```
POST /api/execute
```

Request Body:

```json
{
  "language": "verilog | vhdl | qnx",
  "designCode": "...",
  "testbenchCode": "..."
}
```

Response:

```json
{
  "status": "success",
  "logs": "execution logs here",
  "vcdBase64": "base64 encoded waveform"
}
```

---

# 🧠 Supported Languages

---

## 1️⃣ Verilog (Icarus Verilog)

Script: `run_verilog.sh`

Flow:

1. Copy files to `$HOME/verilog`
2. Compile using `iverilog`
3. Run using `vvp`
4. Generate `demo.vcd`
5. Return logs
6. Encode VCD as Base64

Requirements:
- iverilog
- vvp

---

## 2️⃣ VHDL (GHDL)

Script: `run_vhdl.sh`

Flow:

1. Copy files to `$HOME/vhdl`
2. Analyze with `ghdl -a`
3. Elaborate with `ghdl -e`
4. Run simulation
5. Generate `demo.vcd`
6. Return logs + VCD

Requirements:
- ghdl

---

## 3️⃣ QNX Execution (Advanced)

Script: `run_qnx.sh`

Flow:

1. Replace Windows Momentics `Demo.c`
2. Call QNX toolchain via `cmd.exe`
3. Build project using `make`
4. SCP binary to QNX VM
5. SSH execute program
6. Capture output safely with timeout

Environment Requirements:

- QNX SDP installed (Windows)
- VMware QNX target
- SSH enabled
- WSL/Linux environment
- SCP + SSH access to VM

---

# 🛠 ShellExecutor Utility

`ShellExecutor.execute(script, args...)`

- Uses `ProcessBuilder`
- Redirects stderr to stdout
- Captures full output
- Waits for process completion
- Returns logs

---

# 🗄 Database Configuration

PostgreSQL via Spring Data JPA

`application.properties`:

```properties
spring.datasource.url=...
spring.datasource.username=...
spring.datasource.password=...
spring.jpa.hibernate.ddl-auto=update
```

Entity:

```
User
- id
- email (unique)
- password
- createdAt
```

---

# 🌐 CORS Configuration

Allows:

```
http://localhost:5173
```


# 🧪 Execution Safety Features

✔ Timeout limits  
✔ Log truncation  
✔ Workspace isolation using UUID  
✔ Controlled file writes  
✔ Limited simulation time  

---

# 🚀 How to Run

## 1️⃣ Backend

```bash
mvn clean install
mvn spring-boot:run
```

Server runs on:

```
http://localhost:8080
```

---
