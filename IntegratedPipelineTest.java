package org.example;

import org.example.core.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class IntegratedPipelineTest {
    public static void main(String[] args) throws Exception {
        // === STEP 1: Setup paths ===
        String pythonExe = "C:\\\\Users\\\\EFM\\\\IdeaProjects\\\\Legal_Project\\\\python\\\\.venv\\\\Scripts\\\\python.exe";
        String preprocessScript = "C:\\\\Users\\\\EFM\\\\IdeaProjects\\\\Legal_Project\\\\python\\\\preprocess.py";
        String ocrScript = "C:\\\\Users\\\\EFM\\\\IdeaProjects\\\\Legal_Project\\\\python\\\\ocr_google.py";
        String modelDir = "C:\\\\Users\\\\EFM\\\\IdeaProjects\\\\Legal_Project\\\\models";

        // === STEP 2: Initialize components ===
        OcrBridge ocr = new OcrBridge(pythonExe, ocrScript);
        TextExtractor extractor = new TextExtractor(ocr);
        ProcessRunner preprocessor = new ProcessRunner(pythonExe, preprocessScript);

        // === STEP 3: Load a test file ===
        File file = new File("C:\\\\Users\\\\EFM\\\\IdeaProjects\\\\Legal_Project\\\\testdocs\\\\sample_doc.png");
        System.out.println("📄 Testing document: " + file.getAbsolutePath());

        // === STEP 4: Extraction Stage (includes OCR fallback) ===
        String rawText = extractor.extract(file);
        System.out.println("🔹 Raw extracted text (first 300 chars):\n" +
                rawText.substring(0, Math.min(300, rawText.length())) + "\n");

        // === STEP 5: Preprocessing Stage ===
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", "preprocess");
        payload.put("text", rawText);

        String processedJson = preprocessor.callBridge(payload);
        System.out.println("🔹 Preprocessed output: " + processedJson + "\n");

        // === STEP 6: Classification Stage (Python model) ===
        Map<String, Object> classifyPayload = new HashMap<>();
        classifyPayload.put("mode", "classify");
        classifyPayload.put("text", rawText);

        ProcessRunner classifier = new ProcessRunner(pythonExe, "C:\\\\Users\\\\EFM\\\\IdeaProjects\\\\Legal_Project\\\\python\\\\evaluate_single.py");
        String classifyResult = classifier.callBridge(classifyPayload);
        System.out.println("🔹 Classification result: " + classifyResult + "\n");

        System.out.println("✅ Pipeline test completed successfully.");
    }
}

