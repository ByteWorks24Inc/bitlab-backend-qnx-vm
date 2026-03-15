package com.example.backend.controller;

import com.example.backend.dto.ExecutionResultRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/result")
public class ExecutionResultController {

    @PostMapping
    public void receiveResult(@RequestBody ExecutionResultRequest request) {

        System.out.println("Job result received: " + request.getJobId());
        System.out.println("Logs:");
        System.out.println(request.getLogs());

    }
}
