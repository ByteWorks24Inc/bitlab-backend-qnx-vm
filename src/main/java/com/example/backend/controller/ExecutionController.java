package com.example.backend.controller;

import com.example.backend.dto.ExecutionRequest;
import com.example.backend.dto.ExecutionResponse;
import com.example.backend.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/execute")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ExecutionController {

    private final ExecutionService executionService;

    @PostMapping
    public Object execute(@RequestBody ExecutionRequest request) throws Exception {
        return executionService.execute(request);
    }
    @GetMapping("/graph")
    public ResponseEntity<FileSystemResource> getGraph(
            @RequestParam String language) {

        String path = "";

        switch (language) {
            case "verilog":
                path = "/home/ubuntu/verilog/demo.vcd";
                break;

            case "vhdl":
                path = "/home/ubuntu/vhdl/demo.vcd";
                break;

            case "systemverilog":
                path = "/home/ubuntu/sverilog/demo.vcd";
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
