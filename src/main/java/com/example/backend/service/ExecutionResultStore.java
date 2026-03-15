package com.example.backend.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExecutionResultStore {

    private final ConcurrentHashMap<String, String> results = new ConcurrentHashMap<>();

    public void save(String jobId, String logs) {
        results.put(jobId, logs);
    }

    public String get(String jobId) {
        return results.get(jobId);
    }
}
