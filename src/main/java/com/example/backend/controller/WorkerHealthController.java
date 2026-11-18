package com.example.backend.controller;

import com.example.backend.service.VmCExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/worker")
@RequiredArgsConstructor
public class WorkerHealthController {

    private final VmCExecutor vmCExecutor;

    private static final AtomicLong    lastHeartbeat    = new AtomicLong(0);
    private static final AtomicBoolean explicitlyOnline = new AtomicBoolean(false);

    /** Worker is considered online if heartbeat arrived within this window */
    private static final long ONLINE_THRESHOLD_MS = 30_000;

    /** Called by worker.py on startup → instant ONLINE */
    @PostMapping("/qnx/connect")
    public ResponseEntity<Map<String, String>> connect() {
        explicitlyOnline.set(true);
        lastHeartbeat.set(Instant.now().toEpochMilli());
        System.out.println("[WorkerHealth] QNX worker connected.");
        return ResponseEntity.ok(Map.of("status", "connected"));
    }

    /** Called by worker.py on clean shutdown → instant OFFLINE */
    @PostMapping("/qnx/disconnect")
    public ResponseEntity<Map<String, String>> disconnect() {
        explicitlyOnline.set(false);
        lastHeartbeat.set(0);
        System.out.println("[WorkerHealth] QNX worker disconnected.");
        return ResponseEntity.ok(Map.of("status", "disconnected"));
    }

    /** Called by worker.py every 15s — keeps the session alive for crash detection */
    @PostMapping("/qnx/heartbeat")
    public ResponseEntity<Map<String, String>> heartbeat() {
        lastHeartbeat.set(Instant.now().toEpochMilli());
        explicitlyOnline.set(true);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /** Called by the frontend to check worker status */
    @GetMapping("/qnx/status")
    public ResponseEntity<Map<String, Object>> status() {
        // When the local VMware C runner is configured, report its state so the
        // QNX lab can execute without the AWS SQS worker.
        if (vmCExecutor.isEnabled()) {
            boolean online = vmCExecutor.isAvailable();
            return ResponseEntity.ok(Map.of(
                "online", online,
                "mode", "vm",
                "target", vmCExecutor.describeTarget(),
                "message", online
                    ? "QNX C runner VM is online (" + vmCExecutor.describeTarget() + ")."
                    : "QNX C runner VM is offline. Start bitlab-c-vm in VMware Workstation."
            ));
        }

        // Explicit disconnect takes priority
        if (!explicitlyOnline.get() && lastHeartbeat.get() == 0) {
            return ResponseEntity.ok(Map.of(
                "online", false,
                "message", "QNX worker is offline. Please start worker.py on the QNX machine."
            ));
        }
        long ageMs = Instant.now().toEpochMilli() - lastHeartbeat.get();
        boolean online = explicitlyOnline.get() && ageMs < ONLINE_THRESHOLD_MS;
        return ResponseEntity.ok(Map.of(
            "online", online,
            "lastSeenSeconds", ageMs / 1000,
            "message", online
                ? "QNX worker is online."
                : "QNX worker went offline " + (ageMs / 1000) + "s ago. Please restart worker.py."
        ));
    }
}
