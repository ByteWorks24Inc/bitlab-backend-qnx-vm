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

        String designPath = workDir + "/design.v";
        String tbPath = workDir + "/tb.v";

        writeFile(designPath, designCode);
        writeFile(tbPath, tbCode);

        StringBuilder logs = new StringBuilder();

        // Compile
        ProcessBuilder pbCompile = new ProcessBuilder("iverilog", "-o", "output.vvp", "design.v", "tb.v");
        pbCompile.directory(dir);
        pbCompile.redirectErrorStream(true);
        Process pCompile = pbCompile.start();
        logs.append(readOutput(pCompile));
        if (pCompile.waitFor() != 0) {
            logs.append("\nCompilation failed");
            return logs.toString();
        }

        // Run
        ProcessBuilder pbRun = new ProcessBuilder("vvp", "output.vvp");
        pbRun.directory(dir);
        pbRun.redirectErrorStream(true);
        Process pRun = pbRun.start();
        
        boolean finished = pRun.waitFor(5, TimeUnit.SECONDS);
        if (!finished) {
            pRun.destroy();
            logs.append("\nExecution timed out");
        } else {
            logs.append(readOutput(pRun));
        }

        return logs.toString();
    }

    public String executeVhdl(String workDir, String designCode, String tbCode) throws Exception {
        File dir = new File(workDir);
        dir.mkdirs();

        // Clean code (remove backticks)
        designCode = designCode.replace("`", "");
        tbCode = tbCode.replace("`", "");

        String designPath = workDir + "/design.vhd";
        String tbPath = workDir + "/tb.vhd";

        writeFile(designPath, designCode);
        writeFile(tbPath, tbCode);

        StringBuilder logs = new StringBuilder();

        // Analyze design
        ProcessBuilder pbAnalyzeD = new ProcessBuilder("ghdl", "-a", "design.vhd");
        pbAnalyzeD.directory(dir);
        pbAnalyzeD.redirectErrorStream(true);
        Process pAnalyzeD = pbAnalyzeD.start();
        logs.append(readOutput(pAnalyzeD));
        if (pAnalyzeD.waitFor() != 0) return logs.append("\nAnalysis failed").toString();

        // Analyze tb
        ProcessBuilder pbAnalyzeT = new ProcessBuilder("ghdl", "-a", "tb.vhd");
        pbAnalyzeT.directory(dir);
        pbAnalyzeT.redirectErrorStream(true);
        Process pAnalyzeT = pbAnalyzeT.start();
        logs.append(readOutput(pAnalyzeT));
        if (pAnalyzeT.waitFor() != 0) return logs.append("\nAnalysis failed").toString();

        // Elaborate
        ProcessBuilder pbElab = new ProcessBuilder("ghdl", "-e", "tb");
        pbElab.directory(dir);
        pbElab.redirectErrorStream(true);
        Process pElab = pbElab.start();
        logs.append(readOutput(pElab));
        if (pElab.waitFor() != 0) return logs.append("\nElaboration failed").toString();

        // Run
        ProcessBuilder pbRun = new ProcessBuilder("ghdl", "-r", "tb", "--vcd=demo.vcd");
        pbRun.directory(dir);
        pbRun.redirectErrorStream(true);
        Process pRun = pbRun.start();
        
        boolean finished = pRun.waitFor(5, TimeUnit.SECONDS);
        if (!finished) {
            pRun.destroy();
            logs.append("\nExecution timed out");
        } else {
            logs.append(readOutput(pRun));
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
