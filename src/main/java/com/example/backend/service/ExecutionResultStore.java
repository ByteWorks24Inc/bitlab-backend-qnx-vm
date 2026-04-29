package com.example.backend.service;

import com.example.backend.dto.ExecutionResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExecutionResultStore {

    private final ConcurrentHashMap<String, ExecutionResult> results = new ConcurrentHashMap<>();

    public void save(String jobId, ExecutionResult result) {
        results.put(jobId, result);
    }

    public ExecutionResult get(String jobId) {
        return results.get(jobId);
    }
}
