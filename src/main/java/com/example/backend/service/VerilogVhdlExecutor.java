package com.example.backend.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class VerilogVhdlExecutor {

    public String executeVerilog(String workDir, String designCode, String tbCode) throws Exception {
        File dir = new File(workDir);
        dir.mkdirs();

        writeFile(workDir + "/design.v", designCode);
        writeFile(workDir + "/tb.v", tbCode);

        StringBuilder logs = new StringBuilder();

        // Stage 1: Compile
        logs.append("[STAGE:COMPILE] Compiling Verilog sources...\n");
        ProcessBuilder pbCompile = new ProcessBuilder("iverilog", "-o", "output.vvp", "design.v", "tb.v");
        pbCompile.directory(dir);
        pbCompile.redirectErrorStream(true);
        Process pCompile = pbCompile.start();
        String compileOut = readOutput(pCompile);
        if (!compileOut.isBlank()) logs.append(compileOut);
        if (pCompile.waitFor() != 0) {
            logs.append("[STAGE:FAIL] Compilation failed.\n");
            return logs.toString();
        }
        logs.append("[STAGE:OK] Compilation successful.\n");

        // Stage 2: Run simulation
        logs.append("[STAGE:SIM] Running simulation...\n");
        ProcessBuilder pbRun = new ProcessBuilder("vvp", "output.vvp");
        pbRun.directory(dir);
        pbRun.redirectErrorStream(true);
        Process pRun = pbRun.start();

        boolean finished = pRun.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            pRun.destroy();
            logs.append("[STAGE:FAIL] Simulation timed out.\n");
        } else {
            String simOut = readOutput(pRun);
            if (!simOut.isBlank()) logs.append(simOut);
            logs.append("[STAGE:DONE] Simulation complete. Waveform saved.\n");
        }

        return logs.toString();
    }

    public String executeVhdl(String workDir, String designCode, String tbCode) throws Exception {
        File dir = new File(workDir);
        dir.mkdirs();

        designCode = designCode.replace("`", "");
        tbCode = tbCode.replace("`", "");

        writeFile(workDir + "/design.vhd", designCode);
        writeFile(workDir + "/tb.vhd", tbCode);

        StringBuilder logs = new StringBuilder();

        // Stage 1: Analyze design
        logs.append("[STAGE:ANALYZE] Analyzing design unit...\n");
        ProcessBuilder pbAnalyzeD = new ProcessBuilder("ghdl", "-a", "--std=08", "design.vhd");
        pbAnalyzeD.directory(dir);
        pbAnalyzeD.redirectErrorStream(true);
        Process pAnalyzeD = pbAnalyzeD.start();
        String analyzeOut = readOutput(pAnalyzeD);
        if (!analyzeOut.isBlank()) logs.append(analyzeOut);
        if (pAnalyzeD.waitFor() != 0) {
            logs.append("[STAGE:FAIL] Design analysis failed.\n");
            return logs.toString();
        }
        logs.append("[STAGE:OK] Design analyzed successfully.\n");

        // Stage 2: Analyze testbench
        logs.append("[STAGE:ANALYZE] Analyzing testbench...\n");
        ProcessBuilder pbAnalyzeT = new ProcessBuilder("ghdl", "-a", "--std=08", "tb.vhd");
        pbAnalyzeT.directory(dir);
        pbAnalyzeT.redirectErrorStream(true);
        Process pAnalyzeT = pbAnalyzeT.start();
        String tbOut = readOutput(pAnalyzeT);
        if (!tbOut.isBlank()) logs.append(tbOut);
        if (pAnalyzeT.waitFor() != 0) {
            logs.append("[STAGE:FAIL] Testbench analysis failed.\n");
            return logs.toString();
        }
        logs.append("[STAGE:OK] Testbench analyzed successfully.\n");

        // Stage 3: Elaborate
        logs.append("[STAGE:ELAB] Elaborating design...\n");
        ProcessBuilder pbElab = new ProcessBuilder("ghdl", "-e", "--std=08", "tb");
        pbElab.directory(dir);
        pbElab.redirectErrorStream(true);
        Process pElab = pbElab.start();
        String elabOut = readOutput(pElab);
        if (!elabOut.isBlank()) logs.append(elabOut);
        if (pElab.waitFor() != 0) {
            logs.append("[STAGE:FAIL] Elaboration failed.\n");
            return logs.toString();
        }
        logs.append("[STAGE:OK] Elaboration complete.\n");

        // Stage 4: Run simulation with 1000ns stop-time buffer so the last waveform edge is visible
        logs.append("[STAGE:SIM] Running simulation...\n");
        ProcessBuilder pbRun = new ProcessBuilder(
                "ghdl", "-r", "--std=08", "tb", "--vcd=demo.vcd", "--stop-time=1000ns");
        pbRun.directory(dir);
        pbRun.redirectErrorStream(true);
        Process pRun = pbRun.start();

        boolean finished = pRun.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            pRun.destroy();
            logs.append("[STAGE:FAIL] Simulation timed out.\n");
        } else {
            String simOut = readOutput(pRun);
            if (!simOut.isBlank()) logs.append(simOut);
            logs.append("[STAGE:DONE] Simulation complete. Waveform saved.\n");
        }

        return logs.toString();
    }

    private void writeFile(String path, String content) throws Exception {
        try (FileWriter writer = new FileWriter(path)) {
            writer.write(content);
        }
    }

    private String readOutput(Process process) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        return output.toString();
    }

    public String encodeVcdIfExists(String workDir, String vcdName) throws Exception {
        File file = new File(workDir + "/" + vcdName);
        if (!file.exists()) return null;
        byte[] bytes = Files.readAllBytes(file.toPath());
        return Base64.getEncoder().encodeToString(bytes);
    }
}
