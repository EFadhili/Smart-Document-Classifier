package org.example.core;

import com.google.gson.Gson;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * ProcessRunner — runs a Python bridge script, sends a JSON payload via stdin,
 * and returns the stdout as a String. New overload allows passing extra environment
 * variables to the launched process (useful for passing OAuth access tokens).
 */
public class ProcessRunner {
    private static final Gson GSON = new Gson();
    private final String pythonExe;
    private final String scriptPath;

    public ProcessRunner(String pythonExe, String scriptPath) {
        this.pythonExe = Objects.requireNonNull(pythonExe, "pythonExe required");
        this.scriptPath = Objects.requireNonNull(scriptPath, "scriptPath required");
    }

    /**
     * Backwards-compatible method: no extra environment variables.
     */
    public String callBridge(Map<String, Object> payload) throws Exception {
        return callBridge(payload, null);
    }

    /**
     * Call a Python bridge script with a JSON payload. Optionally pass extra environment
     * variables which will be merged into the child process environment.
     *
     * @param payload  Arbitrary map that will be serialized to JSON and written to the child's stdin
     * @param extraEnv Optional map of env var name -> value to add to the child's environment
     * @return stdout of the process as a String
     * @throws Exception if execution fails or the child exits with non-zero
     */
    public String callBridge(Map<String, Object> payload, Map<String, String> extraEnv) throws Exception {
        System.out.println("🔍 [ProcessRunner DEBUG INFO]");
        System.out.println("➡ Python Executable: " + pythonExe);
        System.out.println("➡ Python Script Path: " + scriptPath);

        // Optional: check python version once per call (useful during debugging)
        try {
            ProcessBuilder versionCheck = new ProcessBuilder(pythonExe, "--version");
            Process versionProcess = versionCheck.start();
            try (BufferedReader vReader = new BufferedReader(new InputStreamReader(versionProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String version = vReader.readLine();
                if (version != null) System.out.println("➡ Python Version (from Java): " + version);
            }
            versionProcess.waitFor();
        } catch (Exception e) {
            // Non-fatal: print a warning but continue — the real execution will fail later if python is missing
            System.err.println("⚠️ Python version check failed: " + e.getMessage());
        }

        ProcessBuilder pb = new ProcessBuilder(pythonExe, scriptPath);

        // Set service account for all Python processes that might need Google Cloud services
        String serviceAccountPath = "C:\\Users\\EFM\\gcloud_key\\vision-service-account.json";
        pb.environment().put("GOOGLE_APPLICATION_CREDENTIALS", serviceAccountPath);
        System.out.println("🔑 Setting GOOGLE_APPLICATION_CREDENTIALS for all Python processes");

        // merge extra env variables if provided
        if (extraEnv != null && !extraEnv.isEmpty()) {
            Map<String, String> env = pb.environment();
            env.putAll(extraEnv);
            System.out.println("➡ Added env keys: " + extraEnv.keySet());
        }

        pb.redirectErrorStream(true);
        Process p = pb.start();

        // Rest of the method remains the same...
        // Send JSON payload to stdin
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8))) {
            w.write(GSON.toJson(payload));
            w.flush();
        }

        // Read stdout from Python
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
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
