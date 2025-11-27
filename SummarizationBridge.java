package org.example.core;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.Objects;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.example.auth.TokenManager;


public class SummarizationBridge {
    private static final Gson GSON = new Gson();
    private final String pythonExe;
    private final String scriptPath;
    private final String googleCredentialsPath; // optional: pass path to service account JSON

    public SummarizationBridge(String pythonExe, String scriptPath) {
        this(pythonExe, scriptPath, null);
    }

    public SummarizationBridge(String pythonExe, String scriptPath, String googleCredentialsPath) {
        this.pythonExe = Objects.requireNonNull(pythonExe, "pythonExe required");
        this.scriptPath = Objects.requireNonNull(scriptPath, "scriptPath required");
        this.googleCredentialsPath = googleCredentialsPath;
    }

    // inside SummarizationBridge (or create a new class SummarizationRestBridge)

    public String getPythonExe() { return pythonExe; }
    public String getScriptPath() { return scriptPath; }

    /**
     * Backwards compatible methods
     */
    public String summarize(String text) {
        return summarize(text, null, 60, null);
    }

    public String summarize(String text, String model, long timeoutSeconds) {
        return summarize(text, model, timeoutSeconds, null);
    }

    /**
     * New overload: accepts an OAuth2 access token (Bearer) which will be passed to the Python process
     * as environment variable GOOGLE_OAUTH_ACCESS_TOKEN. If token is null, behavior is unchanged.
     */
    public String summarize(String text, String model, long timeoutSeconds, String accessToken) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(pythonExe, scriptPath);
            // Helpful debug info
            System.out.println("🔍 [SummarizationBridge] Python Exec: " + pythonExe);
            System.out.println("🔍 [SummarizationBridge] Script Path: " + scriptPath);

            // Propagate env var for Google credentials if provided (service account JSON)
            if (googleCredentialsPath != null && !googleCredentialsPath.isEmpty()) {
                pb.environment().put("GOOGLE_APPLICATION_CREDENTIALS", googleCredentialsPath);
                System.out.println("🔍 [SummarizationBridge] Set GOOGLE_APPLICATION_CREDENTIALS -> " + googleCredentialsPath);
            }

            // If an OAuth access token is provided, set it so Python can use it
            if (accessToken != null && !accessToken.isBlank()) {
                pb.environment().put("GOOGLE_OAUTH_ACCESS_TOKEN", accessToken);
                System.out.println("🔍 [SummarizationBridge] Set GOOGLE_OAUTH_ACCESS_TOKEN (token length: " + accessToken.length() + ")");
            }

            // Merge stderr into stdout so we capture all output
            pb.redirectErrorStream(true);
            process = pb.start();

            // Build payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", text);
            if (model != null && !model.isEmpty()) payload.put("model", model);

            // Send payload
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                w.write(GSON.toJson(payload));
                w.flush();
            }

            // Read output (merged stdout + stderr)
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append("\n");
                }
            }

            // Wait with timeout
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "[Summarization error: Python process timed out after " + timeoutSeconds + "s]";
            }

            int code = process.exitValue();
            System.out.println("🔍 [SummarizationBridge] Python exit code: " + code);
            System.out.println("🔍 [SummarizationBridge] Raw output: " + out.toString());

            if (code != 0) {
                return "[Summarization error: Python script returned exit code " + code + "]";
            }

            // Parse JSON response
            Map<String, Object> response;
            try {
                response = GSON.fromJson(out.toString().trim(), Map.class);
            } catch (JsonSyntaxException jse) {
                return "[Summarization error: Invalid JSON returned by Python script]";
            }

            if (response == null) {
                return "[Summarization error: Empty response from Python script]";
            }

            if (response.containsKey("summary")) {
                Object val = response.get("summary");
                return val == null ? "" : val.toString();
            } else if (response.containsKey("error")) {
                return "[Summarization error: " + response.get("error") + "]";
            } else {
                return "[Summarization error: Unexpected response structure]";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "[Summarization Bridge Exception: " + e.getMessage() + "]";
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}

