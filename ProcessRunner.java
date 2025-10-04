package org.example.core;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
        ProcessBuilder pb = new ProcessBuilder(pythonExe, scriptPath);
        Process p = pb.start();

        // Write payload to stdin
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8))) {
            w.write(GSON.toJson(payload));
            w.flush();
        }

        // Read stdout
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
        }

        int code = p.waitFor();
        if (code != 0) throw new RuntimeException("Bridge exited with code: " + code);

        return sb.toString();
    }
}
