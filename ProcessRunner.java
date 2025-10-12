package org.example.core;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ProcessRunner {
    private static final Gson GSON = new Gson();
    private final String pythonExe;
    private final String scriptPath;

    public ProcessRunner(String pythonExe, String scriptPath) {
        this.pythonExe = pythonExe;
        this.scriptPath = scriptPath;
    }

    public String callBridge(Map<String, Object> payload) throws Exception {
        System.out.println("🔍 [ProcessRunner DEBUG INFO]");
        System.out.println("➡ Python Executable: " + pythonExe);
        System.out.println("➡ Python Script Path: " + scriptPath);

        // Check Python version before running
        ProcessBuilder versionCheck = new ProcessBuilder(pythonExe, "--version");
        Process versionProcess = versionCheck.start();
        try (BufferedReader vReader = new BufferedReader(new InputStreamReader(versionProcess.getInputStream()))) {
            String version = vReader.readLine();
            System.out.println("➡ Python Version (from Java): " + version);
        }
        versionProcess.waitFor();

        ProcessBuilder pb = new ProcessBuilder(pythonExe, scriptPath);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        // Send JSON payload to stdin
        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8))) {
            w.write(GSON.toJson(payload));
            w.flush();
        }

        // Read stdout from Python
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }

        int code = p.waitFor();
        System.out.println("➡ Python Exit Code: " + code);
        System.out.println("📤 Raw Python Output:\n" + sb);

        if (code != 0) {
            throw new RuntimeException("Bridge exited with code: " + code);
        }

        return sb.toString();
    }
}