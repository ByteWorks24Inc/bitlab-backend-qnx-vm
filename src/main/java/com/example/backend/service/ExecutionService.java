package com.example.backend.service;

import com.example.backend.dto.ExecutionRequest;
import com.example.backend.dto.ExecutionResponse;
import com.example.backend.dto.QueueExecutionResponse;
import com.example.backend.util.ShellExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final SqsJobService sqsJobService;

    public String generateWaveformImage(String vcdPath) throws Exception {

    String outputPath = System.getProperty("user.home") + "/output/" 
            + java.util.UUID.randomUUID() + ".png";

    ProcessBuilder pb = new ProcessBuilder(
            System.getProperty("user.home") + "/scripts/generate_waveform.sh",
            vcdPath,
            outputPath
    );

    pb.redirectErrorStream(true);

    Process process = pb.start();

    java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(process.getInputStream())
    );

    String line;
    while ((line = reader.readLine()) != null) {
        System.out.println(line); // logs
    }

    int exitCode = process.waitFor();

    if (exitCode != 0) {
        throw new RuntimeException("Waveform generation failed");
    }

    return outputPath;
}
    
    public Object execute(ExecutionRequest request) throws Exception {

        String workDir = "workspace/" + UUID.randomUUID();
        File dir = new File(workDir);
        dir.mkdirs();

        // ================= VERILOG =================
        if (request.getLanguage().equalsIgnoreCase("verilog")) {

            String designPath = workDir + "/design.v";
            String tbPath = workDir + "/tb.v";

            writeFile(designPath, request.getDesignCode());
            writeFile(tbPath, request.getTestbenchCode());

            String logs = ShellExecutor.execute(
                    "scripts/run_verilog.sh",
                    designPath,
                    tbPath
            );

            String vcd = encodeIfExists(System.getProperty("user.home") + "/verilog/demo.vcd");

            return ExecutionResponse.builder()
                    .status("success")
                    .logs(logs)
                    .vcdBase64(vcd)
                    .build();
        }

        // ================= VHDL =================
        if (request.getLanguage().equalsIgnoreCase("vhdl")) {

            String designPath = workDir + "/design.vhd";
            String tbPath = workDir + "/tb.vhd";

            writeFile(designPath, request.getDesignCode());
            writeFile(tbPath, request.getTestbenchCode());

            String logs = ShellExecutor.execute(
                    "scripts/run_vhdl.sh",
                    designPath,
                    tbPath
            );

            String vcd = encodeIfExists(System.getProperty("user.home") + "/vhdl/demo.vcd");

            return ExecutionResponse.builder()
                    .status("success")
                    .logs(logs)
                    .vcdBase64(vcd)
                    .build();
        }
        // ============= SYSTEM VERILOG =================
        if (request.getLanguage().equalsIgnoreCase("systemverilog")) {

            String designPath = workDir + "/design.sv";
            String tbPath = workDir + "/tb.sv";

            writeFile(designPath, request.getDesignCode());
            writeFile(tbPath, request.getTestbenchCode());

            String logs = ShellExecutor.execute(
                    "scripts/run_sverilog.sh",
                    designPath,
                    tbPath
            );

            String vcd = encodeIfExists(System.getProperty("user.home") + "/sverilog/demo.vcd");

            return ExecutionResponse.builder()
                    .status("success")
                    .logs(logs)
                    .vcdBase64(vcd)
                    .build();
        }

        // ================= QNX =================
        if (request.getLanguage().equalsIgnoreCase("qnx")) {

            String jobId = UUID.randomUUID().toString();

            sqsJobService.sendJob(
                    jobId,
                    "qnx",
                    request.getDesignCode()
            );

            return QueueExecutionResponse.builder()
                    .status("queued")
                    .jobId(jobId)
                    .build();
        }

        throw new RuntimeException("Invalid language");
    }

    private void writeFile(String path, String content) throws Exception {
        try (FileWriter writer = new FileWriter(path)) {
            writer.write(content);
        }
    }

    private String encodeIfExists(String path) throws Exception {
        File file = new File(path);
        if (!file.exists()) return null;

        byte[] bytes = Files.readAllBytes(file.toPath());
        return Base64.getEncoder().encodeToString(bytes);
    }
}
