package com.example.backend.controller;

import lombok.RequiredArgsConstructor;
import com.example.backend.service.*;
import com.example.backend.dto.*;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/result")
@RequiredArgsConstructor
public class ExecutionResultController {

    private final ExecutionResultStore store;

    @PostMapping
    public void receiveResult(@RequestBody ExecutionResultRequest request) {
        store.save(request.getJobId(), ExecutionResult.builder()
                .status("DONE")
                .logs(request.getLogs())
                .build());
        System.out.println("Job result received: " + request.getJobId());
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<?> getResult(@PathVariable String jobId) {
        ExecutionResult result = store.get(jobId);

        if (result == null) {
            return ResponseEntity.ok(Map.of("status", "RUNNING"));
        }
        
        return ResponseEntity.ok(result);
    }
}
