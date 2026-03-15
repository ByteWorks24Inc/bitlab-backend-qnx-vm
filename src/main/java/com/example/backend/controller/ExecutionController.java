package com.example.backend.controller;

import com.example.backend.dto.ExecutionRequest;
import com.example.backend.dto.ExecutionResponse;
import com.example.backend.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/execute")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionService executionService;

    @PostMapping
    public Object execute(@RequestBody ExecutionRequest request) throws Exception {
        return executionService.execute(request);
    }
}
