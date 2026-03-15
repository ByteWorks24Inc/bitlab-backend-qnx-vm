package com.example.backend.util;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ShellExecutor {

    public static String execute(String scriptPath, String... args)
            throws IOException, InterruptedException {

        List<String> command = new ArrayList<>();
        command.add(scriptPath);

        for (String arg : args) {
            command.add(arg);
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);

        Process process = builder.start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );

        StringBuilder output = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        process.waitFor();

        return output.toString();
    }
}