package com.example.backend.controller;

import com.example.backend.dto.ExecutionRequest;
import com.example.backend.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;

@RestController
@RequestMapping("/api/execute")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ExecutionController {

    private final ExecutionService executionService;

    // Run execution normally
    @PostMapping
    public Object execute(@RequestBody ExecutionRequest request) throws Exception {
        return executionService.execute(request);
    }

    // Run execution + return waveform
    @PostMapping("/graph")
    public ResponseEntity<FileSystemResource> getGraph(
            @RequestBody ExecutionRequest request) throws Exception {

        // Execute simulation first
        executionService.execute(request);

        String language = request.getLanguage();
        String path = "";

        switch (language.toLowerCase()) {

            case "verilog":
                path = "/home/ubuntu/verilog/demo.vcd";
                break;

            case "vhdl":
                path = "/home/ubuntu/vhdl/demo.vcd";
                break;

            case "systemverilog":
                path = "/home/ubuntu/systemverilog/demo.vcd";
                break;

            default:
                return ResponseEntity.badRequest().build();
        }

        File file = new File(path);

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header("Content-Type", "application/octet-stream")
                .body(new FileSystemResource(file));
    }
}
