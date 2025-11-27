package org.example.core;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class OcrBridge {
    private static final Gson GSON = new Gson();
    private final String pythonExe;
    private final String scriptPath;
    private String serviceAccountPath;

    public OcrBridge(String pythonExe, String scriptPath) {
        this(pythonExe, scriptPath, null);
    }

    public OcrBridge(String pythonExe, String scriptPath, String serviceAccountPath) {
        this.pythonExe = Objects.requireNonNull(pythonExe, "pythonExe required");
        this.scriptPath = Objects.requireNonNull(scriptPath, "scriptPath required");

        // Set service account path from parameter, system property, or environment variable
        if (serviceAccountPath != null) {
            this.serviceAccountPath = serviceAccountPath;
        } else {
            this.serviceAccountPath = System.getProperty("google.application.credentials");
            if (this.serviceAccountPath == null) {
                this.serviceAccountPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            }
        }
    }

    public String getPythonExe() { return pythonExe; }
    public String getScriptPath() { return scriptPath; }

    // Overloaded method for backward compatibility
    public String ocr(File file) {
        return ocr(file, null);
    }

    public String ocr(File file, String accessToken) {
        Process process = null;
        try {
            System.out.println("🔍 [OCR DEBUG INFO]");
            System.out.println("➡ Python Executable: " + pythonExe);
            System.out.println("➡ OCR Script Path: " + scriptPath);
            System.out.println("➡ File Target: " + file.getAbsolutePath());
            System.out.println("➡ Service Account Path: " + serviceAccountPath);

            // Verify the service account file exists
            if (serviceAccountPath == null || serviceAccountPath.isBlank()) {
                return "[OCR Error: GOOGLE_APPLICATION_CREDENTIALS not configured. Set system property 'google.application.credentials']";
            }

            File saFile = new File(serviceAccountPath);
            if (!saFile.exists()) {
                return "[OCR Error: Service account file not found: " + serviceAccountPath + "]";
            }
            System.out.println("✅ Service account file exists");

            // Test Python availability
            try {
                ProcessBuilder versionCheck = new ProcessBuilder(pythonExe, "--version");
                Process vproc = versionCheck.start();
                vproc.waitFor();
            } catch (Exception e) {
                return "[OCR Error: Python not available - " + e.getMessage() + "]";
            }

            ProcessBuilder pb = new ProcessBuilder(pythonExe, scriptPath);

            // CRITICAL: Set service account credentials for the Python process
            pb.environment().put("GOOGLE_APPLICATION_CREDENTIALS", serviceAccountPath);
            System.out.println("🔑 Setting GOOGLE_APPLICATION_CREDENTIALS for Python process: " + serviceAccountPath);

            // Remove any OAuth token to ensure only service account is used for Vision API
            pb.environment().remove("GOOGLE_OAUTH_ACCESS_TOKEN");
            System.out.println("🔒 Removed OAuth token from environment for Vision API");

            pb.redirectErrorStream(true);
            process = pb.start();

            Map<String, Object> payload = new HashMap<>();
            payload.put("path", file.getAbsolutePath());

            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write(GSON.toJson(payload));
                writer.flush();
            }

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            String rawOutput = output.toString().trim();
            System.out.println("➡ Python Exit Code: " + exitCode);

            if (exitCode != 0) {
                return "[OCR Error: Python process failed (exit=" + exitCode + ")]";
            }

            if (rawOutput.isEmpty()) {
                return "[OCR Error: Empty response from Python script]";
            }

            // Try to parse JSON response
            try {
                // Extract JSON from the output (in case there's debug info mixed in)
                String jsonOutput = rawOutput;
                int jsonStart = rawOutput.indexOf('{');
                int jsonEnd = rawOutput.lastIndexOf('}');

                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    jsonOutput = rawOutput.substring(jsonStart, jsonEnd + 1);
                    System.out.println("📋 Extracted JSON from output");
                }

                Map<String, Object> response = GSON.fromJson(jsonOutput, Map.class);
                if (response == null) {
                    return "[OCR Error: Null JSON response. Raw: " + rawOutput + "]";
                }

                if (response.containsKey("text")) {
                    String text = String.valueOf(response.get("text"));
                    return text.isEmpty() ? "[No text extracted]" : text;
                } else if (response.containsKey("error")) {
                    return "[OCR Error: " + response.get("error") + "]";
                } else {
                    return "[OCR Error: Unexpected response format: " + rawOutput + "]";
                }
            } catch (JsonSyntaxException e) {
                if (rawOutput.toLowerCase().contains("error")) {
                    return "[OCR Error: " + rawOutput + "]";
                }
                return "[OCR Error: Invalid JSON response: " + rawOutput + "]";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "[OCR Bridge Exception: " + e.getMessage() + "]";
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}