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
public class ExecutionController {

    private final ExecutionService executionService;

    @PostMapping
    public Object execute(@RequestBody ExecutionRequest request) throws Exception {
        return executionService.execute(request);
    }
@GetMapping("/graph")
public ResponseEntity<FileSystemResource> getGraph(
        @RequestParam String language) {

    String vcdPath = "";

    switch (language.toLowerCase()) {
        case "verilog":
            vcdPath = System.getProperty("user.home") + "/verilog/demo.vcd";
            break;
        case "vhdl":
            vcdPath = System.getProperty("user.home") + "/vhdl/demo.vcd";
            break;
        case "systemverilog":
            vcdPath = System.getProperty("user.home") + "/sverilog/demo.vcd";
            break;
        default:
            return ResponseEntity.badRequest().build();
    }

    File vcdFile = new File(vcdPath);

    if (!vcdFile.exists()) {
        return ResponseEntity.notFound().build();
    }

    // ✅ RETURN RAW VCD (NO PNG, NO PROCESSING)
    return ResponseEntity.ok()
            .header("Content-Type", "text/plain")
            .header("Content-Disposition", "inline; filename=\"demo.vcd\"")
            .body(new FileSystemResource(vcdFile));
}
}
