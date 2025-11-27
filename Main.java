package org.example.core;

import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        try {
            // Path to your virtualenv Python
            String pythonExe = "C:\\Users\\EFM\\IdeaProjects\\Legal_Project\\python\\.venv\\Scripts\\python.exe";
            String scriptPath = "C:\\Users\\EFM\\IdeaProjects\\Legal_Project\\python\\preprocess.py";

            ProcessRunner runner = new ProcessRunner(pythonExe, scriptPath);

            // Build JSON payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("mode", "preprocess");
            payload.put("text", "This is A SAMPLE text, with punctuation!!!");

            // Call Python bridge
            String result = runner.callBridge(payload);
            System.out.println("Preprocessed output: " + result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
