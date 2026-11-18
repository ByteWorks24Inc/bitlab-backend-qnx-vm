package com.example.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs QNX/C lab programs inside the local VMware "normal C" VM.
 *
 * The source is copied over SSH, compiled with gcc on the guest and executed
 * there, so nothing runs natively on the Windows host. This replaces the AWS
 * SQS worker path when qnx.runner.mode=vm.
 */
@Service
public class VmCExecutor {

    @Value("${qnx.runner.mode:vm}")
    private String runnerMode;

    @Value("${qnx.vm.host:}")
    private String host;

    @Value("${qnx.vm.user:bitlab}")
    private String user;

    @Value("${qnx.vm.ssh-key:${user.home}/.ssh/id_ed25519}")
    private String sshKey;

    @Value("${qnx.vm.remote-dir:/tmp/bitlab}")
    private String remoteDir;

    @Value("${qnx.vm.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${qnx.vm.run-timeout-seconds:10}")
    private int runTimeoutSeconds;

    @Value("${qnx.vm.ssh-command:ssh}")
    private String sshCommand;

    @Value("${qnx.vm.scp-command:scp}")
    private String scpCommand;

    private static final long CHECK_TTL_MS = 15_000;
    private final AtomicLong lastCheck = new AtomicLong(0);
    private volatile boolean lastAvailable = false;

    /** True when the VM runner is configured (mode=vm and a host is known). */
    public boolean isEnabled() {
        return "vm".equalsIgnoreCase(runnerMode)
                && host != null && !host.isBlank();
    }

    public String describeTarget() {
        return user + "@" + host;
    }

    /**
     * Cached reachability probe. The frontend polls /api/worker/qnx/status,
     * so we avoid opening an SSH connection on every request.
     */
    public synchronized boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (now - lastCheck.get() < CHECK_TTL_MS) {
            return lastAvailable;
        }
        lastCheck.set(now);
        if (!isEnabled()) {
            lastAvailable = false;
            return false;
        }
        try {
            ProcessResult result = run(sshCommand("echo BITLAB_OK"), 8);
            lastAvailable = result.exitCode == 0 && result.output.contains("BITLAB_OK");
        } catch (Exception e) {
            lastAvailable = false;
        }
        return lastAvailable;
    }

    /**
     * Copies the C source to the VM, compiles it with gcc and runs it.
     *
     * @param workDir local per-job directory (also used for the remote folder name)
     * @param code    the C source submitted by the UI
     * @return human-readable stage log shown in the BitLab terminal
     */
    public String execute(String workDir, String code) throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException("QNX VM runner is not enabled (set qnx.runner.mode=vm and qnx.vm.host)");
        }

        Path dir = Path.of(workDir);
        Files.createDirectories(dir);
        Path source = dir.resolve("main.c");
        Files.writeString(source, code == null ? "" : code, StandardCharsets.UTF_8);

        String jobName = dir.getFileName().toString();
        String jobDir = remoteDir + "/" + jobName;

        StringBuilder logs = new StringBuilder();
        logs.append("[STAGE:VM] Ubuntu C VM: ").append(describeTarget()).append('\n');

        ProcessResult mkdir = run(sshCommand("mkdir -p " + jobDir), 10);
        if (mkdir.exitCode != 0) {
            throw new IllegalStateException("Cannot reach the QNX C VM at " + describeTarget() + ": " + mkdir.output.trim());
        }

        logs.append("[STAGE:COPY] Uploading main.c to the VM...\n");
        ProcessResult upload = run(scp(source.toString(), jobDir + "/main.c"), 30);
        if (upload.exitCode != 0) {
            throw new IllegalStateException("Upload to the QNX C VM failed: " + upload.output.trim());
        }

        logs.append("[STAGE:COMPILE] gcc -Wall -Wextra -pthread -o prog main.c -lm\n");
        ProcessResult compile = run(
                sshCommand("cd " + jobDir + " && gcc -Wall -Wextra -pthread -o prog main.c -lm 2>&1"),
                timeoutSeconds);
        if (!compile.output.isBlank()) {
            logs.append(compile.output);
            if (!compile.output.endsWith("\n")) logs.append('\n');
        }
        if (compile.exitCode != 0) {
            logs.append("[STAGE:FAILED] Compilation failed (exit ").append(compile.exitCode).append(").\n");
            return logs.toString();
        }

        logs.append("[STAGE:RUN] ./prog (limit ").append(runTimeoutSeconds).append("s)\n");
        ProcessResult execution = run(
                sshCommand("cd " + jobDir + " && timeout -k 2s " + runTimeoutSeconds + "s ./prog 2>&1"),
                timeoutSeconds);
        if (!execution.output.isBlank()) {
            logs.append(execution.output);
            if (!execution.output.endsWith("\n")) logs.append('\n');
        }
        if (execution.exitCode == 124) {
            logs.append("[STAGE:TIMEOUT] Program exceeded ").append(runTimeoutSeconds).append(" seconds and was stopped.\n");
        }
        logs.append("[STAGE:DONE] Execution finished (exit ").append(execution.exitCode).append(").\n");
        return logs.toString();
    }

    // ------------------------------------------------------------------
    // SSH helpers
    // ------------------------------------------------------------------

    private List<String> sshCommand(String remoteCommand) {
        List<String> cmd = new ArrayList<>();
        cmd.add(sshCommand);
        cmd.add("-i"); cmd.add(sshKey);
        cmd.add("-o"); cmd.add("BatchMode=yes");
        cmd.add("-o"); cmd.add("StrictHostKeyChecking=no");
        cmd.add("-o"); cmd.add("UserKnownHostsFile=NUL");
        cmd.add("-o"); cmd.add("LogLevel=ERROR");
        cmd.add("-o"); cmd.add("ConnectTimeout=5");
        cmd.add("-o"); cmd.add("IdentitiesOnly=yes");
        cmd.add(describeTarget());
        cmd.add(remoteCommand);
        return cmd;
    }

    private List<String> scp(String localFile, String remoteFile) {
        List<String> cmd = new ArrayList<>();
        cmd.add(scpCommand);
        cmd.add("-i"); cmd.add(sshKey);
        cmd.add("-o"); cmd.add("BatchMode=yes");
        cmd.add("-o"); cmd.add("StrictHostKeyChecking=no");
        cmd.add("-o"); cmd.add("UserKnownHostsFile=NUL");
        cmd.add("-o"); cmd.add("LogLevel=ERROR");
        cmd.add("-o"); cmd.add("ConnectTimeout=5");
        cmd.add("-o"); cmd.add("IdentitiesOnly=yes");
        cmd.add(localFile);
        cmd.add(describeTarget() + ":" + remoteFile);
        return cmd;
    }

    private ProcessResult run(List<String> command, int timeoutSec) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    output.append(line).append('\n');
                }
            } catch (Exception ignored) {
                // stream closed when the process is killed
            }
        }, "qnx-vm-ssh-reader");
        reader.setDaemon(true);
        reader.start();

        boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            reader.join(2000);
            return new ProcessResult(-1, output + "[TIMEOUT] SSH command did not finish within " + timeoutSec + "s.\n");
        }
        reader.join(2000);
        return new ProcessResult(process.exitValue(), output.toString());
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
