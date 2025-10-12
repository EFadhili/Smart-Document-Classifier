package org.example.core;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class OcrBridge {
    private static final Gson GSON = new Gson();
    private final String pythonExe;
    private final String scriptPath;

    public OcrBridge(String pythonExe, String scriptPath) {
        this.pythonExe = pythonExe;
        this.scriptPath = scriptPath;
    }

    /**
     * Calls the Python OCR script and returns the extracted text or an error message.
     */
    public String ocr(File file) {
        try {
            System.out.println("🔍 [OCR DEBUG INFO]");
            System.out.println("➡ Python Executable: " + pythonExe);
            System.out.println("➡ OCR Script Path: " + scriptPath);
            System.out.println("➡ File Target: " + file.getAbsolutePath());

            // 🧠 Check Python interpreter version before running OCR
            ProcessBuilder versionCheck = new ProcessBuilder(pythonExe, "--version");
            Process versionProcess = versionCheck.start();
            BufferedReader versionReader = new BufferedReader(
                    new InputStreamReader(versionProcess.getInputStream()));
            String versionOutput = versionReader.readLine();
            versionProcess.waitFor();
            System.out.println("➡ Python Version (from Java): " + versionOutput);

            ProcessBuilder pb = new ProcessBuilder(pythonExe, scriptPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

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
            System.out.println("➡ Python Process Exit Code: " + exitCode);
            System.out.println("📤 Raw OCR Output:\n" + output);

            if (exitCode != 0) {
                return "[OCR Error: Python process failed (exit=" + exitCode + ")]";
            }

            Map<String, Object> response = GSON.fromJson(output.toString(), Map.class);
            if (response == null) {
                return "[OCR Error: No JSON response]";
            }

            if (response.containsKey("text")) {
                return response.get("text").toString();
            } else if (response.containsKey("error")) {
                return "[OCR Error: " + response.get("error") + "]";
            } else {
                return "[OCR Error: Unknown JSON response structure]";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "[OCR Bridge Exception: " + e.getMessage() + "]";
        }
    }
}