package com.example.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * Checks whether the QNX Python worker (worker.py) is online.
 * The worker is expected to expose a /health endpoint on its port.
 */
@RestController
@RequestMapping("/api/worker")
@RequiredArgsConstructor
public class WorkerHealthController {

    @Value("${qnx.worker.health-url:http://localhost:5050/health}")
    private String qnxWorkerHealthUrl;

    @GetMapping("/qnx/status")
    public ResponseEntity<Map<String, Object>> qnxWorkerStatus() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(qnxWorkerHealthUrl).openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                return ResponseEntity.ok(Map.of("online", true, "message", "QNX worker is online."));
            }
        } catch (Exception ignored) {
            // Connection refused or timeout = worker is offline
        }
        return ResponseEntity.ok(Map.of("online", false, "message", "QNX worker is offline. Please contact an admin to start the QNX environment."));
    }
}
