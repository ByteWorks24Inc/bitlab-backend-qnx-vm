
# BitLab Full-Stack Architecture

BitLab is a full-stack execution platform that allows users to seamlessly write, compile, and simulate code for multiple targets (Verilog, SystemVerilog, VHDL, and QNX).

This document outlines the recent architectural improvements, specifically focusing on asynchronous executions, Kafka workers, Docker containerization, security, and observability.

---

## 1. Asynchronous Execution Architecture (Kafka & SQS)

Previously, Verilog and VHDL code was compiled and executed directly via shell scripts synchronously on the main backend. This approach blocked HTTP threads and did not scale. The architecture has been refactored to be fully asynchronous.

### The Flow:
1. **Request Reception:** The frontend sends the source code (`designCode` and `testbenchCode`) and the `language` to the `/api/execute` backend endpoint.
2. **Job Enqueueing:**
   - **QNX:** The backend sends the job payload to an **AWS SQS** queue (using `SqsJobService`) and instantly returns a `jobId` to the frontend with `status: "queued"`.
   - **Verilog/VHDL:** The backend creates a `KafkaExecutionMessage`, serializes it, and publishes it to a Kafka topic corresponding to the language (`execution_requests_verilog` or `execution_requests_vhdl`). It instantly returns a `jobId` with `status: "queued"`.
3. **Execution (Workers):**
   - Separate containerized workers act as consumers for these tasks. 
   - A Spring Boot Kafka Listener (`KafkaExecutionListener.java`) picks up the message, sets up a unique workspace directory, and invokes the `VerilogVhdlExecutor`.
   - The `VerilogVhdlExecutor` uses Java's native `ProcessBuilder` (bypassing bash scripts completely) to safely invoke the required binaries (`iverilog`, `vvp`, `ghdl`).
4. **Result Storage:** Once compilation and simulation are finished (or timed out), the worker captures the logs, base64-encodes the generated `.vcd` waveform file, extracts explicit error lines (if compilation fails), and persists an `ExecutionResult` to the `ExecutionResultStore` mapped by the `jobId`.
5. **Frontend Polling:** 
   - The frontend (`CodeLab.jsx`), upon receiving the `jobId`, enters a polling loop (pinging `/result/{jobId}` every 2 seconds).
   - Once the backend store registers the result as `DONE`, the frontend consumes the logs, displays the `errorLine` directly in the UI if an error occurred, and automatically loads the decoded `vcdBase64` data into the Waveform Studio.

---

## 2. Docker Compose Infrastructure

We have Dockerized the platform to separate concerns and guarantee reproducible environments. The platform is launched via `docker-compose.yml` into multiple interconnected containers:

1. **kafka & zookeeper:** The messaging layer handling Verilog and VHDL job distribution.
2. **postgres:** The relational database storing User profiles and persistent state.
3. **backend:** The core Java 17 Spring Boot API server. This container is configured with `KAFKA_LISTENER_ENABLED=false` so it acts *only* as the API Gateway and Kafka Producer, not as a worker.
4. **verilog-worker:** An instance of the Java backend extending a custom `Dockerfile.verilog` which installs `iverilog`. It runs with `KAFKA_LISTENER_ENABLED=true` and consumes jobs from the `execution_requests_verilog` topic.
5. **vhdl-worker:** An instance of the Java backend extending a custom `Dockerfile.vhdl` which installs `ghdl`. It consumes jobs from the `execution_requests_vhdl` topic.
6. **frontend:** A Node.js build served by an NGINX alpine container exposing the React application on port 80.

*(Note: The QNX worker runs separately via `worker-aws-sqs-py` reading from AWS SQS).*

---

## 3. JWT Security & Session Management

Authentication has been transitioned from Basic Auth to stateless JWT (JSON Web Tokens).

- **Backend:** 
  - On `/api/auth/login`, `AuthService` validates credentials and generates a signed JWT using `io.jsonwebtoken` (expires in 24 hours).
  - A `JwtFilter` intercepts all incoming requests, extracts the `Bearer` token from the `Authorization` header, validates the signature, and populates the `SecurityContextHolder`.
  - Non-authenticated endpoints like `/api/auth/**` and `/actuator/**` are explicitly whitelisted in `SecurityConfig.java`.
- **Frontend:**
  - Upon successful login, the React app stores the JWT in `localStorage`.
  - The `api.js` Axios interceptor attaches the token as a `Bearer` authorization header dynamically to all subsequent requests.

---

## 4. Error Parsing

When a user submits invalid HDL code, the `KafkaExecutionListener` parses the standard error stream of the compilers:
- **Verilog:** Matches regex `(?:design\.v|tb\.v):(\d+):.*error` to pinpoint the specific failing line number.
- **VHDL:** Matches regex `(?:design\.vhd|tb\.vhd):(\d+):.*error`.
The extracted `errorLine` payload is sent to the frontend and elegantly displayed in the terminal UI for immediate debugging feedback.

---

## 5. Observability & Prometheus Metrics

The Spring Boot backend is equipped with production-grade observability:
- **Spring Boot Actuator** and **Micrometer Prometheus Registry** dependencies are integrated.
- Real-time application metrics (memory usage, CPU, garbage collection, and HTTP request timings) are scraped and exposed at the `/actuator/prometheus` endpoint.
- Health status is accessible at `/actuator/health`.
