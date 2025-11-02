package org.example.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.example.core.ProcessRunner;
import org.example.core.TextExtractor;
import org.example.core.SummarizationBridge;
import org.example.core.OcrBridge;
import org.example.storage.StorageManager;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Controller that hides storage/process details from the UI components.
 * Updated to parse JSON strings returned by ProcessRunner into Map<String,Object>.
 */
public class UIController {
    private MainWindow mainWindow;

    // backend collaborators
    private StorageManager storage;
    private final ProcessRunner preprocessor;
    private final ProcessRunner classifier;
    private final TextExtractor extractor;
    private final SummarizationBridge summarizer;

    private final Gson gson = new Gson();
    private final Type mapType = new TypeToken<Map<String, Object>>() {}.getType();

    public UIController() {
        // configure paths to your python executables and scripts here
        String python = "C:\\Users\\EFM\\IdeaProjects\\Legal_Project\\python\\.venv\\Scripts\\python.exe";
        String preprocessScript = "C:\\Users\\EFM\\IdeaProjects\\Legal_Project\\python\\preprocess.py";
        String classifierScript = "C:\\Users\\EFM\\IdeaProjects\\Legal_Project\\python\\evaluate_single.py";
        String ocrScript = "C:\\Users\\EFM\\IdeaProjects\\Legal_Project\\python\\ocr_google.py";
        String summarizerScript = "C:\\Users\\EFM\\IdeaProjects\\Legal_Project\\python\\summarize_gemini.py";
        String creds = "C:\\Users\\EFM\\gcloud_key\\legal-ocr-project-474912-7416611b42c0.json";

        this.preprocessor = new ProcessRunner(python, preprocessScript);
        this.classifier = new ProcessRunner(python, classifierScript);
        this.extractor = new TextExtractor(new OcrBridge(python, ocrScript));
        this.summarizer = new SummarizationBridge(python, summarizerScript, creds);

        // try to init StorageManager (non-fatal)
        try {
            String projectRoot = "C:\\Users\\EFM\\IdeaProjects\\Legal_Project";
            this.storage = new StorageManager(projectRoot);
        } catch (Exception e) {
            this.storage = null;
            System.err.println("Storage init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setMainWindow(MainWindow w) { this.mainWindow = w; }

    public Path uploadFile(File f) throws Exception {
        if (storage == null) throw new IllegalStateException("Storage not available");
        return storage.storeOriginal(f);
    }

    /**
     * Run pipeline asynchronously. This method expects ProcessRunner.callBridge(...) to return a JSON string.
     * It parses those JSON strings into Map<String,Object> using Gson before composing the final result map.
     *
     * onComplete receives a Map with keys: extracted, preprocessed, prediction, confidence, summary
     */
    public void runPipeline(File file, Consumer<Map<String,Object>> onComplete, Consumer<Exception> onError) {
        new Thread(() -> {
            try {
                // 1) extract
                String extracted = extractor.extract(file);
                if (extracted == null) extracted = "";

                // 2) preprocess (call bridge returns JSON string)
                String preRaw = preprocessor.callBridge(Map.of("mode", "preprocess", "text", extracted));
                Map<String, Object> preResp = tryParseJsonToMap(preRaw);
                String preprocessed;
                if (preResp != null) {
                    // prefer "normalized" if present, else try "tokens" or fallback to raw string
                    Object norm = preResp.getOrDefault("normalized", preResp.getOrDefault("normalized_text", preResp.get("text")));
                    if (norm != null) preprocessed = norm.toString();
                    else preprocessed = extracted;
                } else {
                    // if parsing failed, try to use the raw string directly
                    preprocessed = preRaw != null ? preRaw : extracted;
                }

                // 3) classify (call bridge returns JSON string)
                String classRaw = classifier.callBridge(Map.of("text", preprocessed));
                Map<String, Object> classResp = tryParseJsonToMap(classRaw);
                String prediction = "Unknown";
                double confidence = 0.0;
                if (classResp != null) {
                    if (classResp.get("prediction") != null) prediction = classResp.get("prediction").toString();
                    Object confObj = classResp.get("confidence");
                    if (confObj instanceof Number) confidence = ((Number) confObj).doubleValue();
                    else if (confObj != null) {
                        try { confidence = Double.parseDouble(confObj.toString()); } catch (Exception ignored) {}
                    }
                } else {
                    // if classRaw is a plain string (not JSON), treat it as prediction text
                    if (classRaw != null && !classRaw.isBlank()) prediction = classRaw;
                }

                // 4) summarise
                String summary = summarizer.summarize(extracted, "models/gemini-2.5-flash", 120);

                Map<String,Object> out = new HashMap<>();
                out.put("extracted", extracted);
                out.put("preprocessed", preprocessed);
                out.put("prediction", prediction);
                out.put("confidence", confidence);
                out.put("summary", summary);

                onComplete.accept(out);
            } catch (Exception e) {
                onError.accept(e);
            }
        }, "pipeline-thread").start();
    }

    public boolean saveResults(File originalFile, Map<String,Object> pipelineResult) throws Exception {
        if (storage == null) throw new IllegalStateException("Storage not available");
        Path stored = storage.storeOriginal(originalFile);
        String sha = StorageManager.sha256(originalFile);

        String pred = pipelineResult.getOrDefault("prediction","unknown").toString();
        double conf = pipelineResult.containsKey("confidence") ? Double.parseDouble(pipelineResult.get("confidence").toString()) : 0.0;
        String extracted = pipelineResult.getOrDefault("extracted","").toString();
        String summary = pipelineResult.getOrDefault("summary","").toString();

        Path extractedPath = storage.storeExtracted(pred, originalFile.getName(), extracted);
        Path summaryPath = storage.storeSummary(pred, originalFile.getName(), "{ \"summary\": " + gson.toJson(summary) + " }");

        Map<String,Object> rec = new java.util.HashMap<>();
        rec.put("filename", originalFile.getName());
        rec.put("original_path", originalFile.getAbsolutePath());
        rec.put("stored_input_path", stored.toString());
        rec.put("extracted_path", extractedPath.toString());
        rec.put("summary_path", summaryPath.toString());
        rec.put("predicted_label", pred);
        rec.put("confidence", conf);
        rec.put("sha256", sha);
        rec.put("processed", 1);
        rec.put("notes", "Saved from UI");

        storage.upsertDocumentRecord(rec);
        return true;
    }

    public StorageManager getStorage() { return storage; }

    // Helper: parse JSON string to Map<String,Object>. Returns null on parse failure.
    private Map<String, Object> tryParseJsonToMap(String json) {
        if (json == null) return null;
        json = json.trim();
        if (json.isEmpty()) return null;
        try {
            // sometimes python scripts print extra newlines — trim and attempt parse
            return gson.fromJson(json, mapType);
        } catch (Exception e) {
            // parsing failed — log and return null so caller can fallback
            System.err.println("JSON parse failed in UIController.tryParseJsonToMap: " + e.getMessage());
            return null;
        }
    }
}
